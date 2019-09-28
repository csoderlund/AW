package build.file;
/**********************************************************
 * Reads the Ensembl Variant Predictor file and enters
 * effect=?, cDNApos=?, CDSpos=?, AApos=?, AAs=?, codons=?
 * where effect contains both missense and damaging info
 * TODO: everything but damaging will later be computed but
 * this may still read polyphen or SIFT if we figure out how to run.
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.ErrorReport;
import util.LogTime;
import database.DBConn;
import database.MetaData;
import build.Cfg;

public class VarAnno {
	private Vector <String> varAnnoVec; 
	static public final String DELIM="; "; // EVP uses ';'
	static public final String DELIM2=",";
	static public final String HIGH = "high";
	
	public VarAnno(DBConn dbc, Cfg cfg) {
		mDB = dbc;
		varAnnoVec = cfg.getVarAnnoVec();
		
		if (varAnnoVec==null) {
			LogTime.PrtSpMsg(1, "No Variant annotation file defined in cfg file -- skipping step");
			return;
		}
		else if (!cfg.isEVP() && !cfg.isSnpEFF()) {
			LogTime.PrtWarn("Could not identify the Variant annotation files  -- skipping step");
			return;
		}
		try {
			mDB.executeUpdate("update SNP set effectList=''");
			if (cfg.isEVP()) 
				 mDB.executeUpdate("update SNPtrans set effect='', codons='', AAs='', AApos=0" +
				 		",cDNApos=0, CDSpos=0");	
			else mDB.executeUpdate("update SNPtrans set effect='', codons='', AAs='', AApos=0");	
		} 
		catch (Exception e) {ErrorReport.prtError(e, "VarAnno initializing");}
		
		long startTime = LogTime.getTime();
		if (cfg.isEVP()) {
			LogTime.PrtDateMsg("Load EVP variant annotations (files " + varAnnoVec.size() + ")");
			loadEVPfiles();
		}
		else if (cfg.isSnpEFF()) {
			LogTime.PrtDateMsg("Load snpEFF variant annotations (files " + varAnnoVec.size() + ")");
			loadSnpEFF();
		}
		// sets missense counts, etc
		LogTime.PrtSpMsgTime(0, "Complete adding variant annotation", startTime);
	}
	/********************************************************************
	 * snpEFF
	 * - they are changing from effect classic to seq.ontology, which EVP uses and we will use  
	 */
	private void loadSnpEFF() {
		ResultSet rs=null;
		HashMap <String, String> so = new HashMap <String,String> ();
		so.put("codon_change", "coding_sequence_variant");
		so.put("codon_insertion", "inframe_insertion");
		so.put("codon_change_plus_codon_insertion", "disruptive_inframe_insertion");
		so.put("codon_deletion",  "inframe_deletion");
		so.put("codon_change_plus_codon_deletion", "disruptive_inframe_deletion");
		so.put("downstream", "downstream_gene_variant");
		so.put("exon", "exon_variant");
		so.put("exon_deleted", "exon_loss_variant");
		so.put("frame_shift", "frameshift_variant");
		so.put("gene", "gene_variant");
		so.put("intergenic", "intergenic_region");
		so.put("intergenic_conserved", "conserved_intergenic_region");
		so.put("intragenic", "intragenic_variant");
		so.put("intron", "intron_variant");
		so.put("intron_conserved", "conserved_intron_variant");
		so.put("non_synonymous_coding", "missense_variant");
		so.put("non_synonymous_stop", "stop_retained_variant");
		so.put("rare_amino_acid", "rare_amino_acid_variant");
		so.put("splice_site_acceptor", "splice_acceptor_variant");
		so.put("splice_site_donor", "splice_donor_variant");
		so.put("splice_site_region", "splice_region_variant");
		so.put("splice_site_branch", "splice_region_variant");
		so.put("stop_lost", "stop_lost");
		so.put("start_gain", "start_codon_gain_variant");
		so.put("start_lost", "start_lost");
		so.put("stop_gained", "start_gained");
		so.put("synonymous_coding", "synonymous_variant");
		so.put("synonymous_start", "start_retained");
		so.put("synonymous_stop", "stop_retained_variant");
		so.put("regulation", "regulatory_region_variant");
		so.put("utr_3_prime", "3_prime_utr_variant");
		so.put("utr_5_prime", "5_prime_utr_variant");
		
		try {
		// read database
			HashMap <String, Integer> varMap = new HashMap <String, Integer> ();
			rs = mDB.executeQuery("select snpid,chr,pos from SNP");
			while (rs.next()) {
				int snpid = rs.getInt(1);
				String chr = rs.getString(2).toLowerCase();
				int pos = rs.getInt(3);
				varMap.put((chr + ":" + pos), snpid);
			}	
			rs.close();
			if (varMap.size()==0) 
				LogTime.die("No variants in database");
			
			HashMap<String,Integer> transMap = new HashMap<String,Integer>();
			rs = mDB.executeQuery("select SNP.snpid, trans.transiden, trans.transid " +
					" from SNP " +
					" join SNPtrans on SNPtrans.snpid=SNP.snpid " +
					" join trans on trans.transid=SNPtrans.transid");
			while (rs.next()) {
				int snpid = rs.getInt(1);
				String ens = rs.getString(2);
				int transid = rs.getInt(3);
				transMap.put((snpid+":"+ens), transid);
			}
			rs.close();
			String chrRoot = new MetaData(mDB).getChrRoot();
			
			LogTime.PrtSpMsg(2, "Variants: " + varMap.size() + "  Transcripts-Variants pairs: " + transMap.size());
			
			PreparedStatement ps1 = mDB.prepareStatement("update SNPtrans " +
					"set effect=?, codons=?, AAs=?, AApos=? where snpid=? and transid=?");	
			Pattern effPat =   Pattern.compile("(\\w+)\\((.*)\\)"); 
			Pattern aaPat =   Pattern.compile("(\\D+)(\\d+)(\\D*)"); 
			HashMap<Integer,String> snpAnno = new HashMap<Integer,String>();
			int cntFile=0, cntChrErr=0;
			
		// loop through files
			for (String file : varAnnoVec)
			{
				int cntAdd=0, cntFileTotal=0, skipTrans=0, skipSNP=0, cntLines=0;
				cntFile++;
				LogTime.PrtSpMsg(1, "File #" + cntFile + " " + file);
				
				BufferedReader br = new BufferedReader(new FileReader(new File(file)));
				while (br.ready()) {
					String line = br.readLine();
					if (line.startsWith("#")) continue;
					cntLines++;
					String [] tok = line.split("\\t");
					
					String chr = tok[0];
					if (chr.startsWith(chrRoot)) chr = chr.substring(chrRoot.length());
					else {
						if (cntChrErr<5) 
							LogTime.PrtWarn("Line does not start with seqname prefix '" + chrRoot + "'" + "\nLine: " + line);
						else if (cntChrErr==5) LogTime.PrtWarn("Surpressing further such warnings");
						cntChrErr++;
					}
					String key = chr+":"+tok[1];
					if (!varMap.containsKey(key)) {
						skipSNP++;
						continue;
					}
					int SNPid = varMap.get(key);
					
					// go through list of SNP-trans pairs
					String list = tok[7].substring(4);
					String [] eff = list.split(",");
					for (int i=0; i< eff.length; i++) {
						Matcher m = effPat.matcher(eff[i]);
						if (!m.find()) {
							LogTime.PrtSpMsg(3, "Cannot parse: " + eff[i]);
							continue;
						}
						// each effect has list of values 
						// (Stop_lost is effect, high is impact, MISSENSE is functional class)
						// ,STOP_LOST(HIGH|MISSENSE|tGa/tTa|A30T 
						// this last is (AA pos AA) which isn't used but the AApos should be extracted
						String effect = m.group(1).toLowerCase();
						String [] eInfo = m.group(2).split("\\|");
						if (eInfo.length < 8) {
							LogTime.PrtSpMsg(3, eInfo.length + " Not enough fields: " + m.group(2));
							continue;
						}
						String impact = eInfo[0];
						String funClass = eInfo[1];
						String codon=eInfo[2]; 
						String AAs = "-", a="", b="";
						int AApos = 0;
						Matcher n = aaPat.matcher(eInfo[3]);
						if (n.find()) { 
							a = n.group(1);
							try {
								AApos = Integer.parseInt(n.group(2));
							}
							catch (Exception e) {LogTime.PrtWarn("snpEff: " + eInfo[3] + " " + n.group(2));}
							b = n.group(3);
						}
						if (!a.equals("") && !b.equals("")) AAs= a + "/" + b;
						else if (!a.equals("")) AAs = a;
						else if (!a.equals("")) AAs = "/" + b;
						
						if (so.containsKey(effect)) effect = so.get(effect);
						if (impact.equals("HIGH")) effect += DELIM2 + HIGH;
						
						if (!codon.equals("")) { // distance to transcript; don't want
							try {
								Integer.parseInt(codon);
								codon="-";
							}
							catch (Exception e){}
						}
					
						String ens = eInfo[8];
						String key2 = SNPid + ":" + ens;
						if (transMap.containsKey(key2)) {
							int tid = transMap.get(key2);
							effect = effect.toLowerCase();
							ps1.setString(1,effect);
							ps1.setString(2, codon);
							ps1.setString(3, AAs);
							ps1.setInt(4,  AApos);
							ps1.setInt(5,SNPid); // where clause
							ps1.setInt(6,tid);
							ps1.addBatch();
							cntFileTotal++; cntAdd++;
							if (cntAdd==1000) {
								ps1.executeBatch();
								LogTime.r("processed " + cntFileTotal + " from " + cntLines);
								cntAdd=0;
							}
							
							if (!snpAnno.containsKey(SNPid)) snpAnno.put(SNPid, effect);
							else {
								String e = snpAnno.get(SNPid);
								if (!e.contains(effect))
									snpAnno.put(SNPid, e + DELIM + effect);
							}
						}
						else skipTrans++;
					}
				} // end read
				if (cntAdd>0) ps1.executeBatch();
				
				LogTime.PrtSpMsg(2, "Update SNP-trans: " + cntFileTotal + "                                  ");
				if (skipSNP>0 || skipTrans>0) 
					LogTime.PrtSpMsg(2, "Skipped SNPs: " + skipSNP + "  skipped Trans: " + skipTrans);
			} // end files
		
			LogTime.PrtSpMsg(1, "Update mySQL Variant tables                        ");
			PreparedStatement ps = mDB.prepareStatement("update SNP set effectList=? where snpid=?");
			int cntAdd=0, cntDesc=0;
			for (int snpid : snpAnno.keySet())
			{
				ps.setInt(2, snpid);
				ps.setString(1,snpAnno.get(snpid));
				ps.addBatch();
				cntAdd++; cntDesc++;
				
				if (cntAdd==1000) {
					ps.executeBatch(); 
					LogTime.r("Finalize variants " + cntDesc);
				}
			}
			if (cntAdd > 0) ps.executeBatch();
			LogTime.PrtSpMsg(2, "Added descriptions: " + cntDesc);
		}
		catch (Exception e) {ErrorReport.prtError(e, "Loading the snpEFF files ");}
	}
	/*********************************************************************
	 * XXX add the annotation for the SNPs from the full gtf file
	 * SNP listed for each of its transcripts; key off location to match with SNP in HW_mus
	 * 0.Uploaded_variation=rs50154800   1.Location=1:4923964  2.Allele=C  
	 * 3.Gene=ENSMUSG00000002459   4.Feature=ENSMUST00000118000  5.Type=Transcript      
	 * 6.Consequence=missense_variant   7.cDNA_position=602   8.CDS_position=494 9.Protein_position=165  
	 * 10.AminoAcid=M/R  11.codons=aTg/aGg 
	 * 12.Existing varition=-       13.Extra=SIFT=deleterious(0)
	 * 
	 * add db fields int cDNApos, int AApos, tinytex AAs, tinytext Codons, tinytext Who 
	 * where who= ref or alt, depending on which causes the change
	 */
	private void loadEVPfiles() {
		try {	
			HashMap<String,HashMap<Integer,Integer>> snpbyChr = new HashMap<String,HashMap<Integer,Integer>>(); // snps in the db
			HashMap<Integer,HashSet<String>> snp2trans = new HashMap<Integer,HashSet<String>>();
			HashMap<String,Integer> transID = new HashMap<String,Integer>();
			
			ResultSet rs = null;
	
			// create hash of key chromosomes with value hashMap of pos, SNPid
			rs = mDB.executeQuery("select snpid,chr,pos from SNP");
			while (rs.next()) {
				int snpid = rs.getInt(1);
				String chr = rs.getString(2);
				int pos = rs.getInt(3);
				if (!snpbyChr.containsKey(chr)) 
					snpbyChr.put(chr, new HashMap<Integer,Integer>());
				snpbyChr.get(chr).put(pos, snpid);
			}	
			rs.close();
			
			// create hash of key SNPid with value hashSet of transiden (ensembl)
			rs = mDB.executeQuery("select SNP.snpid,trans.transiden, trans.transid " +
					" from SNP " +
					" join SNPtrans on SNPtrans.snpid=SNP.snpid " +
					" join trans on trans.transid=SNPtrans.transid");
			while (rs.next()) {
				int snpid = rs.getInt(1);
				String transiden = rs.getString(2);
				if (!snp2trans.containsKey(snpid)) snp2trans.put(snpid, new HashSet<String>());
				snp2trans.get(snpid).add(transiden);
				if (!transID.containsKey(transiden)) {
					transID.put(transiden, rs.getInt(3));
				}
			}
			rs.close();
			
			int tried = 0, skipSNP = 0, skipBadChr=0;
			HashSet<Integer> seen = new HashSet<Integer>();
			
			// read exon_indels.annot.txt, exon_snps.annot.txt, and gatk.calls.exon.annot.txt
			// create hashMap of key SNPid with value hash of annotations
			// enter annotation per transcript into SNPtrans table
			HashMap<Integer, String> snpAnno = new HashMap<Integer, String>();
		
			PreparedStatement ps1 = mDB.prepareStatement("update SNPtrans " +
					"set effect=?, cDNApos=?, CDSpos=?, AApos=?, AAs=?, codons=? " +
					"where snpid=? and transid=?");		
						
			for (String file : varAnnoVec)
			{
				LogTime.PrtSpMsg(1, "Load " + file);
				int cntAdd=0, cntFileTotal=0, skipTrans = 0, skipSNPTrans = 0;
				
				BufferedReader br = new BufferedReader(new FileReader(new File(file)));
				while (br.ready()) {
					String line = br.readLine();
					if (line.startsWith("#")) continue;
					tried++;
					if (tried %1000==0) LogTime.r("read " + tried);
					String[] f = line.split("\\t");
					
					String[] chrpos = f[1].split(":");
					if (chrpos.length != 2) ErrorReport.die("bad chrpos: " + line);
					
					String chr = chrpos[0];
					String posStr = chrpos[1].replaceAll("\\-\\d+", ""); // for indel, it is start-end, we take start
					int pos = Integer.parseInt(posStr);
					
					if (!snpbyChr.containsKey(chr)) {
						skipBadChr++;
						if (skipBadChr<5) LogTime.PrtWarn("Bad chr:" + line);
						else if (skipBadChr==5) LogTime.PrtWarn("Surpressing further bad chr errors");
						continue;
					}
					if (!snpbyChr.get(chr).containsKey(pos)) {
						skipSNP++;
						continue;
					}
					int snpid = snpbyChr.get(chr).get(pos);
					seen.add(snpid);
					
					String trans = f[4];
					if (!transID.containsKey(trans)) {
						skipTrans++;
						continue;
					}
					int tid = transID.get(trans);
					
					String effect = f[6]; // for SNP.effectList
					if (effect.contains(",")) effect = effect.substring(0, effect.indexOf(","));
					String annot = f[6].trim();
					if (f.length >= 14) {
						String extra = f[13];
						if (!extra.equals("")) {
							annot += DELIM2 + extra;
							if (extra.contains("SIFT=del")) effect += DELIM2 + "SIFT=deleterious";
						}
					}
					
					if (!snp2trans.containsKey(snpid) || !snp2trans.get(snpid).contains(trans)) {
						skipSNPTrans++;
						continue; 
					}
					// indels can have field -, or d+-d+
					int cDNApos = 0, CDSpos=0, AApos=0;
					try {cDNApos = Integer.parseInt(f[7].replaceAll("\\-\\d+", ""));} catch (Exception e) {};
					try {CDSpos = Integer.parseInt(f[8].replaceAll("\\-\\d+", ""));} catch (Exception e) {};
					try {AApos = Integer.parseInt(f[9].replaceAll("\\-\\d+", ""));} catch (Exception e) {};
					
					ps1.setString(1,annot);
					ps1.setInt(2, cDNApos); 
					ps1.setInt(3, CDSpos); 
					ps1.setInt(4, AApos); 
					ps1.setString(5, f[10]);		// A->A
					ps1.setString(6, f[11]);		// codon->codon
					ps1.setInt(7,snpid); // where clause
					ps1.setInt(8,tid);
					ps1.addBatch();
					cntAdd++; cntFileTotal++;
					if (cntAdd==1000) {
						ps1.executeBatch();
						cntAdd=0;
						LogTime.r("processed " + cntFileTotal);
					}
					
					if (!snpAnno.containsKey(snpid)) 
						snpAnno.put(snpid, effect);
					else {
						String e = snpAnno.get(snpid);
						if (!e.contains(effect))
							snpAnno.put(snpid, e + DELIM + effect);
					}
				} // end reading file
				if (cntAdd > 0) ps1.executeBatch();
				LogTime.PrtSpMsg(2, "Update SNP-trans: " + cntFileTotal + "                                  ");
				if (skipSNP>0 || skipTrans>0 || skipSNPTrans>0) 
					LogTime.PrtSpMsg(2, "Skipped SNPs: " + skipSNP + "  skipped Trans: " + skipTrans
							+ "  skip SNP Trans: " + skipSNPTrans);
			} // end loop through files
			
			LogTime.PrtSpMsg(1, "Update mySQL Variant tables                        ");
			PreparedStatement ps = mDB.prepareStatement("update SNP set effectList=? where snpid=?");
			int cntAdd=0, cntFinal=0;
			for (int snpid : snpAnno.keySet())
			{
				ps.setInt(2, snpid);
				ps.setString(1, snpAnno.get(snpid));
				ps.addBatch();
				cntAdd++; cntFinal++;
				
				if (cntAdd==1000) {
					LogTime.r("Finalize variants " + cntFinal);
					ps.executeBatch(); 
				}
			}
			if (cntAdd > 0) ps.executeBatch();
			LogTime.PrtSpMsg(2, "Added " + cntFinal + " descriptions (of " + tried + ")");
			if (skipBadChr>0) LogTime.PrtSpMsg(3, "Skipped SNPs due to unknown chromosome: " + skipBadChr);
		} 
		catch(Exception e) {
			ErrorReport.prtError(e, "doing snp annotation");
		}
	}
	private DBConn mDB;
}
