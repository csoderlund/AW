package build.compute;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import util.ErrorReport;
import util.Globals;
import util.LogTime;
import database.DBConn;
import database.MetaData;
import build.compute.Stats;

// Computes p-values for several things:
// 1. per-SNP ASE, using binomial
// 2. SNP replicate anomalies - in disagreement with the overall. pvalue is chi2 using the *overall* ratio
// 3. per-gene and per-transcript ASE, same way
// 4. gene and transcript ASE variability score, using chisq goodness-of-fit on
//		 the snps in the gene or transcript, checking the fit to the constant overall ASE ratio
//
// Note, no p-values computed if both ref+alt have < Globals.MIN_READS; 
// The best binomial pvalue in these cases are insignificant anyway.
public class ASE 
{
	public static final String odSNP = "odSNP";
	public static final String odREP = "odRep"; 
	public static final String odREMARK = "Opposite Direction";
	
	private final double NO_PVALUE = Globals.NO_PVALUE;
	private final int MIN_COV = Globals.MIN_READS;
	private final double PVALUE = Globals.AI_PVALUE;
	
	public ASE(DBConn db)
	{
		long startTime = LogTime.getTime();
		LogTime.PrtDateMsg("Computing pvalues");
		
		mDB = db;
		meta = new MetaData(db);
	
		snpASE();
		transASE();
		geneASE(); 
		
		addDynSNPCols();
		addDynTransCols();
		addDynGeneCols();
		
		updateSNP();
		updateTrans();
		updateGene();
		LogTime.PrtSpMsgTime(0, "Finish pvalues", startTime);
	}
	public void snpASE()
	{
		ResultSet rs;
		PreparedStatement ps;
		
		try
		{
			LogTime.PrtSpMsg(1, "Computing SNP ASE...");
			mDB.executeUpdate("update SNPlib set pvalue=" + NO_PVALUE);
			
			int numToDo = mDB.executeCount("select count(*) from SNPlib where repnum=0 and " +
					"(refcount+altcount>=" + MIN_COV + ")");
			
			ps = mDB.prepareStatement("update SNPlib set pvalue=? where snpid=? and repnum=? and libid=?");
					
			rs = mDB.executeQuery("select snpid, repnum, refcount, altcount, libid from SNPlib " +
					" where (refcount+altcount>=" + MIN_COV + ") order by snpid asc, libid asc, repnum asc");
			int cur_snp = 0, cur_lib = 0;
			double cur_ratio = 0;
			int numComputed = 0, numASE = 0, numDiffReps = 0, numToUpload = 0;
			LogTime.r("SNP/Libs to process " + numToDo);

			while (rs.next())
			{
				int snpid = rs.getInt(1);
				int repnum = rs.getInt(2);
				int refcount = rs.getInt(3);
				int altcount = rs.getInt(4);
				int libid = rs.getInt(5);

				int total = refcount + altcount;
				
				if (repnum == 0) // compute p-value for allele imbalance
				{
					cur_snp = snpid;
					cur_lib = libid;
					cur_ratio = ((double)refcount)/((double)total); // for repnum>0 chisquare test
					double pvalue = Stats.binomialHalf(total,refcount);
					if (pvalue < PVALUE) numASE++;
					
					numComputed++;
					numToDo--;
					
					ps.setDouble(1, pvalue);
					ps.setInt(2, snpid );
					ps.setInt(3, repnum);
					ps.setInt(4, libid);
					ps.addBatch();
					numToUpload++;
					if (numToUpload >= 1000)
					{
						LogTime.r("SNP/Libs to process " + numToDo);
						ps.executeBatch();
						numToUpload = 0;
					}
				}
				else // replicates with the opposite direction of ASE to a significant degree
				{
					if (snpid != cur_snp || libid != cur_lib) // shouldn't happen
					{
						LogTime.warn("SNP Replicate with no repnum 0!");
						continue;
					}
					double pvalue = chiRatio(cur_ratio, refcount, altcount);
					if (pvalue != NO_PVALUE)
					{
						numDiffReps++;							
						ps.setDouble(1, pvalue);
						ps.setInt(2, snpid );
						ps.setInt(3, repnum);
						ps.setInt(4, libid);
						ps.addBatch();
						numToUpload++;
					}
				}
			}
			if (numToUpload > 0) ps.executeBatch();
		
			rs.close();
			
			LogTime.PrtSpMsg(2, "SNP/libs computed:" + numComputed + "   ASE:" + numASE + "   " +
					odREMARK + " Reps:" + numDiffReps);
		}
		catch(Exception e){ErrorReport.prtError(e, "SNP ASE computation failed");}
	}
	public void transASE()
	{
		ResultSet rs;
		PreparedStatement ps, ps2;
		
		try
		{	
			LogTime.PrtSpMsg(1, "Computing Trans ASE...");
			mDB.executeUpdate("update transLib set pvalue=" + NO_PVALUE + " , pvalue2=" + NO_PVALUE);
			
			int numToDo = mDB.executeCount("select count(*) from transLib where repNum=0 and " +
					"(refcount+altcount >=" + MIN_COV + ")  or (refcount2+altcount2 >=" + MIN_COV + ")");
			
			ps = mDB.prepareStatement("update transLib set pvalue=? where transid=? and repnum=? and libid=?");
			ps2 = mDB.prepareStatement("update transLib set pvalue2=? where transid=? and repnum=? and libid=?");
			
			rs = mDB.executeQuery("select transid, repnum, refcount, altcount, refcount2, altcount2, libid, cntSNPcov " +
					" from transLib " +
					" where (refcount+altcount >=" + MIN_COV + ")  or (refcount2+altcount2 >=" + MIN_COV + ")" +
					" order by transid asc, libid asc, repnum asc");
			int cur_trans = 0, cur_lib = 0;
			double cur_ratio = 0, cur_ratio2 = 0;
			int numComputed = 0, numASE = 0, numASE2 = 0;
			int numDiffReps = 0, numDiffReps2 = 0, numToUpload = 0, numToUpload2 = 0;
			LogTime.detail("Trans/Libs to process " + numToDo + "       ");

			while (rs.next())
			{
				int transid = rs.getInt(1);
				int repnum = rs.getInt(2);
				int refcount = rs.getInt(3);
				int altcount = rs.getInt(4);
				int refcount2 = rs.getInt(5);
				int altcount2 = rs.getInt(6);
				int libid = rs.getInt(7);
				int cntSNPcov = rs.getInt(8);

				int total = refcount + altcount;
				int total2 = refcount2 + altcount2;
				
				if (repnum == 0) // ASE p-value
				{
					cur_trans = transid;
					cur_lib = libid;
					
					if (cntSNPcov>0) // changed from refcount+altcount>=20
					{
						cur_ratio = ((double)refcount)/((double)total); // used for repnum>0 chisquare test
						double pvalue = Stats.binomialHalf(total,refcount);
						if (pvalue < PVALUE)numASE++;
						
						ps.setDouble(1, pvalue);
						ps.setInt(2, transid );
						ps.setInt(3, repnum);
						ps.setInt(4, libid);
						ps.addBatch();
						numToUpload++;
					}
					if (total2>=MIN_COV)
					{
						cur_ratio2 = ((double)refcount2)/((double)total2);
						double pvalue2 = Stats.binomialHalf(total2,refcount2);
						if (pvalue2 < PVALUE)numASE2++;
						
						ps2.setDouble(1, pvalue2);
						ps2.setInt(2, transid );
						ps2.setInt(3, repnum);
						ps2.setInt(4, libid);
						ps2.addBatch();
						numToUpload2++;
					}
					numComputed++;
					numToDo--;
					if (numToUpload >= 1000)
					{
						LogTime.r("Trans/Libs to process " + numToDo);
						ps.executeBatch();
						numToUpload = 0;
					}
					if (numToUpload2 >= 1000)
					{
						ps2.executeBatch();
						numToUpload2 = 0;
					}
				}
				////////////////////////////////////////////////////////////////////////////
				else //replicates with the opposite direction of ASE to a significant degree
				{
					if (transid != cur_trans || libid != cur_lib) // shouldn't happen
					{
						LogTime.warn("Trans Replicate with no repnum 0!");
						continue;
					}
					double pvalue = chiRatio(cur_ratio, refcount, altcount);			
					if (pvalue != NO_PVALUE)
					{
						numDiffReps++;					
						ps.setDouble(1, pvalue);
						ps.setInt(2, transid );
						ps.setInt(3, repnum);
						ps.setInt(4, libid);
						ps.addBatch();
						numToUpload++;
					}
					pvalue = chiRatio(cur_ratio2, refcount2, altcount2);						
					if (pvalue != NO_PVALUE)
					{		
						numDiffReps2++;		
						ps2.setDouble(1, pvalue);
						ps2.setInt(2, transid );
						ps2.setInt(3, repnum);
						ps2.setInt(4, libid);
						ps2.addBatch();
						numToUpload2++;
					}		
				}
			}
			if (numToUpload > 0)ps.executeBatch();
			if (numToUpload2 > 0)ps2.executeBatch();
		
			rs.close();
			ps.executeBatch();
			String msg = String.format("%22s %4d   %20s %4d  %s %5s %4d", 
					 "Trans/libs computed: ", numComputed,
					 "SNP Coverage ASE: ", numASE, odREMARK, "Reps: ", numDiffReps);
			LogTime.PrtSpMsg(2, msg);
			
			if (numASE2>0) {
				msg = String.format("%22s %4s   %20s %4d  %s %5s %4d", 
					"", "",
					"Read Count ASE:", numASE2, odREMARK, "Reps:", numDiffReps2);
				LogTime.PrtSpMsg(2, msg);
			}
		}
		catch(Exception e){ErrorReport.prtError(e, "ASE computation failed");}
	}
	
