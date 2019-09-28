package build.compute;
/***********************************************************
 * Compute dynamic gene/trans/SNPs information for each library:
 * 		alt+ref, ratio, cntLib
 */

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;

import build.file.VarAnno;

import database.DBConn;
import database.MetaData;
import util.Globals;
import util.ErrorReport;
import util.LogTime;

public class Compute {
	private final String MISSENSE = "missense";
	private final String DAMAGING = "SIFT=delet"; // EVP
	private final String HIGH = VarAnno.HIGH; // snpEFF
	
	public Compute (DBConn dbc, int task) {	
		try {
			mDB = dbc;
			meta = new MetaData(mDB); // needs to be reread to update
			
			long time = LogTime.getTime();
			LogTime.PrtDateMsg("Update mySQL tables");
			
			if (task==0) {
				LogTime.PrtSpMsg(1, "Add ref/alt information to main tables");
				addRefAlt("SNPid",  "SNP", "SNPlib", false); // SNPs
				addRefAlt("GENEid",  "gene", "geneLib", false); // SNPs
				addRefAlt("TRANSid", "trans","transLib",false);
				addRefAlt("GENEid",  "gene", "geneLib", true); // Reads
				addRefAlt("TRANSid", "trans","transLib",true);
				
				mDB.executeUpdate("Update trans set rank=0, totalRead=0.0");
				addRankTrans();
				
				mDB.executeUpdate("Update SNP set isMissense=0, isDamaging=0");
				mDB.executeUpdate("Update trans set cntMissense=0, cntDamage=0");
				mDB.executeUpdate("Update SNPtrans set isMissense=0, isDamaging=0, dist=0");
				mDB.executeUpdate("Update gene set cntMissense=0");
				
				addCntsSNP();
				addCntsTrans();
				addCntsGene();
				addSNPDist();
				addLibTotals();
				//new Score(dbc); // quite working... only computes for a few
			} 
			else if (task==1){ // for  GenTrans and Varanno updated
				mDB.executeUpdate("Update SNP set isMissense=0, isDamaging=0");
				mDB.executeUpdate("Update trans set cntMissense=0, cntDamage=0");
				mDB.executeUpdate("Update SNPtrans set isMissense=0, isDamaging=0, dist=0");
				mDB.executeUpdate("Update gene set cntMissense=0");
				
				addCntsSNP();
				addCntsTrans();
				addCntsGene();
				addSNPDist();
			}	
			else if (task==2) { // Read counts have been added
				addRefAlt("GENEid",  "gene", "geneLib", true); // Reads
				addRefAlt("TRANSid", "trans","transLib",true);
				
				mDB.executeUpdate("Update trans set rank=0, totalRead=0.0");
				addRankTrans();
			}
			else if (task==3) { // SNP counts for trans/genes have been updated with new READ_LEN
				addRefAlt("SNPid",  "SNP", "SNPlib", false); // SNPs
				addRefAlt("GENEid",  "gene", "geneLib", false); // SNPs
				addRefAlt("TRANSid", "trans","transLib",false);
				
				mDB.executeUpdate("Update trans set rank=0, totalRead=0.0");
				addRankTrans();
			}
			else if (task==4) addSNPDist(); // may be called from VarCov
			
			LogTime.PrtSpMsgTime(0,"Complete computations", time);
		}
		catch (Exception e) {ErrorReport.prtError(e, "Compute error");}
	}
	/********************************************
	 * Add RefAlt to table
	 */
	private void addRefAlt(String IDCol, String table, String tableLib, boolean isRead) {
		try { 
			HashSet <Integer> entitySet = new HashSet <Integer> ();
			ResultSet rs = mDB.executeQuery("Select " + IDCol + " from " + table);
			while (rs.next()) entitySet.add(rs.getInt(1));
			rs.close();
			
			String r = Globals.PRE_REFCNT;
			String a = Globals.PRE_ALTCNT;
			String s = Globals.SUF_TOTCNT;
			String fieldRefAlt;
			if (isRead) fieldRefAlt = 	"refCount" + s + ", altCount" + s + " ";
			else fieldRefAlt = 			"refCount" +     ", altCount ";
			
			int cnt = 0, addCnt=0;
			for (Integer ID : entitySet) { 
				cnt++;
				if (cnt % 1000 == 0) LogTime.r(table + " #" + cnt + " " + ID);
				
				int cntLib=0;
				String ratSQL = "UPDATE " + table + " SET ";
				rs = mDB.executeQuery("SELECT libName, " + fieldRefAlt +
							"FROM " + tableLib + " WHERE " + IDCol + "=" + ID + " and repNum=0");	
				while (rs.next()) {							
					String libName = rs.getString(1);
					int ref = rs.getInt(2);
					int alt = rs.getInt(3);
					if (ref!=0 || alt!=0) {
						cntLib++;
						if (isRead) {
							ratSQL += r + libName + s + "=" + ref + ",";
							ratSQL += a + libName + s + "=" + alt + ",";
						}
						else {
							ratSQL += r + libName + "=" + ref + ",";
							ratSQL += a + libName + "=" + alt + ",";
						}
					}
				}
				rs.close();
				
				if (cntLib>0) {
					addCnt++;
					if (! table.equals("SNP")) ratSQL += " cntLib=" + cntLib;
					else ratSQL = ratSQL.substring(0, ratSQL.length()-1); // get rid of comma
					mDB.executeUpdate(ratSQL +  " WHERE " + IDCol + " =" + ID);
				}
			}	
			if (isRead) {
				if (addCnt>0) LogTime.PrtSpMsg(2, "Added ref:alt " + addCnt + " read counts to " + table + "    ");
			}
			else LogTime.PrtSpMsg(2, "Added ref:alt " + addCnt + " SNPs counts to " + table  + "     ");
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Compute ratio error");
		}
	}
	/**************************************************
	 * SIFT: Positions with normalized probabilities less than 0.05 are predicted to be deleterious,
	 * those greater than or equal to 0.05 are predicted to be tolerated. 
	 * 
	 * This is done here instead of in VarAnno.java as the missense may be computed by GenTrans
	 */
	private void addCntsSNP() {
		try {
			LogTime.PrtSpMsg(1, "Add counts for missense and damaged/high SNP");
			int cntSNP = mDB.executeCount("Select count(*) from SNP");
			int [] snpDam = new int [cntSNP+1];
			int [] snpMis = new int [cntSNP+1];
			for (int i=0; i<=cntSNP; i++) snpDam[i]=snpMis[i]=0;
			
			ResultSet rs = mDB.executeQuery("Select SNPid,  effectList from SNP");
			while (rs.next()) {
				int SNPid = rs.getInt(1);
				String funList = rs.getString(2);
				if (funList==null || funList.length()==0) continue;
				
				int isMis=0, isNotMis=0, isDam=0, isNotDam=0;
				String [] fun = funList.split(";");
				for (int i=0; i<fun.length; i++ ) {
					if (fun[i].contains(MISSENSE)) isMis++;
					else isNotMis++;
					if (fun[i].contains(DAMAGING) || fun[i].contains(HIGH)) isDam++;
					else isNotDam++;
				}
				if (isDam>0 && isNotDam==0) snpDam[SNPid]=1;
				else if (isDam>0 && isNotDam>0) snpDam[SNPid]=2;
				if (isMis>0 && isNotMis==0) snpMis[SNPid]=1;
				else if (isMis>0 && isNotMis>0) snpMis[SNPid]=2;
			}
			rs.close();
			
			mDB.openTransaction();
			PreparedStatement ps = mDB.prepareStatement("update SNP set " +
					"isDamaging=?, isMissense=? where SNPid=?");
			int cntS=0, cntAdd=0;
			for (int i=0; i<=cntSNP; i++) {
				if (snpMis[i]>0) {
					ps.setInt(1, snpDam[i]);
					ps.setInt(2, snpMis[i]);
					ps.setInt(3, i);
					ps.addBatch();
					cntAdd++;
					if (cntAdd==1000) {
						ps.executeBatch();
						cntAdd=0;
					}
					cntS++;
				}
			}	
			if (cntAdd>0) ps.executeBatch();
			mDB.closeTransaction();
			LogTime.PrtSpMsg(2, "Updated SNPs: " + cntS + "                   ");
		}
		catch (Exception e) {ErrorReport.prtError(e, "add SNP counts error");}
	}
	/********************************************
	 * add missense counts to transcripts
	 * done here because missense may be read in VarCov or computed in GenTrans
	 */
	private void addCntsTrans() {
		try {
			LogTime.PrtSpMsg(1, "Add missense et al counts to transcripts");
			int cntTrans = mDB.executeCount("Select count(*) from trans");
			int [] cntMis = new int [cntTrans+1];
			int [] cntDam = new int [cntTrans+1];
			for (int i=0; i<=cntTrans; i++) cntDam[i]=cntMis[i]=0;
			ArrayList <String> snpTransVec = new ArrayList <String> ();
			
			ResultSet rs = mDB.executeQuery(
					"Select SNPtrans.TRANSid, SNPtrans.effect, SNP.SNPid " +
					" from SNPtrans " +
					" join SNP on SNP.SNPid=SNPtrans.SNPid");
			while (rs.next()) {
				int TRANSid = rs.getInt(1);
				String funList = rs.getString(2);
				int SNPid = rs.getInt(3);
				
				if (funList==null || funList.equals("")) continue;
				int mis=0, dam=0;
				if (funList.contains(MISSENSE)) {
					cntMis[TRANSid]++;
					mis=1;
				}
				if (funList.contains(DAMAGING) || funList.contains(HIGH)) {
					cntDam[TRANSid]++;
					dam=1;
				}
				if (mis==1 || dam==1) snpTransVec.add(TRANSid + ":" + SNPid + ":" + mis + ":" + dam);
			}
			rs.close();
			
			mDB.openTransaction();
			PreparedStatement ps0 = mDB.prepareStatement("Update trans SET " +
					" cntMissense=?, cntDamage=? where TRANSid=?");
			// update trans
			int cntT=0, cntS=0, cntAdd=0;
			for (int i=0; i<=cntTrans; i++) {
				if (cntMis[i]==0) continue;
				
				ps0.setInt(1, cntMis[i]);
				ps0.setInt(2, cntDam[i]);
				ps0.setInt(3, i);
				ps0.addBatch();
				cntAdd++;
				if (cntAdd==1000) {
					ps0.executeBatch();
					cntAdd=0;
				}
				cntT += cntMis[i];
				cntS += cntDam[i];
			}
			if (cntAdd>0) ps0.executeBatch();
			mDB.closeTransaction();
			
			cntAdd=0;
			mDB.openTransaction();
			PreparedStatement ps1 = mDB.prepareStatement("Update SNPtrans SET " +
					" isMissense=?, isDamaging=? where TRANSid=? and SNPid=?");
			// update SNPtrans -- these two fields depend on locations so are trans specific
			for (String st : snpTransVec) {
				String [] tok = st.split(":");
				ps1.setInt(1, Integer.parseInt(tok[2]));
				ps1.setInt(2, Integer.parseInt(tok[3]));
				ps1.setInt(3, Integer.parseInt(tok[0]));
				ps1.setInt(4, Integer.parseInt(tok[1]));
				ps1.addBatch();
				cntAdd++;
				if (cntAdd==1000) {
					ps1.executeBatch();
					cntAdd=0;
				}
			}
			if (cntAdd>0) ps0.executeBatch();
			mDB.closeTransaction();
			LogTime.PrtSpMsg(2, "Trans Missense SNPS: " + cntT +  " Damaging: " + cntS);
		}
		catch (Exception e) {ErrorReport.die(e, "add Trans counts");}
	}
	/********************************************
	 * add missense counts to genes
	 * TODO update cntAI and cntCov
	 */
	private void addCntsGene() {
		try {
			LogTime.PrtSpMsg(1, "Add missense et al counts to genes");
	
			HashMap <Integer, Integer> damGeneMap = new HashMap <Integer, Integer>  ();
			ResultSet rs = mDB.executeQuery(
					"Select SNPgene.GENEid from SNPgene " +
					"join SNP on SNP.SNPid=SNPgene.SNPid " +
					"where SNP.isMissense>0");
			while (rs.next()) {
				int geneid = rs.getInt(1);
				if (damGeneMap.containsKey(geneid)) damGeneMap.put(geneid, damGeneMap.get(geneid)+1);
				else damGeneMap.put(geneid, 1);
			}
			rs.close();
			
			for (int geneid : damGeneMap.keySet()) {
				mDB.executeUpdate("Update gene set cntMissense=" + damGeneMap.get(geneid) +
						" where GENEid=" + geneid);
			}
			LogTime.PrtSpMsg(2, "Gene with missense SNPs: " + damGeneMap.size());
		}
		catch (Exception e) {ErrorReport.die(e, "add Trans counts");}
	}
	/***************************************************
	 * add library totals
	 */
	private void addLibTotals() {
		try {
			LogTime.PrtSpMsg(1, "Add library sizes                              ");
			int nLib = mDB.executeCount("Select count(*) from library");
			
			for (int lid=1; lid<=nLib; lid++) {
				int varRefSize, varAltSize, varLibSize;
				int readRefSize=0, readAltSize=0, readLibSize=0;
				if (meta.hasReadCnt()) {
					readRefSize = mDB.executeCount("Select sum(refCount2) from transLib where LIBid=" + lid);
					readAltSize = mDB.executeCount("Select sum(altCount2) from transLib where LIBid=" + lid);
					readLibSize = mDB.executeCount("Select sum(totCount2) from transLib where LIBid=" + lid);
				}
				varRefSize = mDB.executeCount("Select sum(refCount) from transLib where LIBid=" + lid);
				varAltSize = mDB.executeCount("Select sum(altCount) from transLib where LIBid=" + lid);
				varLibSize = varRefSize+varAltSize;
			
				mDB.executeUpdate("Update library set " +
					"varRefSize=" + varRefSize + ", varAltSize=" + varAltSize + ", varLibSize=" + varLibSize +
					", readRefSize=" + readRefSize + ", readAltSize=" + readAltSize + ", readLibSize=" + readLibSize +
					" where LIBid=" + lid);
			}		
		}
		catch (Exception e) {ErrorReport.prtError(e, "Compute addLibTotals error");}
	}
	/************************************************
	 * The best trans for a gene has the highest totCount2 (total expression level)
	 * or if no reads, use refCount+altCOunt
	 */
	private void addRankTrans() {
		long st = LogTime.getTime();
		LogTime.PrtSpMsg(1, "Compute best trans per gene");
		
		ResultSet rs;
		try {	
			int cntGenes = mDB.executeCount("SELECT count(*) from gene");
			int [] geneidList = new int [cntGenes];
			rs = mDB.executeQuery("SELECT GENEid from gene");
			int g=0;
			while (rs.next()) geneidList[g++] = rs.getInt(1);
			rs.close();
			
			int cnt=0;
			for (int geneid : geneidList) {
				int cntTrans = mDB.executeCount("Select count(*) from trans where GENEid=" + geneid);
				Trans [] trList = new Trans [cntTrans];
				
				rs = mDB.executeQuery("SELECT transid, cntSNP from trans where GENEid=" + geneid);
				int t=0;
				while (rs.next()) { 
					trList[t++] = new Trans(rs.getInt(1), rs.getInt(2));
				}
				for (Trans tr : trList) { // sum counts from all libraries
					rs = mDB.executeQuery("SELECT refCount, altCount, refCount2, altCount2, totCount2 from transLib " +
							" where TRANSid=" + tr.id + 
							" and repNum=0 and (refCount>0 or altCount>0 or refCount2>0 or altCount2>0)");
					while (rs.next()) {
						tr.add(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5));
					}
					rs.close();
				}
				
				Arrays.sort(trList);
				int rank=1;
				for (Trans tr : trList) {
					if (tr.tot==0 && tr.tot2==0) continue;
					
					int total = (tr.tot2>0) ? tr.tot2 : tr.tot;
					mDB.executeUpdate("Update trans set " +
							" rank=" + rank + "," + " totalRead=" + total + 
							" where TRANSid=" + tr.id);
					rank++;
				}
				if (rank>1) cnt++;
				if (cnt % 1000 == 0) 
					LogTime.r("processed " + cnt);
			} // end loop through genes	
			LogTime.PrtSpMsgTime(2, "Rank=1: " + cnt + "                           ", st);
		}
		catch (Exception e) {ErrorReport.prtError(e, "add Best Trans");}
	}
	// a transcript will have a constant number snp, but a ref;alt for each library
	private class Trans implements Comparable<Trans>{
		private Trans (int id, int snp) {
			this.id = id;
			this.snp = snp;
		}
		private void add(int ref, int alt, int ref2, int alt2, int t2) {
			if (ref>alt && ref2>alt2) dir++;
			else if (ref<alt && ref2<alt2) dir++;
			tot += (ref+alt);
			tot2 += (ref2+alt2);
			tottot2 += t2; // this is true total
		}
		public int compareTo(Trans tr) {
			 if (tr.tot2 < tot2) return -1;
			 if (tr.tot2 > tot2) return 1;
			 // read count probably never ties 
			 if (tr.tot < tot) return -1; 
			 if (tr.tot > tot) return 1;
			 if (tr.dir < dir) return -1;
			 if (tr.dir > dir) return 1;
			 if (tr.snp < snp) return -1;
			 if (tr.snp > snp) return 1;
			 if (tr.tottot2 < tottot2) return -1;
			 if (tr.tottot2 > tottot2) return 1;
			 return 0;
		 }	
		int id, snp, tot=0, tot2=0, tottot2=0;
		int dir=0;
	}
	/***************************************************
	 * Add distance between SNPs
	 */
	private void addSNPDist() {
		int cntLt=0, cntTotal=0;
		int span = Globals.READ_LEN/2;
		try {
			LogTime.PrtSpMsg(1, "Add distances between variants");
			int cntSNP = mDB.executeCount("Select count(*) from SNP");
			String [] diffList = new String [cntSNP];
			for (int i=0; i<cntSNP; i++) diffList[i]=";";
			
			HashMap<Integer, Integer> transMap = new HashMap <Integer, Integer> ();
			ResultSet rs = mDB.executeQuery("Select TRANSid, strand, start " +
					"FROM trans where (cntSNP+cntIndel)>0");
			while (rs.next()) {
				int id = rs.getInt(1);
				String s = rs.getString(2);
				int start = rs.getInt(3);
				if (s.equals("-")) start = -start;
				transMap.put(id, start);
			}
			rs.close();
			LogTime.PrtSpMsg(2, "Trans with >0 variants " + transMap.size());
			
			int cnt=0;
			for (int TRANSid : transMap.keySet()) {
				cnt++;
				if (cnt%1000==0) 
					LogTime.r("processed " + cnt);
				int start = transMap.get(TRANSid);
				String order = "";
				if (start<0) {
					start = -start;
					order = " DESC";
				}
				int cntTrSNP = mDB.executeCount("Select count(*) from SNPtrans where TRANSid=" + TRANSid);
				int [] pos = new  int [cntTrSNP];
				int [] snpid = new int [cntTrSNP];
				
				rs = mDB.executeQuery("SELECT SNPid, cDNApos FROM SNPtrans " +
						" WHERE TRANSid=" + TRANSid + " order by cDNApos " + order);  
				int i=0;
				while (rs.next()) {
					snpid[i] = rs.getInt(1);
					pos[i] = rs.getInt(2);
					i++;
				}
				rs.close();
				
				// enter into database, last variant has diff of 0, which is default
				for (i=0; i<cntTrSNP-1; i++) {
					int diff = Math.abs(pos[i+1]-pos[i]);
					if (diff<=span) cntLt++;
					cntTotal++;
				
					mDB.executeUpdate("UPDATE SNPtrans " +
							" SET dist=" + diff +   
							" where SNPid=" + snpid[i] + " and TRANSid=" + TRANSid);
				}
			}
			int p = (int)(((float) cntLt/(float) cntTotal)*100.0);
			LogTime.PrtSpMsg(2, "SNPs distance< " + span +": "  + cntLt + "(" + p + "%)");
		}
		catch (Exception e) {ErrorReport.prtError(e, "add SNP distance");}
	}
	DBConn mDB=null;
	MetaData meta=null;
}
