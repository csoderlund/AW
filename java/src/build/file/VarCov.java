package build.file;

/***************************************
 * NOTE: code in the file depends on sequential table ids!
 * 		i.e. it will not work if some ids have been removed in SNPlib, gene, etc
 * 
 * The SNPs table is populated with the SNPs
 * Read the bam.ref and bam.alt and make the SNPlib 

 * SNPgene and SNPtrans have already been created in SNPs
 * Use SNPgene and SNPlib to create geneLib of summed counts per library
 * Use SNPtrans and SNPlib to create transLib of summed counts per library
 */
import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.Vector;

import util.ErrorReport;
import util.LogTime;
import util.Globals;
import build.Bmain;
import build.Cfg;
import database.DBConn;
import database.Dynamic;
import database.MetaData;
import build.compute.Compute;
import build.compute.Overview;

public class VarCov {
	private Vector <String> varCovVec;
	private String varCovDir;
	private String chrRoot;
	private Vector <String> chrVec;
	private int numReps=4; // needs to be computed somewhere
	
	public VarCov(DBConn dbc, Cfg c, int only) {
		mDB = dbc; cfg = c;
		varCovVec = cfg.getVarCovVec();
		varCovDir = cfg.getVarCovDir();
		readDB(); // everything needs this
		
		if (only==1) { // called to only execute the clustering and read count assignment
			try {
				boolean hasCDNApos = new MetaData(mDB).hasCDSpos();
				if (!hasCDNApos) {
					LogTime.PrtWarn("No cDNApos values - exiting...");
					return;
				}
				int cnt = mDB.executeCount("select count(*) from SNPlib");
				if (cnt==0) {
					LogTime.PrtWarn("The SNP coverage has not been added ");
					return;
				}
				LogTime.PrtDateMsg("Update trans SNP coverage with read length " + Globals.READ_LEN);
				mDB.executeUpdate("update SNPtrans set included=1");
				mDB.executeUpdate("update transLib set refCount=0, altCount=0");
				deClusterSNPs();
				sumTransLib(true);
				return;
			}
			catch (Exception e) {ErrorReport.prtError(e, "VarCov: clearing tables 1");}
		}
		
		long time = LogTime.getTime();
		LogTime.PrtDateMsg("Load variant coverage " + varCovVec.size() + " files ");
		Dynamic.addLib(dbc);
		try {
			mDB.executeUpdate("Update library set reps=0");
			mDB.executeUpdate("update SNPtrans set included=1");
			mDB.executeUpdate("truncate table SNPlib");
			mDB.executeUpdate("truncate table geneLib");
			mDB.executeUpdate("truncate table transLib");
			mDB.executeUpdate("Update SNP set cntLibCov=0, cntCov=0"); 
		
			mDB.executeUpdate("Update gene set cntSNPCov=0"); // computed from SNP cntLibCov
			mDB.executeUpdate("Update trans set cntSNPCov=0");
		}
		catch (Exception e) {ErrorReport.prtError(e, "VarCov: clearing tables 2");}
		
		addVarCov();
		sumSNPlib();
		sumGeneLib();
		deClusterSNPs();
		sumTransLib(false);
		LogTime.PrtSpMsgTime(0, "Complete variant postprocess ", time);
	}
	/***********************************************
	 * Files have already been verified
	 * Format:
	 * chr1    4923964 4923964 16:0:a:c
	 * Bed files start counting at 0
	 */
	private void addVarCov() {		
		try {
			long time=LogTime.getTime();
			LogTime.PrtSpMsg(1, "Add heterozygous SNP counts");
		
			int totalCnt=0, cntFile=0, addBatch=0, notFound=0;
			PreparedStatement ps = mDB.prepareStatement(
					"INSERT SNPlib SET " +
					"LIBid=?, libName=?, SNPid=?, repNum=?, refCount=?, altCount=?");
			HashMap <String, Integer> cntReps = new HashMap <String, Integer> ();
			
			for (String bedFile : varCovVec) {
				String [] x = bedFile.split(":");
				String file = x[0];
				libName = x[1];
				if (cntReps.containsKey(libName)) cntReps.put(libName, cntReps.get(libName)+1);
				else  cntReps.put(libName, 1);
				
				String repStr = x[2];
				int repNum = Integer.parseInt(repStr);
				cntFile++;
					
				if (!libMap.containsKey(libName)) { // shouldn't 
					System.out.println("No library - ignore: " + file);
					continue;
				}		
				int LIBid = libMap.get(libName);
				mDB.executeUpdate("UPDATE library SET reps=reps+1 where LIBid=" + LIBid);
				
				int cntRead=0;
				String line="";
				String path = varCovDir + "/" + file;
				BufferedReader reader = new BufferedReader ( new FileReader (path ) ); 	
				
				while ((line = reader.readLine()) != null) {	
					cntRead++;
					String [] tok = line.split("\\s+");
					if (tok.length==0) continue;
					if (tok.length!=4) {
						LogTime.PrtSpMsg(3, "File #"  + cntFile +  " " + file +" line# " + cntRead + ": " + line + "                               ");		
						continue;
					}	
					String loc;
					String chr = getChr(tok[0], line);
					if (chr==null) continue;
					
					int pos = Integer.parseInt(tok[1])+1; // for bed files, which use zero offset
					loc = chr + ":" +  pos;	
					if (!snpMap.containsKey(loc)) {
						notFound++;
						continue;
					}
					int SNPid = snpMap.get(loc);
					String [] items = tok[3].split(":");
					int ref, alt;
					try {
						ref = Integer.parseInt(items[0]);
						alt = Integer.parseInt(items[1]);
					}
					catch (Exception e) {
						LogTime.PrtSpMsg(3, "bad ref:alt in file=" + file + " line=" + line);
						continue;
					}
					if (ref > 0 || alt > 0) {
						ps.setInt(1, LIBid);
						ps.setString(2,libName);
						ps.setInt(3, SNPid);
						ps.setInt(4, repNum);
						ps.setInt(5, ref);
						ps.setInt(6, alt);
						ps.addBatch();
						addBatch++; totalCnt++;
					}
					if (addBatch > 1000) {
						addBatch=0;
						ps.executeBatch();
						System.out.print("      File#" + cntFile + " Read:" + cntRead + " Add:" + totalCnt + "\r");
					}
				}	
				if (addBatch > 0) ps.executeBatch();
			}
			for (int val : cntReps.values()) if (val>numReps) val = numReps;
					
			LogTime.PrtSpMsgTime(2, 
					"Add: " + totalCnt + "  Skipped: " + notFound + " Max Reps: " + numReps, time);
			if (cntChrErr>0 || cntChrErr2>0)
				LogTime.PrtSpMsg(3, "  Error with chromosomes: " + (cntChrErr+cntChrErr2));
			if (totalCnt==0) ErrorReport.die("Did not add any variant coverage counts");
		}
		catch (Exception e){ErrorReport.die(e, "Adding variant coverage");}
	}
	/***********************************************
	 * parse chromosome
	 */
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
		if (!chrVec.contains(chr)) {
			if (cntChrErr2<3) LogTime.PrtWarn("Line does not start with a valid chr (i.e. Seqname in GTF)\nLine: " + line);
			else if (cntChrErr2==3) LogTime.PrtWarn("Surpressing further such messages");
			cntChrErr2++;
			return null;
		}
		return chr;
	}	
	/* 
	 * mark SNPs to exclude from summing transcript SNPs in order to approximate  
	 * independent reads for obtaining accurate p-value
	 */
	private void deClusterSNPs()
	{
		int clustRadius = Globals.READ_LEN/2; 
		boolean useCnts=false;
		
		LogTime.PrtSpMsg(1, "Mark SNP clusters to count reads once using radius=" + clustRadius);
		MetaData md = new MetaData(mDB);
		if (!md.hasVarDist()) {
			if (md.hasCDNApos()) new Compute(mDB, 4);
			else {
				LogTime.PrtWarn("No cDNApos in database - skip clustering SNPs");
				return;
			}
		}
	
		try
		{
			HashMap<Integer, Integer> transMap = new HashMap <Integer, Integer> ();
			ResultSet rs = mDB.executeQuery("Select TRANSid, strand " +
					"FROM trans where (cntSNP+cntIndel)>0");
			while (rs.next()) {
				int id = rs.getInt(1);
				String s = rs.getString(2);
				int o = (s.equals("+")) ? 0 : 1;
				transMap.put(id, o);
			}
			int cnt=0, cntEx=0, cntTot=0;
			for (int TRANSid : transMap.keySet()) {
				cnt++;
				if (cnt%1000==0) 
					System.out.print("      processed " + cnt + "\r");
				String order = (transMap.get(TRANSid)==1) ? "DESC" : "";
			
				int cntTrSNP = mDB.executeCount("Select count(*) from SNPtrans where TRANSid=" + TRANSid);
				int [] distList = new  int [cntTrSNP];
				int [] idList = new int [cntTrSNP];
				cntTot+= cntTrSNP;
				
				rs = mDB.executeQuery("SELECT SNPid, dist FROM SNPtrans " +
						" WHERE TRANSid=" + TRANSid + " order by cDNApos " + order);  
				int s=0;
				while (rs.next()) {
					idList[s] = rs.getInt(1);
					distList[s] = rs.getInt(2);
					s++;
				}
				if (useCnts) { // Will, this is so you can add the counts back in
					int [] cntList = new int [cntTrSNP];
					for (int i=0; i<cntTrSNP; i++) {
						cntList[i] = mDB.executeCount("SELECT sum(refCount)+sum(altCount) FROM SNPlib " +
							"WHERE SNPid=" + idList[i]);
					}
				}
				HashSet<Integer> excluded = new HashSet<Integer>();
				int head=0, cur=0,  sum=0;
				while (head<cntTrSNP-1) {
					sum = distList[head];
					cur = head+1;
					while (cur<cntTrSNP-1 && sum <clustRadius) {
						excluded.add(idList[cur]);
						cntEx++;
						sum += distList[cur];
						cur++;
					}
					head=cur;
				}
				PreparedStatement ps = mDB.prepareStatement("update SNPtrans set included=0 " +
						"where TRANSid=? and SNPid=?");
				ps.setInt(1, TRANSid);
				for (int snpid : excluded)
				{
					ps.setInt(2, snpid);
					ps.addBatch();
				}
				ps.executeBatch();
			}// end loop through trans
			int p = (int)(((float) cntEx/(float) cntTot)*100.0);
			LogTime.PrtSpMsg(2, "Excluded SNP/trans pairs for summing of counts " + cntEx + "(" + p + "%)");
		}
		catch (Exception e)
		{
			ErrorReport.die(e, "doing SNP decluster");	
		}		
	}
	
	/****************************************************************
	 * Add rep 0 with summed ref/alt from reps
	 */
	private void sumSNPlib() {
		LogTime.PrtSpMsg(1, "Sum ref/alt SNP coverage from replicas for " 
				+ libMap.size() + " libraries and " + snpMap.size() + " SNPs");
		long time = LogTime.getTime();
			
		try {		
			int nSNP = mDB.executeCount("Select count(*) from SNP");	
			int [] cov20 = new int [nSNP+1];
			int [] covCnt = new int [nSNP+1];
			for (int i=0; i<nSNP; i++) cov20[i]=covCnt[i]=0;
			
			PreparedStatement ps = mDB.prepareStatement(
					"INSERT SNPlib SET " +
					"LIBid=?, libName=?, SNPid=?, repNum=?, refCount=?, altCount=?");
			int addBatch=0, cntInsert=0, nlib=0;
			
			for (String libName : libMap.keySet()) {
				int LIBid = libMap.get(libName);
				int nsnp=0;
				nlib++;
						
				for (String loc : snpMap.keySet()) {
					nsnp++;
					int SNPid = snpMap.get(loc);
					ResultSet rs = mDB.executeQuery("SELECT sum(refCount), sum(altCount) " +
						" FROM SNPlib WHERE LIBid=" + LIBid + " and SNPid=" + SNPid + " and repNum>0");					
					int refCount=0, altCount=0;
					if (rs.next()) {
						refCount = rs.getInt(1);
						altCount = rs.getInt(2);
					}	
					if (refCount>0 || altCount>0) {
						ps.setInt(1, LIBid);
						ps.setString(2,libName);
						ps.setInt(3, SNPid);
						ps.setInt(4, 0);
						ps.setInt(5, refCount);
						ps.setInt(6, altCount);
						ps.addBatch();
						addBatch++; cntInsert++;
						
						int sum = refCount+altCount;
						if (sum>=Globals.MIN_READS) cov20[SNPid]++;
						covCnt[SNPid]+= sum;
					}
					if (addBatch > 1000) {
						addBatch=0;
						ps.executeBatch();
						System.out.print("  " + nlib + ". " + libName + " SNP#" + nsnp + "\r");
					}	
				} 
			} 
			System.out.print("                                                                     \r");
			if (addBatch > 0) ps.executeBatch();
			// used in sumTransLib - so must be done here.
			for (int i=0; i<nSNP; i++) {
				if (cov20[i]>0 || covCnt[i]>0)
					mDB.executeUpdate("Update SNP set cntLibCov=" + cov20[i] + ",cntCov=" + covCnt[i] + 
						" where SNPid=" + i);
			}
			LogTime.PrtSpMsgTime(2, "Finish updating SNP Lib; insert " + cntInsert, time);	
		}
		catch (Exception e) {ErrorReport.die(e, "doing SNPlib sums");}
	}
	

	/***************************************************************
	 * create geneLib table, summing SNP ref/alt for all libs for all replicas (including 0)
	 */
	private void sumGeneLib() {
		LogTime.PrtSpMsg(1, "Sum ref/alt for gene coverage");
		long time = LogTime.getTime();
		ResultSet rs;
		HashMap <Integer, String> geneMap = new HashMap <Integer, String> (); 
		
		try {
			// need geneName to insert into table geneLib table
			rs = mDB.executeQuery("Select GENEid, geneName from gene");
			while (rs.next()) {
				geneMap.put(rs.getInt(1), rs.getString(2));
			}
			rs.close();
			int numGenes = geneMap.size();
			
			// get all gene:SNP associations
			String [] gene = new String [numGenes+1];
			for (int i=0; i<=numGenes; i++) gene[i]="";
			rs = mDB.executeQuery("SELECT SNPid, GENEid FROM SNPgene");
			while (rs.next()) {
				gene[rs.getInt(2)] +=  rs.getInt(1) + ":";
			}
			int cntInsert=0, cnt=0;
			for (int i=0; i<=numGenes; i++) if (!gene[i].equals("")) cnt++;
			LogTime.PrtSpMsg(2, "Read " + numGenes + " genes of which " + cnt + " have variants");
	
			for (int GENEid=1; GENEid<=numGenes; GENEid++) {
				if (gene[GENEid].equals("")) continue;	
				String [] SNPids = gene[GENEid].split(":");
			
				for (String libName : libMap.keySet()) {
					int LIBid = libMap.get(libName);
					int [] refCount = new int [numReps+1];
					int [] altCount = new int [numReps+1];	
					for (int r=0; r<=numReps; r++) refCount[r]=altCount[r]=0;
					
					// SNP loop: all SNPs for this gene, the Ref/Alt get summed per rep (including 0)
					for (int i=0; i< SNPids.length; i++) {
						int SNPid = Integer.parseInt(SNPids[i]);
						
						rs = mDB.executeQuery("SELECT repNum, refCount, altCount FROM SNPlib " +
								"WHERE LIBid=" + LIBid + " and SNPid=" + SNPid);			
						while (rs.next()) {
							int r = rs.getInt(1);
							refCount[r] += rs.getInt(2);
							altCount[r] += rs.getInt(3);
						}
					}
	
					// add ref:alt counts for all Libs/Reps for this Gene 
					for (int r=0; r<=numReps; r++) {		
						if (refCount[r]==0 && altCount[r]==0) continue;
						int sCov = 0;
							
						if (r==0) {
							sCov = mDB.executeCount("select count(*) from SNPlib " +
									" join SNPgene on SNPlib.SNPid=SNPgene.SNPid" +
									" where SNPlib.refcount+SNPlib.altcount>=" + Globals.MIN_READS +
									" and SNPgene.GENEid=" + GENEid +
									" and LIBid=" + LIBid);
						}
						mDB.executeUpdate("INSERT geneLib SET " +
							" GENEid=" 		+ GENEid + 
							",geneName= " 	+ quote(geneMap.get(GENEid)) +
							",LIBid="  		+ LIBid + 
							",libName= " 		+ quote(libName) + 
							",repNum=" 		+ r + 
							",cntSNPCov=" 	+ sCov +
							",refCount=" 		+ refCount[r] + 
							",altCount=" 		+ altCount[r] 
							);
						cntInsert++;
					}	
		
				} // lib loop
				// Number of SNP with >=20 for any library
				int cntCov20 = mDB.executeCount("select count(*) from SNP " +
						" join SNPgene on SNP.SNPid=SNPgene.SNPid" +
						" where SNP.cntLibCov>0 and SNPgene.GENEid=" + GENEid);
				if (cntCov20>0) 
					mDB.executeUpdate("Update gene set cntSNPCov=" + 
							cntCov20 + " where GENEid=" + GENEid);
				
				if ((cntInsert % 1000) == 0)
					System.err.print("      Gene# " + GENEid + " add " + cntInsert + "...\r");		
			} // gene loop
			System.out.print("                                                                 \r");
			LogTime.PrtSpMsgTime(2, "Finish gene Lib add " + cntInsert, time);
		}
		catch (Exception e) {ErrorReport.die(e, "doing sums for geneLib");}
	}
	/***************************************************************
	 * create transLib table, summing SNP ref/alt for all libs for all replicas (including 0)
	 */
	private void sumTransLib(boolean update) {
		LogTime.PrtSpMsg(1, "Sum ref/alt to transLib");
		long time = LogTime.getTime();
		ResultSet rs;
		HashMap <Integer, String> transMap = new HashMap <Integer, String> (); 
		
		try {
			rs = mDB.executeQuery("Select TRANSid, transName from trans");
			while (rs.next()) {
				transMap.put(rs.getInt(1), rs.getString(2));
			}
			rs.close();
			int numTrans = transMap.size();
			
			String [] transIn = new String [numTrans+1];
			for (int i=0; i<=numTrans; i++) transIn[i]="";
			
			rs = mDB.executeQuery("SELECT SNPid, TRANSid FROM SNPtrans where included=1");
			while (rs.next()) {
				transIn[rs.getInt(2)] +=  rs.getInt(1) + ":";
			}
			
			int cntInsert=0, cnt=0;
			for (int i=0; i<=numTrans; i++) if (!transIn[i].equals("")) cnt++;
			LogTime.PrtSpMsg(2, "Read " + numTrans + " trans of which " + cnt + " have variants");
			
			for (int TRANSid=1; TRANSid<=numTrans; TRANSid++) {
				if (transIn[TRANSid].equals("")) continue;
				
				String [] tranSNPs = transIn[TRANSid].split(":");
				for (String libName : libMap.keySet()) {
					int LIBid = libMap.get(libName);
					int [] refCount = new int [numReps+1];
					int [] altCount = new int [numReps+1];	
					for (int r=0; r<=numReps; r++) refCount[r]=altCount[r]=0;
					
					// sum SNP loop: this includes rep0
					for (int i=0; i< tranSNPs.length; i++) {
						if (tranSNPs[i].equals("")) continue;
						
						int SNPid = Integer.parseInt(tranSNPs[i]);
						rs = mDB.executeQuery("SELECT repNum, refCount, altCount " +
								" FROM SNPlib WHERE LIBid=" + LIBid + " and SNPid=" + SNPid);			
						while (rs.next()) {
							int r = rs.getInt(1);
							refCount[r] += rs.getInt(2);
							altCount[r] += rs.getInt(3);
						}
					}
		
					for (int r=0; r<=numReps; r++) {		
						if (refCount[r]==0 && altCount[r]==0) continue;
						cntInsert++;
						if (update) {
							mDB.executeUpdate("update transLib SET "  +
								" refCount=" + refCount[r] +",altCount=" + altCount[r] +
								" where TRANSid=" + TRANSid + " and LIBid=" + LIBid + " and repNum=" + r
								);
							continue;
						}
					
						int sCov=0;
						if (r==0) {
							sCov = mDB.executeCount("select count(*) from SNPlib " +
									" join SNPtrans on SNPlib.SNPid=SNPtrans.SNPid" +
									" where SNPlib.refcount+SNPlib.altcount>=" + Globals.MIN_READS +
									" and SNPtrans.TRANSid=" + TRANSid +
									" and LIBid=" + LIBid);
						}
						mDB.executeUpdate("INSERT transLib SET " +
								" TRANSid=" + TRANSid +
								",transName= " 	+ quote(transMap.get(TRANSid)) + 
								",LIBid="  + LIBid + 
								",libName= " 		+ quote(libName) + 
								",repNum=" + r + 
								",cntSNPCov=" + sCov +
								",refCount=" + refCount[r] + 
								",altCount=" + altCount[r]);
				
					}
				} // end lib loop
				// Number of SNP with >=20 for any library, where cntLibCov is the number over 20 per SNP
				int cntCov20 = mDB.executeCount("select count(*) from SNP " +
								" join SNPtrans on SNP.SNPid=SNPtrans.SNPid" +
								" where SNP.cntLibCov>0 and SNPtrans.TRANSid=" + TRANSid);
				if (cntCov20>0) 
					mDB.executeUpdate("Update trans set cntSNPCov=" + 
							cntCov20 + " where transid=" + TRANSid);
				
				if ((cntInsert % 1000) == 0)
					System.err.print("      Trans# " + TRANSid + " add " + cntInsert + "...\r");		
			} // trans loop
			System.out.print("                                                                 \r");
			LogTime.PrtSpMsgTime(2, "Finish trans Lib add " + cntInsert, time);
		}
		catch (Exception e) {ErrorReport.die(e, "doing sums for transLib");}
	}

	private void readDB() {
		ResultSet rs;
		try {	
			MetaData md = new MetaData(mDB);
			chrRoot = md.getChrRoot();
			chrVec = md.getChr();
			
			rs = mDB.executeQuery("Select LIBid, libName from library");
			while (rs.next()) {
				libMap.put(rs.getString(2), rs.getInt(1));
			}	
			rs = mDB.executeQuery("Select SNPid, chr, pos from SNP");
			while (rs.next()) {
				snpMap.put(rs.getString(2).toLowerCase() + ":" + rs.getString(3), rs.getInt(1));
			}
			rs.close();		
		}
		catch (Exception e) {ErrorReport.die(e, "reading DB ");}		
	}
	
	private String quote(String word) {return "'" + word + "'";}
	private DBConn mDB=null;
	private Cfg cfg=null;
	private String libName="";
	private HashMap <String, Integer> libMap = new HashMap <String, Integer> (); // name LIBid
	private HashMap <String, Integer> snpMap = new HashMap <String, Integer> (); //chr:pos SNPid
}
