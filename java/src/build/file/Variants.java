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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Vector;

import util.*;
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
		LogTime.PrtDateMsg("Add variant calls to database");
		if (varFiles.size()>1) 
			LogTime.PrtSpMsg(1, "Load " + varFiles.size() + " files from " + cfg.getVarDir());
		
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
			readFileAddSNPtables(file, 	fileCnt);
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
	private void readFileAddSNPtables(String file, int fileCnt) {	
		try {		
			LogTime.PrtSpMsg(1, "File#" + fileCnt + " " + file);
			chrClear();
			
			BufferedReader reader = new BufferedReader ( new FileReader ( file ) ); 
			String line="";
			int read=0, cnt=0, newSNP=0, newIND=0, addSNP=0, addExon=0, addTrans=0, addGene=0;
		
			PreparedStatement psSNP = mDB.prepareStatement("INSERT into SNP " +
					"(rsID,isdbSNP,isSNP,chr,pos,ref, alt, qual,exonList,isCoding)" +
					" values(?,?,?,?,?, ?,?,?,?,?)");
			
			PreparedStatement psExon = mDB.prepareStatement("INSERT into SNPexon " +
					"(SNPid, EXONid) values(?,?)");
			
			PreparedStatement psTran = mDB.prepareStatement("INSERT into SNPtrans " +
				    "(SNPid, TRANSid, transName, isCoding, nExon, locExon)  " +
				    " values (?,?,?,?,?,?)");
			
			PreparedStatement psGene = mDB.prepareStatement("INSERT into SNPgene " +
				    "(SNPid, GENEid, geneName) values(?,?,?)");
			
			// Read SNP/Indel (variant) per line
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("#")) continue;
				String [] tok = line.split("\t");
				if (tok.length==0) continue;
				if (tok.length<passCol) {
					LogTime.warn("Ignore: " + line);		
					continue;
				}
				read++;
				
				String chr = chrCheck(tok[chrCol], line);
				if (chr==null) continue;
				
				int pos = Integer.parseInt(tok[locCol]);
				Double qual = Double.parseDouble(tok[qualCol]);
				String ref = tok[refCol];
				String alt = tok[altCol];
				boolean isSNP = (ref.length()==1 && alt.length()==1) ? true : false;
				int endPos = pos;
				if (!isSNP && ref.length()>0) endPos += ref.length();
				
				String snpName = tok[nameCol];
				int isdbSNP=1;
				if (snpName.equals(".")) {
					if (isSNP) {
						snpName = "SNP" + fileCnt + "_" + newSNP;
						newSNP++;
					}
					else {
						snpName = "IND" + fileCnt + "_" + newIND;
						newIND++;
					}	
					isdbSNP=0;
				}
				
				HashMap <Trans, Variant>transMap = new HashMap <Trans, Variant> ();
				ArrayList <Exon>exonList = new ArrayList <Exon> ();
				ArrayList <Gene>geneList = new ArrayList <Gene> ();
				boolean snpIsCoding=false;
				
				// transcripts with SNPs (TODO include 5k up/down stream (snpeff uses 5k))
				String exonListStr="";
				Vector <Gene> genes = chrGeneMap.get(chr);
				
				for (Gene gn : genes) {
					if (pos < gn.start || pos > gn.end) continue;
					
					boolean inGene=false;			
					for (Trans tr: gn.trans) {
						if (!(pos >= tr.start && endPos <=tr.end)) continue;
						Exon exon=null;			
						String locExon="";
						
						// EXON  (TODO check if indels cross splice sites)
						//       Since CDS exons can be contained within !CDS, we want the CDS if possible
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
						
						// Trans list
						transMap.put(tr, new Variant(code, pos, exon.nExon, locExon));
							
						if (exon!=null) { // exon list
							if (exonListStr=="") exonListStr = "" + exon.nExon;
							else exonListStr += "," + exon.nExon;
							exonList.add(exon);
						}	
					} // end loop through trans for this variant
					
					if (inGene) { // gene list
						if (!allGeneSet.contains(gn)) allGeneSet.add(gn);
						if (!geneList.contains(gn))   geneList.add(gn);
					}
				} // end loop through genes for this variant
				
				if (transMap==null || transMap.size()==0) continue;
			
				psSNP.setString(1, snpName);
				psSNP.setInt(2, isdbSNP);
				psSNP.setBoolean(3, isSNP);
				psSNP.setString(4, chr);
				psSNP.setInt(5, pos);
				psSNP.setString(6, ref);
				psSNP.setString(7, alt);
				psSNP.setDouble(8, qual);
				psSNP.setString(9, exonListStr);
				psSNP.setBoolean(10, snpIsCoding);
				psSNP.executeUpdate();
				addSNP++;
				
				int SNPid=0;
				ResultSet rs = mDB.executeQuery("select last_insert_id() as pid");  
		        if (rs.next()) SNPid = rs.getInt("pid");
		        else ErrorReport.die("Cannot get last_insert_id in SNPs");	
				rs.close();
				
		        // SNPexon
		        int cntAdd=0;
		        for (Exon ex : exonList) {
					ex.cnt(isSNP);
					psExon.setInt(1, SNPid);
					psExon.setInt(2, ex.exonid);
					psExon.addBatch();
					addExon++; cntAdd++;
				}
		        if (cntAdd>0) psExon.executeBatch();
		        
		        cntAdd=0;
		        for (Trans tr : transMap.keySet()) {
		        		Variant var = transMap.get(tr);
		        		tr.cnt(isSNP, var.coding);
		        		
		        		psTran.setInt(1, SNPid);
		        		psTran.setInt(2, tr.transid);
		        		psTran.setString(3, tr.name);
		        		psTran.setInt(4, var.coding);
		        		psTran.setInt(5, var.exon);
		        		psTran.setString(6, var.loc);
		        		psTran.addBatch();
		        		addTrans++; cntAdd++;
				}
		        if (cntAdd>0) psTran.executeBatch();
		        
		        cntAdd=0;
		        for (Gene gn : geneList) {
					gn.cnt(isSNP);
					
					psGene.setInt(1, SNPid);
					psGene.setInt(2,gn.geneid);
					psGene.setString(3, gn.name);
					psGene.addBatch();
					addGene++; cntAdd++;
				}
		        if (cntAdd>0) psGene.executeBatch();
		        
				cnt++;
				if (cnt==100) { 
					LogTime.r("Read: " + read + "  SNPs: " + cnt + "  inExon: " + addExon +
							"  New SNP: " + newSNP + "  New Indel: " + newIND);
					cnt=0;
				}
				
			} // End loop through lines of file
			
			LogTime.PrtSpMsg(2, "Read: " + read + "   Variants: " + addSNP + 
					 "  In Exon: " + addExon + "   New SNP: " + newSNP + "   New Indel: " + newIND + "     ");
			LogTime.PrtSpMsg(2, "Genes with variants: " + allGeneSet.size() + "   Gene-variant pairs: " + addGene);
			LogTime.PrtSpMsg(2, "Trans with variants: " + allTrSet.size() +   "   Tran-variant pairs: " + addTrans);
			
			if (addSNP==0) ErrorReport.die("No variants added");
			
			chrPrt();
		}
		catch (Exception e) {ErrorReport.die(e, "Reading Variant file");}
	}
	/*******************************************************
	 * CASZ 8Oct19 print one of each type of error per file
	 */
	private HashSet <String> badChr = new HashSet <String> ();
	private int cntChrErr=0, cntBadChr=0;
	
	private String chrCheck(String chr, String line) {
		String chrX="";
		if (chr.startsWith(chrRoot)) {
			chrX = chr.substring(chrRoot.length());
		}
		else {
			if (cntChrErr==0) LogTime.PrtWarn("Line does not start with prefix '" + chrRoot +"'" +"\nLine: " + line);
			cntChrErr++;
			return null;
		}
		if (!chrVec.contains(chrX)) { 
			if (!badChr.contains(chrX)) LogTime.PrtWarn("No seqname '" + chr + "' in database");
			badChr.add(chrX);
			cntBadChr++;
			return null;
		}
		chrCntMap.put(chrX, chrCntMap.get(chrX)+1);
		return chrX;
	}
	private void chrClear() {
		cntChrErr=0;
		cntBadChr=0;
		badChr.clear();
		chrCntMap.clear();
		for (String x : chrVec) chrCntMap.put(x, 0);
	}
	private void chrPrt() {
		if (cntChrErr>0) LogTime.PrtSpMsg(2,"***Lines that do no start with prefix: " + cntChrErr);
		if (cntBadChr>0) LogTime.PrtSpMsg(2,"***No seqnane in database:  " + cntBadChr);
		
		String line= chrRoot + ": ";
		for (String x : chrCntMap.keySet()) {
			line += x + ":" + chrCntMap.get(x) + " ";
		}
		LogTime.PrtSpMsg(2, line);
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
				if (tr.cntSNP>0 || tr.cntIndel>0 || tr.cntSNPcode>0 || tr.cntINDcode>0) {
					mDB.executeUpdate("UPDATE trans SET " +
						"cntSNP=" + tr.cntSNP + ", cntIndel="+ tr.cntIndel + 
						",cntCoding=" + tr.cntSNPcode + ", cntIDcoding="+ tr.cntINDcode + 
						" where TRANSid="+tr.transid);
					cntTr++;
				}
			}
			for (Gene gn : allGeneSet) {
				if (gn.cntSNP>0 || gn.cntIndel>0) {
					mDB.executeUpdate("UPDATE gene SET " +
						"cntSNP=" + gn.cntSNP + ", cntIndel="+ gn.cntIndel + " where GENEid="+ gn.geneid);
					cntGn++;
				}
			}
			LogTime.PrtSpMsg(2, "Update Genes: " + cntGn +
					"  Trans: " + cntTr + "  Exons: " + cntEx);
		}
		catch (Exception e) {ErrorReport.die(e, "Variant: addSNPs2Trans");}
	}
	
	/***********************************************
	 * readDB
	 */
	private void readDB() {
		try {
			ResultSet rs=null;
			MetaData md = new MetaData(mDB);
			chrRoot = md.getChrRoot();
			chrVec = md.getChr();
			
			for (String chr : chrVec) {
				chrGeneMap.put(chr, new Vector <Gene> ());
				chrCntMap.put(chr, 0);
			}
			
			for (String chr : chrGeneMap.keySet()) {
				LogTime.r("reading database for chr " + chr);
				Vector <Gene> gvec = chrGeneMap.get(chr);
				rs = mDB.executeQuery("Select GENEid, geneName, start, end FROM gene" +
						" where chr='" + chr + "' order by start");
				while (rs.next()) {
					Gene g = new Gene(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4));
					gvec.add(g);
				}
				chrGeneMap.put(chr, gvec); 
				
				for (Gene gn: gvec) {
					rs = mDB.executeQuery("Select TRANSid, transName, start, end" +
							" from trans " +
							" where GENEid="+ gn.geneid + " and start>0 and end>0 order by start"); 
					while (rs.next()) {
						Trans tr = new Trans(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4));
						gn.addTr(tr);
					}
				
					for (Trans tr: gn.trans) {
						rs = mDB.executeQuery("Select EXONid, cStart, cEnd, nExon, " +
								"frame from transExon " +
								"where TRANSid=" + tr.transid + " order by cStart"); 
						while (rs.next()) {
							tr.addExon(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5));
						}
					}
				}
			} // loop trough chr
			if (rs!=null) rs.close();
			LogTime.r("                                                     ");
		}
		catch (Exception e) {ErrorReport.die(e, "read DB ");}
	}

	/***********************************************************
	 * Data structure and methods
	 */
	private HashSet <Gene> allGeneSet = new HashSet <Gene> ();
	private HashSet <Trans> allTrSet = new HashSet <Trans> ();
	private HashMap <String, Vector <Gene>> chrGeneMap = new HashMap <String, Vector <Gene>> ();
	private HashMap <String, Integer> chrCntMap = new HashMap <String, Integer> ();

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
			start = (s < e) ? s : e;
			end =   (s < e) ? e : s;
		}	
		private void addExon(int id, int cs, int ce, int n, int f) {
			exonList.add(new Exon(id, cs,ce,n,f));
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
		int cntSNP=0, cntIndel=0, cntSNPcode=0, cntINDcode=0;
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
			this.exon = exon;
			this.loc = loc;
		}
		int coding, exon;
		String loc="";
	}
	private DBConn mDB;
	
}