	public void geneASE()
	{
		ResultSet rs;
		PreparedStatement ps, ps2;
		
		try
		{	
			LogTime.PrtSpMsg(1, "Computing Gene ASE with minimum coverage " + MIN_COV);
			mDB.executeUpdate("update geneLib set pvalue=" + NO_PVALUE + ", pvalue2=" + NO_PVALUE);
			
			int numToDo = mDB.executeCount("select count(*) from geneLib where repnum=0 and " +
					"(refcount+altcount >=" + MIN_COV + ")  or (refcount2+altcount2 >=" + MIN_COV + ")");

			ps = mDB.prepareStatement("update geneLib set pvalue=? where geneid=? and repnum=? and libid=?");
			ps2 = mDB.prepareStatement("update geneLib set pvalue2=? where geneid=? and repnum=? and libid=?");
			
			rs = mDB.executeQuery("select geneid, repnum, refcount, altcount, refcount2, altcount2, libid, cntSNPcov " +
					" from geneLib " +
					" where (refcount+altcount >=" + MIN_COV + ")  or (refcount2+altcount2 >=" + MIN_COV + ")" +
					" order by geneid asc, libid asc, repnum asc");
			int cur_gene = 0, cur_lib = 0;
			double cur_ratio = 0, cur_ratio2 = 0;
			int numComputed = 0, numASE = 0, numASE2 = 0;
			int numDiffReps = 0, numDiffReps2 = 0,numToUpload = 0, numToUpload2 = 0;
			LogTime.detail("Gene/Libs to process " + numToDo);

			while (rs.next())
			{
				int geneid = rs.getInt(1);
				int repnum = rs.getInt(2);
				int refcount = rs.getInt(3);
				int altcount = rs.getInt(4);
				int refcount2 = rs.getInt(5);
				int altcount2 = rs.getInt(6);
				int libid = rs.getInt(7);
				int cntSNPcov = rs.getInt(8);

				int total =  refcount  + altcount;
				int total2 = refcount2 + altcount2;
				
				if (repnum == 0) // ASE pvalue
				{
					cur_gene = geneid;
					cur_lib = libid;
					
					if (cntSNPcov>0)
					{
						cur_ratio = ((double)refcount)/((double)total); // for repnum>0 chisquare test
						double pvalue = Stats.binomialHalf(total,refcount);
						if (pvalue < PVALUE) numASE++;
						
						ps.setDouble(1, pvalue);
						ps.setInt(2, geneid );
						ps.setInt(3, repnum);
						ps.setInt(4, libid);
						ps.addBatch();
						numToUpload++;
					}
					if (total2>=MIN_COV)
					{
						cur_ratio2 = ((double)refcount2)/((double)total2);
						double pvalue2 = Stats.binomialHalf(total2,refcount2);
						if (pvalue2 < PVALUE) numASE2++;
						
						ps2.setDouble(1, pvalue2);
						ps2.setInt(2, geneid );
						ps2.setInt(3, repnum);
						ps2.setInt(4, libid);
						ps2.addBatch();
						numToUpload2++;
					}
					numComputed++;
					numToDo--;
					if (numToUpload >= 1000)
					{
						LogTime.r("Gene/Libs to process " + numToDo);
						ps.executeBatch();
						numToUpload = 0;
					}
					if (numToUpload2 >= 1000)
					{
						ps2.executeBatch();
						numToUpload2 = 0;
					}
				}
				////////////////////////////////////////////////////////////////////////////
				else // replicates with the opposite direction of ASE to a significant degree
				{
					if (geneid != cur_gene || libid != cur_lib)
					{
						LogTime.warn("Gene Replicate with no repnum 0 - gene: " + cur_gene + " lib - " + cur_lib);
						continue;
					}
					double pvalue = chiRatio(cur_ratio, refcount, altcount);
					if (pvalue != NO_PVALUE) {
						numDiffReps++;						
						ps.setDouble(1, pvalue);
						ps.setInt(2, geneid );
						ps.setInt(3, repnum);
						ps.setInt(4, libid);
						ps.addBatch();
						numToUpload++;
					}
					pvalue = chiRatio(cur_ratio2, refcount2, altcount2);	
					if (pvalue != NO_PVALUE)
					{	numDiffReps2++;
						ps2.setDouble(1, pvalue);
						ps2.setInt(2, geneid );
						ps2.setInt(3, repnum);
						ps2.setInt(4, libid);
						ps2.addBatch();
						numToUpload2++;
					}		
				}	
			}
			if (numToUpload > 0) ps.executeBatch();
			if (numToUpload2 > 0) ps2.executeBatch();
			rs.close();
			
			LogTime.PrtSpMsg(2, "Gene/libs computed: " + numComputed + "   SNP coverage ASE:" + numASE + 
					"   " + odREMARK + " reps:" + numDiffReps);		
			if (numASE2>0) LogTime.PrtSpMsg(2, "Read Count ASE: " + numASE2 + "   " + odREMARK +" reps: " + numDiffReps2);
		}
		catch(Exception e){ErrorReport.prtError(e, "ASE computation failed");}
	}
	
