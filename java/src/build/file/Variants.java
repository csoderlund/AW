package build.file;

/*****************************************
 * * Reads the VCF file, creates the SNP records, SNPexon, SNPgene, SNPtrans
 * and update exon, trans, gene counts
 * VCF file format:
 * 0       1       2               3       4       5       6
 * chr1    4785683 rs221502411     G       A       257.00  PASS    
 */
import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Vector;

import util.*;
import build.Bmain;
import build.Cfg;
import database.DBConn;
import database.MetaData;

public class Variants {
	static int chrCol = 0;
	static int locCol = 1;
	static int nameCol = 2;
	static int refCol = 3;
	static int altCol = 4;
	static int qualCol = 5;
	static int passCol = 6; // Check VCF file, some have PASS, some do not
	private String chrRoot;
	private Vector <String> chrVec;
	
	public Variants (DBConn dbc, Cfg cfg) {
		mDB = dbc;
		Vector <String> varFiles = cfg.getVarVec();
	
		long startTime = LogTime.getTime();
		LogTime.PrtDateMsg("Add variants to database");
		
		try { // this is what gets updated in this file
			mDB.executeUpdate("truncate table SNP");
			mDB.executeUpdate("truncate table SNPgene");
			mDB.executeUpdate("truncate table SNPtrans");
			mDB.executeUpdate("truncate table SNPexon");
			mDB.executeUpdate("UPDATE transExon set cntSNP=0, cntIndel=0");
			mDB.executeUpdate("UPDATE trans set cntSNP=0, cntIndel=0, cntCoding=0, cntIDCoding=0");
			mDB.executeUpdate("UPDATE gene set cntSNP=0, cntIndel=0");
		}
		catch (Exception e) {ErrorReport.die(e, "Clearing tables");}
		
		readDB();
		int fileCnt=1;
		for (String file : varFiles) {
			readFileAddVariantExon(file, 	fileCnt);
			fileCnt++;
		}
		chrGeneMap = null; 
		updateDBcounts(); 
		LogTime.PrtSpMsgTime(0, "Finish adding variants", startTime);
	}
	/*******************************************************************
	 * Read VCF file of SNPs and locate them in exons
	 * #CHROM  POS     ID      		REF     ALT     QUAL    FILTER  
		chr10   3101362 rs33880920      G       C       226.33  PASS
	 */
	private void readFileAddVariantExon(String file, int fileCnt) {	
		try {		
			LogTime.PrtSpMsg(1, "Load file#" + fileCnt + " " + file);
			BufferedReader reader = new BufferedReader ( new FileReader ( file ) ); 
			String line="";
			int read=0, cnt=0, newSNP=0, newIND=0, addExon=0;
		
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("#")) continue;
				String [] tok = line.split("\t");
				if (tok.length==0) continue;
				if (tok.length<passCol) {
					System.err.println("Ignore: " + line);		
					continue;
				}
				read++;
				
				String chr = getChr(tok[chrCol], line);
				if (chr==null) continue;
				
				int pos = Integer.parseInt(tok[locCol]);
				Double qual = Double.parseDouble(tok[qualCol]);
				String ref = tok[refCol];
				String alt = tok[altCol];
				boolean isSNP = (ref.length()==1 && alt.length()==1) ? true : false;
				int endPos = pos;
				if (!isSNP && ref.length()>0) endPos += ref.length();
				
				String snpid = tok[nameCol];
				int isdbSNP=1;
				if (snpid.equals(".")) {
					if (isSNP) {
						snpid = "SNP" + fileCnt + "_" + newSNP;
						newSNP++;
					}
					else {
						snpid = "IND" + fileCnt + "_" + newIND;
						newIND++;
					}	
					isdbSNP=0;
				}
				
				HashMap <Trans, Variant>transMap = new HashMap <Trans, Variant> ();
				ArrayList <Exon>exonList = new ArrayList <Exon> ();
				ArrayList <Gene>geneList = new ArrayList <Gene> ();
				boolean snpIsCoding=false;
				
				// transcripts with SNPs
				// TODO include 5k up/down stream (snpeff uses 5k)
				String exonListStr="";
				Vector <Gene> genes = chrGeneMap.get(chr);
				for (Gene gn : genes) {
					if (pos < gn.start || pos > gn.end) continue;
					boolean inGene=false;			
					for (Trans tr: gn.trans) {
						if (!(pos >= tr.start && endPos <=tr.end)) continue;
						Exon exon=null;			
						String locExon="";
						// TODO check if indels cross splice sites
						// Since CDS exons can be contained within !CDS, we want the CDS if possible
						for (Exon ex : tr.exonList) {
							if (ex.frame>=0 && pos >= ex.start && pos <= ex.end) {
								snpIsCoding=true;
								exon=ex;
								locExon = (pos-ex.start+1) + "," + (ex.end-pos+1);
								break;
							}
						}
						if (exon==null) {
							for (Exon ex : tr.exonList) {
								if (ex.frame<0 && pos >= ex.start && pos <= ex.end) {
									snpIsCoding=false;
									exon=ex;
									locExon = (pos-ex.start+1) + "," + (ex.end-pos+1);
									break;
								}
							}
							if (exon==null) continue;
						}
						inGene=true;
				
						if (!allTrSet.contains(tr)) allTrSet.add(tr);
						int code = (exon.frame >=0) ? 1 : 0;
						transMap.put(tr, new Variant(code, pos, exon.nExon, locExon));
							
						if (exon!=null) {
							if (exonListStr=="") exonListStr = "" + exon.nExon;
							else exonListStr += "," + exon.nExon;
							exonList.add(exon);
						}	
					} // end loop through trans for this variant
					if (inGene) {
						if (!allGeneSet.contains(gn)) allGeneSet.add(gn);
						if (!geneList.contains(gn)) geneList.add(gn);
					}
				} // end loop through genes for this variant
				
				if (transMap==null || transMap.size()==0) continue;
			
				mDB.executeUpdate("INSERT SNP SET " +
						" rsID=" 		+ quote(snpid) + 
						",isdbSNP=" 	+ isdbSNP + 
						",isSNP="		+ isSNP + 
						",chr=" 		+ quote(chr) + 
						",pos=" 		+ pos + 
						",ref=" 		+ quote(ref) + 
						",alt=" 		+ quote(alt) + 
						",qual="		+ qual + 
						",exonList=" + quote(exonListStr) +
						",isCoding=" + snpIsCoding
						);
			
				int SNPid=0;
				ResultSet rs = mDB.executeQuery("select last_insert_id() as pid");  
		        if (rs.next()) SNPid = rs.getInt("pid");
		        else ErrorReport.die("Cannot get last_insert_id in SNPs");	
				
		        for (Exon ex : exonList) {
					ex.cnt(isSNP);
					mDB.executeUpdate("INSERT SNPexon SET SNPid=" + SNPid + ", EXONid=" + ex.exonid);
					addExon++;
				}
	
		        for (Trans tr : transMap.keySet()) {
		        		Variant var = transMap.get(tr);
		        		tr.cnt(isSNP, var.coding);
				    mDB.executeUpdate("INSERT SNPtrans SET SNPid= " + SNPid + 
				    		", TRANSid=" + tr.transid + ", transName=" + quote(tr.name) +
				    		", isCoding=" + var.coding + ", nExon=" + var.exon + 
				    		", locExon='" + var.loc + "'" );
				}
		        for (Gene gn : geneList) {
					gn.cnt(isSNP);
					mDB.executeUpdate("INSERT SNPgene SET SNPid=" + SNPid + ", " +
							"GENEid=" + gn.geneid + ", geneName=" + quote(gn.name));
					addExon++;
				}
				cnt++;
				if (cnt % 1000 == 0) 
					System.out.print("      Read: " + read + "  SNPs: " + cnt + "  inExon: " + addExon +
							"  New SNP: " + newSNP + "  New Indel: " + newIND +  "..." + "\r");
			}		
			LogTime.PrtSpMsg(2, "Read: " + read + "  Variants: " + cnt + 
					 "  In Exon: " + addExon + "  New SNP: " + newSNP + "  New Indel: " + newIND + "     ");
			LogTime.PrtSpMsg(2, "Genes with variants: " + allGeneSet.size() + " Trans with variants: " + allTrSet.size());
			if (cnt==0 && newSNP==0 && newIND==0) ErrorReport.die("No variants added");
		}
		catch (Exception e) {ErrorReport.die(e, "Reading Variant file");}
	}
	private int cntChrErr=0, cntChrErr2=0;
	private String getChr(String chr, String line) {
		if (chr.startsWith(chrRoot)) {
			chr = chr.substring(chrRoot.length());
		}
		else {
			if (cntChrErr<3) LogTime.PrtWarn("Line does not start with root '" + chrRoot +"'" +"\nLine: " + line);
			else if (cntChrErr==3) LogTime.PrtWarn("Surpressing further such messages");
			cntChrErr++;
			return null;
		}
		if (!chrGeneMap.containsKey(chr)) { // shouldn't happen, already checked above
			LogTime.PrtWarn("no chr " + chr + " in database");
			return null;
		}
		if (!chrVec.contains(chr)) {
			if (cntChrErr2<3) LogTime.PrtWarn("Line does not start with a valid chr (i.e. Seqname in GTF)\nLine: " + line);
			else if (cntChrErr2==3) LogTime.PrtWarn("Surpressing further such messages");
			cntChrErr2++;
			return null;
		}
		return chr;
	}
	/************************************************************
	 * XXX update transExon
	 */
	private void updateDBcounts() {
		LogTime.PrtSpMsg(1, "Update counts for Exons, Trans and Genes");
		try {
			int cntTr=0, cntEx=0, cntGn=0;
			for (Trans tr : allTrSet) {
				for (Exon ex : tr.exonList) {
					if (ex.cntSNP>0 || ex.cntIndel>0) {
						mDB.executeUpdate("UPDATE transExon SET " +
							"cntSNP=" + ex.cntSNP + ",cntIndel= " + ex.cntIndel + 
							" where EXONid=" + ex.exonid + " and TRANSid=" + tr.transid);
						cntEx++;
					}
				}
				mDB.executeUpdate("UPDATE trans SET " +
						"cntSNP=" + tr.cntSNP + ", cntIndel="+ tr.cntIndel + 
						",cntCoding=" + tr.cntSNPcode + ", cntIDcoding="+ tr.cntINDcode + 
						" where TRANSid="+tr.transid);
				cntTr++;
			}
			for (Gene gn : allGeneSet) {
				mDB.executeUpdate("UPDATE gene SET " +
						"cntSNP=" + gn.cntSNP + ", cntIndel="+ gn.cntIndel + " where GENEid="+ gn.geneid);
				cntGn++;
			}
			LogTime.PrtSpMsg(2, "Complete adding Variants to Genes: " + cntGn +
					" Trans: " + cntTr + " Exons: " + cntEx);
		}
		catch (Exception e) {
			ErrorReport.die(e, "Variant: addSNPs2Trans");
		}
	}
	
	/***********************************************
	 * readDB
	 */
	private void readDB() {
		try {
			LogTime.PrtSpMsg(1, "Reading database for coords");
			ResultSet rs;
			int cntTr=0, cntExon=0, cntGene=0;
			MetaData md = new MetaData(mDB);
			chrRoot = md.getChrRoot();
			chrVec = md.getChr();
			
			for (String chr : chrVec) {
				chrGeneMap.put(chr, new Vector <Gene> ());
			}
			
			for (String chr : chrGeneMap.keySet()) {
				System.out.print(("      reading database for chr " + chr + "      \r"));
				Vector <Gene> gvec = chrGeneMap.get(chr);
				rs = mDB.executeQuery("Select GENEid, geneName, start, end FROM gene" +
						" where chr='" + chr + "' order by start");
				while (rs.next()) {
					Gene g = new Gene(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4));
					gvec.add(g);
					cntGene++;
				}
				chrGeneMap.put(chr, gvec); 
				
				for (Gene gn: gvec) {
					rs = mDB.executeQuery("Select TRANSid, transName, start, end" +
							" from trans " +
							" where GENEid="+ gn.geneid + " and start>0 and end>0 order by start"); 
					while (rs.next()) {
						Trans tr = new Trans(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4));
						gn.addTr(tr);
						cntTr++;
					}
				
					for (Trans tr: gn.trans) {
						rs = mDB.executeQuery("Select EXONid, cStart, cEnd, nExon, " +
								"frame from transExon " +
								"where TRANSid=" + tr.transid + " order by cStart"); 
						while (rs.next()) {
							tr.addExon(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5));
							cntExon++;
						}
					}
				}
			} // loop trough chr
			LogTime.PrtSpMsg(2, "Gene: " + cntGene + "   Trans: " + cntTr + "   Exons: " + cntExon);
		}
		catch (Exception e) {ErrorReport.die(e, "read DB ");}
	}

	/***********************************************************
	 * Data structure and methods
	 */
	private HashSet <Gene> allGeneSet = new HashSet <Gene> ();
	private HashSet <Trans> allTrSet = new HashSet <Trans> ();
	private HashMap <String, Vector <Gene>> chrGeneMap = new HashMap <String, Vector <Gene>> ();

	// Gene
	private class Gene {
		private Gene (int id, String n, int s, int e) {
			geneid=id;
			name = n;
			start = (s < e) ? s : e;
			end =   (s < e) ? e : s;
		}
		private void addTr(Trans tr) {
			trans.add(tr);
		}
		private void cnt(boolean isSNP) {
			if (isSNP) cntSNP++;
			else cntIndel++;
		}
		int geneid, start, end, cntSNP=0, cntIndel=0;
		String name;
		ArrayList <Trans> trans = new ArrayList <Trans> ();
	}
	// Trans
	private class Trans {
		private Trans (int id, String n, int s, int e) {
			transid = id;
			name = n;
			isPos = (s<e) ? true : false;
			start = (s < e) ? s : e;
			end =   (s < e) ? e : s;
		}	
		private void addExon(int id, int cs, int ce, int n, int f) {
			exonList.add(new Exon(id, cs,ce,n,f));
			cntExon++;
		}
		
		private void cnt(boolean isSNP, int isCoding) {
			if (isSNP) cntSNP++;
			else cntIndel++;
			if  (isCoding==1) {
				if (isSNP) cntSNPcode++;
				else cntINDcode++;
			}
		}
		
		String name;
		int start, end, transid;
		int cntSNP=0, cntIndel=0, cntSNPcode=0, cntINDcode=0, cntExon=0;
		boolean isPos;
		ArrayList <Exon>exonList = new ArrayList <Exon> ();
	}
	private class Exon {
		private Exon(int id, int s, int e, int n, int f) {
			exonid = id;
			start=s;
			end=e;
			nExon=n;
			frame=f;
		}
		private void cnt(boolean isSNP) {
			if (isSNP) cntSNP++;
			else cntIndel++;
		}
		int exonid, start, end, nExon, frame, cntSNP=0, cntIndel=0;
	}
	private class Variant {
		private Variant(int coding, int pos, int exon, String loc) {
			this.coding = coding;
			this.pos = pos;
			this.exon = exon;
			this.loc = loc;
		}
		int coding, pos, exon;
		String loc="";
	}
	private String quote(String word) {
		return "'" + word + "'"; 
	}
	private DBConn mDB;
}