	private double chiRatio(double cur_ratio, int refCount, int altCount) {
		double pval=NO_PVALUE;
		if (cur_ratio <0 || cur_ratio >1.0) return pval;
		
		int total = refCount+altCount;
		if (total<MIN_COV) return pval;
			
		if ((cur_ratio < 0.5 && refCount > altCount) ||(cur_ratio > 0.5 && refCount < altCount))
		{
			double expect = cur_ratio*((double)total);
			double chi = (refCount - expect)*(refCount - expect)/expect;
			double p = Stats.chisqr(1, chi);
			if (p<PVALUE) pval=p;
		}			
		return pval;
	}
	/****************************************************************************/
	/***************************************************
	 * copy pvalues from SNPlib to SNP dynamic columns
	 */
	private void addDynSNPCols() { 
		try {
			LogTime.PrtSpMsg(2, "Updating database with dynamic SNP columns");
			
			int cntSNP = mDB.executeCount("Select max(SNPid) from SNPlib");
			String [] sql = new String [cntSNP+1];
			for (int i=0; i<cntSNP; i++) sql[i]=null;
			
			ResultSet rs = mDB.executeQuery("Select libName, SNPid, pvalue " +
					"from SNPlib where repNum=0");
			int cnt=0;
			while (rs.next()) {
				int sid = rs.getInt(2);
				String libName = rs.getString(1);
				double pvalue = rs.getDouble(3);
				
				if (sql[sid] == null)  
					 sql[sid]=   "Update SNP set " + libName + "=" + pvalue;
				else sql[sid] +=               "," + libName + "=" + pvalue; 
				
				if (cnt%1000==0) LogTime.r("read " + cnt);
				cnt++;
			}
			rs.close();
			for (int i=0; i<cntSNP; i++) {
				if (sql[i]!=null) {
					sql[i] += " where SNPid=" + i;
					mDB.executeUpdate(sql[i]);
				}
				if (i%1000==0) LogTime.r("entered " + i);
			}
		}
		catch (Exception e) {ErrorReport.die(e, "update pvalues for dynamic columns");}
	}
	/***************************************************
	 * copy pvalues from transLib to trans dynamic columns
	 */
	private void addDynTransCols() {
		try {
			LogTime.PrtSpMsg(2, "Updating database with trans dynamic columns");
			mDB.executeUpdate("UPDATE trans set cntLibAI=0");
			boolean hasReadCnts = meta.hasReadCnt();
			
			int cntTrans = mDB.executeCount("Select max(TRANSid) from transLib");
			String [] sql = new String [cntTrans+1];
			int [] cntAI = new int [cntTrans+1];
			for (int i=0; i<cntTrans; i++) {sql[i]=null; cntAI[i]=0;}
			
			ResultSet rs = mDB.executeQuery("Select libName, TRANSid, pvalue, pvalue2 " +
					"from transLib where repNum=0");
			int cnt=0;
			String suf = Globals.SUF_TOTCNT;
			while (rs.next()) {
				int tid = rs.getInt(2);
				String libName = rs.getString(1);
				double pvalue = rs.getDouble(3);
				double pvalue2 = rs.getDouble(4);
				
				String set =  libName + "=" + pvalue; 
				if (hasReadCnts) set += "," + (libName+suf) + "=" + pvalue2;
				
				if (sql[tid] == null)  sql[tid]="Update trans set " + set;
				else sql[tid] +=   "," + set; 
				
				if (pvalue<Globals.AI_PVALUE) cntAI[tid]++;
				
				if (cnt%1000==0) LogTime.r("read " + cnt);
				cnt++;
			}
			rs.close();
			for (int i=0; i<cntTrans; i++) {
				if (sql[i]!=null) {
					sql[i] += ", cntLibAI=" + cntAI[i] + " where TRANSid=" + i;
					mDB.executeUpdate(sql[i]);
				}
				if (i%1000==0) LogTime.r("entered " + i);
			}
		}
		catch (Exception e) {ErrorReport.die(e, "update pvalues for dynamic columns");}
	}
	/***************************************************
	 * copy pvalues from geneLib to gene dynamic columns
	 */
	private void addDynGeneCols() {
		try {
			LogTime.PrtSpMsg(2, "Updating database with gene dynamic columns");
			mDB.executeUpdate("UPDATE gene set cntLibAI=0");
			boolean hasReadCnt = meta.hasReadCnt();
			
			int cntGene = mDB.executeCount("Select max(GENEid) from geneLib");
			String [] sql = new String [cntGene+1];
			int [] cntAI = new int [cntGene+1];
			for (int i=0; i<cntGene; i++) {sql[i]=null; cntAI[i]=0;}
			
			ResultSet rs = mDB.executeQuery("Select libName, GENEid, pvalue, pvalue2 " +
					"from geneLib where repNum=0");
			int cnt=0;
			String suf = Globals.SUF_TOTCNT;
			while (rs.next()) {
				int tid = rs.getInt(2);
				String libName = rs.getString(1);
				double pvalue = rs.getDouble(3);
				double pvalue2 = rs.getDouble(4);
				
				String set =  libName + "=" + pvalue; 
				if (hasReadCnt) set += "," + (libName+suf) + "=" + pvalue2;
				
				if (sql[tid] == null)  sql[tid]="Update gene set " + set;
				else sql[tid] +=   "," + set; 
				
				if (pvalue<Globals.AI_PVALUE) cntAI[tid]++;
				
				if (cnt%1000==0) LogTime.r("read " + cnt);
				cnt++;
			}
			rs.close();
			for (int i=0; i<cntGene; i++) {
				if (sql[i]!=null) {
					sql[i] += ", cntLibAI=" + cntAI[i] + " where GENEid=" + i;
					mDB.executeUpdate(sql[i]);
				}
				if (i%1000==0) LogTime.r("entered " + i);
			}
		}
		catch (Exception e) {ErrorReport.die(e, "update gene pvalues for dynamic columns");}
	}
	/***********************************************************************/
	/******************************************************
	 * Add SNP remark about bad Rep and isAI
	 */
	private void updateSNP() {
		try {
			LogTime.PrtSpMsg(2, "Updating database with SNP replicate '" + odREMARK + "' remarks");
			mDB.executeUpdate("UPDATE SNP set remark='', cntLibAI=0");
			
			int cntSNP = mDB.executeCount("Select max(SNPid) from SNP");
			int [] badRep = new int [cntSNP];
			int [] isAI = new int [cntSNP];
			for (int i=0; i<cntSNP; i++) {isAI[i]=0; badRep[i]=0;}
			
			int cnt=0;
			ResultSet rs = mDB.executeQuery("SELECT SNPid, libName, pvalue, repNum " +
					" FROM SNPlib WHERE pvalue!=" + Globals.NO_PVALUE);
			while (rs.next()) {
				double pval = rs.getDouble(3);
			
				if (pval<Globals.AI_PVALUE) {
					int snpid = rs.getInt(1);
					int repNum = rs.getInt(4);
					
					if (repNum==0) isAI[snpid]++;
					else badRep[snpid]++; 	
				}
			}
			rs.close();
			
			for (int i=0; i<cntSNP; i++) {
				if (badRep[i]>0 || isAI[i]>0) {
					String rm= (badRep[i]>0) ?  odREP + badRep[i] : "";
					mDB.executeUpdate("UPDATE SNP set " +
							"cntLibAI="+ isAI[i] + ", remark='" + rm + "' where SNPid=" + i);
					cnt++;
				}
			}
			LogTime.PrtSpMsg(3, "Updated SNPs: " + cnt );
		}
		catch (Exception e) {ErrorReport.die(e, "adding SNP remarks");}
	}
	/*********************************************
	 * Add trans OD remarks and cntAI
	 */
	private void updateTrans() {
		try {
			LogTime.PrtSpMsg(2, "Updating database with trans '" + odREMARK + "' remarks");
			mDB.executeUpdate("UPDATE trans set odRmk='', cntSNPAI=0");
			
			String [] libs = meta.getLibAbbr();
			int cntAddTr=0, cntAddTrSn=0;
			int cntTrans = mDB.executeCount("Select max(TRANSid) from trans");
			
			for (int tid=0; tid<cntTrans; tid++) {
				// opposite direction reps
				String trRepMsg="";
				int cntBadRep = mDB.executeCount("SELECT count(*) FROM transLib " +
						" WHERE pvalue!=" + Globals.NO_PVALUE + " and repNum>0 and TRANSid=" + tid);
				if (cntBadRep>0) trRepMsg = odREP + cntBadRep + ";";
				
				// opposite direction SNPs
				String trSnpMsg = updateTransodSNP(tid, libs);
				if (!trSnpMsg.equals("")) {
					cntAddTrSn++;
					trSnpMsg = odSNP + ": " + trSnpMsg + "; ";
				}
				String msg = trSnpMsg + trRepMsg;
				
				// count number of AI SNPs
				int cntAI = mDB.executeCount("Select count(*) from SNP " +
						" join SNPtrans on SNPtrans.SNPid=SNP.SNPid " +
						" where SNP.cntLibAI>0 and SNPtrans.TRANSid=" +tid);
				
				if (!msg.equals("") || cntAI>0) {
					if (!msg.equals("")) {
						if (msg.length() >= Globals.shortLen) msg = msg.substring(0, Globals.shortLen-1);
						msg = msg.substring(0, msg.length()-1);
					}
					mDB.executeUpdate("Update trans set cntSNPAI= " + cntAI + ", odRmk='" + msg + 
							"' where TRANSid=" + tid);
					cntAddTr++;
				}
				if (tid%1000 == 0) 
					LogTime.r("processed #" + tid + " " + cntAddTrSn + " " + cntAddTr);
			}
			LogTime.PrtSpMsg(3,  odSNP + ": " + cntAddTrSn + "    " + odREP + ": " + cntAddTr + "      ");
		}
		catch (Exception e) {ErrorReport.prtError(e, "add Remarks");}
	}
	
	/*****************************************************
	 * compute whether two significant SNPs have opposite direction
	 */
	private String updateTransodSNP(int TRANSid, String [] libs) {
		try {
			String snpMsg="";
			ResultSet rs;
			
			for (String lib : libs) {
				int cnt1=0, cnt2=0;
				rs = mDB.executeQuery("Select refcount, altcount from SNPlib " +
						"JOIN SNPtrans ON SNPlib.SNPid=SNPtrans.SNPid " +
						"where repNum=0 and SNPtrans.TRANSid=" + TRANSid + 
						" and pvalue<" + PVALUE + " and libName='" + lib + "'");
				while (rs.next()) {
					int refCount = rs.getInt(1);
					int altCount = rs.getInt(2);
					if (refCount < altCount) cnt1++;
					else cnt2++;
				}
				rs.close();
				
				if (cnt1>0 && cnt2>0) {
					if (!snpMsg.equals("")) snpMsg += ",";
					snpMsg += lib;
				}
			}	
			return snpMsg;
		}
		catch (Exception e) {ErrorReport.die(e, "add odSNP Remarks");}
		return "";
	}

	private void updateGene() {
		try {
			LogTime.PrtSpMsg(2, "Updating database with gene with SNP AI count");
			mDB.executeUpdate("UPDATE gene set cntSNPAI=0");
			
			int cntSNPAI=0;
			int cntGene = mDB.executeCount("Select max(GENEid) from gene");
			
			for (int gid=0; gid<cntGene; gid++) {
				
				int cntAI = mDB.executeCount("Select count(*) from SNP " +
						" join SNPgene on SNPgene.SNPid=SNP.SNPid " +
						" where SNP.cntLibAI>0 and SNPgene.GENEid=" +gid);
				
				if (cntAI>0) {
					mDB.executeUpdate("Update gene set cntSNPAI= " + cntAI +
							" where GENEid=" + gid);
					cntSNPAI++;
				}
				if (gid%1000 == 0) 
					LogTime.r("processed #" + gid + " " + cntSNPAI);
			}
			LogTime.PrtSpMsg(3,  "Genes with at least 1 AI SNP: " + cntSNPAI + 	"                 ");
		}
		catch (Exception e) {ErrorReport.prtError(e, "add Remarks");}
	}
	private DBConn mDB=null;
	private MetaData meta=null;
}
