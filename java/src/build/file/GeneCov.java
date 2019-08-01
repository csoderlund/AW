package build.file;
/****************************************************
 * loads the express results into transLib table,
 * then updates trans table with read refCount2 and altCount2
 * CAS 2/27/14 - add rep0 for transcripts that have no SNPs but have totCount2>0
 * 		not worth adding all reps or for zero
 */

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.FileReader;

import util.ErrorReport;
import util.LogTime;
import build.Cfg;
import database.DBConn;
import database.Dynamic;

public class GeneCov {
		
	private HashMap <String, Integer> libMap = new HashMap <String, Integer> (); // name LIBid
	private HashMap <Integer, String> libMap2 = new HashMap <Integer, String> (); // libid name
	private HashMap <String, Integer> transMap = new HashMap <String, Integer> (); // transName --> id
	private HashMap <Integer, String> transName = new HashMap <Integer, String> (); // id --> transName
	private HashMap <String, String> transAlias = new HashMap <String, String> (); // transIden --> transName
	private HashSet <String> transLib = new HashSet <String> ();    // TRANSid:LIBid:RepNum
	private HashSet <String> newTransLib = new HashSet <String> (); // TRANSid:LIBid:RepNum
	private DBConn mDB;
	private Cfg  cfg;

	private Pattern transPat;
	private String geneCovDir;	
	private Vector <String> geneCovVec;
	
	public GeneCov(DBConn dbc, Cfg c) {
		mDB = dbc; cfg = c;
		if (!cfg.hasExpDir()) {
			LogTime.PrtWarn("No gene coverage (eXpress) directory defined in cfg file -- skipping step");
			return;
		}
		try {
			mDB.executeUpdate("update transLib set refCount2=0,altCount2=0,totCount2=0");
			mDB.executeUpdate("update geneLib set refCount2=0,altCount2=0,totCount2=0");
		} catch (Exception e) {ErrorReport.prtError(e, "GeneCov: clearing tables");}
		
		long time = LogTime.getTime();
		geneCovDir = cfg.getGeneCovDir();
		LogTime.PrtDateMsg("Loading transcript counts " + geneCovDir);
		geneCovVec = cfg.getGeneCovVec();
		
		transPat = Pattern.compile("(A|B|C)\\.(\\S+)"); // e.g. A.geneName or A.ENSMUST00000140873
		
		Dynamic.addLib2(dbc);
		readLibDB();
		loadTransCov();
		
		LogTime.PrtSpMsg(1, "Counts added, now updating gene information ");
		updateGenes(); 
		
		LogTime.PrtSpMsgTime(0, "Finish loading transcript counts ", time);
	}
	private void loadTransCov(){
		try {
			int nLib=0;
			long totalLoad=0, totalAdd=0;
		
		
			for (String toks : geneCovVec)
			{
				String [] x = toks.split(":");
				String file = x[0];
				String libName = x[1];
				String rep = x[2];
				
				HashMap<String,HashMap<String,Integer>> counts = new HashMap<String,HashMap<String,Integer>>();
				
				int libid = libMap.get(libName);
				PreparedStatement ps = mDB.prepareStatement("update transLib " +
						" set refCount2=?,altCount2=?,totCount2=? " +
						" where libid=" + libid + " and repnum=" + rep + " and transid=?");
				PreparedStatement ps2 = mDB.prepareStatement("update transLib  " +
						" set refCount2=refCount2 + ?, altCount2=altcount2 + ?,totCount2= totcount2 + ? " +
						" where libid=" + libid + " and repnum=0 and transid=?");
				
				int loaded = 0;
				int unkTrans = 0, addLib=0;
				
				BufferedReader r = new BufferedReader(new FileReader(geneCovDir + "/" + file));
				r.readLine();
				while (r.ready()) {  
					String[] fields = r.readLine().trim().split("\\s+");
					if (fields[0].equals("") || fields.length <= 14) continue; //probably end of file 
					Matcher m1 = transPat.matcher(fields[1]);
					if (!m1.matches()) {
						System.err.println("Can't parse name " + fields[1] + ", file " + file);
						break;
					}
					String abc = m1.group(1);
					String tName = m1.group(2);
					int count = (int)Math.round(Double.parseDouble(fields[7]));
					if (abc.equals("C")) 
					{
						count = (int)(count/2);
					}
					if (!transMap.containsKey(tName) && !transAlias.containsKey(tName))
					{
						//System.err.println("Failed to match name " + tName);
						unkTrans++;
						continue;
					}
					else
					{
						//System.err.println("Matched name " + tName);
					}
					if (transAlias.containsKey(tName))
					{
						tName = transAlias.get(tName);
					}
					int transID = transMap.get(tName);
					
					// CAS 2/27/14 - transLib only has entries with SNPs, add entries for rep0
					// not sure its worth it, but the data can be seen from the Trans Lib table
					boolean allReps=true;
					String key=transID + ":" + libid;
					
					if (!transLib.contains(key)) {
						allReps=false;
						if (!newTransLib.contains(key)) {
							mDB.executeUpdate("insert transLib " +
									"set TRANSid=" + transID + ",LIBid=" + libid + ",repNum=0," +
									"libName='" + libName + "'," +
									"transName='" + transName.get(transID)+"'");
							newTransLib.add(key);
							addLib++;
						}
					}
					if (loaded%1000==0) {
						System.out.print("   " + nLib + "." + libid + "." + file + 
								" trans=" + loaded + " skipped=" + unkTrans + " added=" + addLib+ "     \r");
					}
					
					if (abc.equals("C")) //  no ref/alt, so only one read count
					{
						if (allReps) {
							ps.setInt(4,transID);
							ps.setInt(1,0);
							ps.setInt(2,0);
							ps.setInt(3, count);
							ps.addBatch();
						}
						ps2.setInt(4,transID);
						ps2.setInt(1,0);
						ps2.setInt(2,0);
						ps2.setInt(3, count);
						ps2.addBatch();
						loaded++;
					}
					else // A is ref and B is alt
					{
						if (!counts.containsKey(tName)) {
							counts.put(tName, new HashMap<String,Integer>());
							counts.get(tName).put(abc,count);
						}
						else
						{
							counts.get(tName).put(abc,count); // easiest way to sort into A and B
							if (allReps) {
								ps.setInt(1,counts.get(tName).get("A"));
								ps.setInt(2,counts.get(tName).get("B"));
								ps.setInt(3,counts.get(tName).get("A") + counts.get(tName).get("B"));
								ps.setInt(4,transID);
								ps.addBatch();
							}
							ps2.setInt(1,counts.get(tName).get("A"));
							ps2.setInt(2,counts.get(tName).get("B"));
							ps2.setInt(3,counts.get(tName).get("A") + counts.get(tName).get("B"));
							ps2.setInt(4,transID);
							ps2.addBatch();
							loaded++;
						}
					}
					if (loaded%1000==0) { 
						ps.executeBatch();
						ps2.executeBatch();
					}
				}
				ps.executeBatch();
				ps2.executeBatch();
				totalLoad+= (long) loaded; 
				totalAdd+= (long) addLib;			
				System.out.print("   " + nLib + "." + file + ": trans=" + loaded + " skipped=" + unkTrans + 
						" added=" + addLib + "      \r");
			}
			System.out.println(); // so only last one shows; the #loaded and #skipped are the same for all
			LogTime.PrtSpMsg(2, "Total loaded " + totalLoad + "; added " + totalAdd);
		}
		catch(Exception e) {
			ErrorReport.die(e,"Loading expression results from " + geneCovDir);
		}
	}
	private void updateGenes() {
		ResultSet rs;
		try {
			int cntAdd=0, cntUpdate=0;
			
		// add geneLib repNum=0 where transLibs have been added where they didn't exist because no SNP coverage
			HashMap <Integer, Integer> geneTrans = new HashMap <Integer, Integer> ();
			rs = mDB.executeQuery("select geneid, transid from trans");
			while (rs.next()) geneTrans.put(rs.getInt(2), rs.getInt(1));
			
			HashSet <String> newGeneLib = new HashSet <String> ();
			for (String key1 : newTransLib) {
				String [] tok = key1.split(":");		
				int transid = Integer.parseInt(tok[0]);
				int libid = Integer.parseInt(tok[1]);
				
				int geneid = geneTrans.get(transid);
				String key2 = geneid + ":" + libid;
				
				if (!newGeneLib.contains(key2)) {
					String lib= libMap2.get(libid);
					rs = mDB.executeQuery("select geneName from gene where geneid=" + geneid);
					rs.next();
					String name = rs.getString(1);
					
					mDB.executeUpdate("insert ignore geneLib " +
							"set GENEid=" + geneid + ",LIBid=" + libid + ",repNum=0, refCount=0, altCount=0, pvalue=2," +
							"libName='" + lib + "'," +  "geneName='" + name+"'");
					cntAdd++;
					newGeneLib.add(key2);
				}
			}
			LogTime.PrtSpMsg(2, "Add gene lib: " + cntAdd);
			
		// now update genesLib from transLib
			Vector<Integer> genes = new Vector<Integer>();
			rs = mDB.executeQuery("select geneid from gene");
			while (rs.next()) genes.add(rs.getInt(1));
			
			int N = genes.size();
			int batched = 0;
			PreparedStatement ps = mDB.prepareStatement("update geneLib set refcount2=?,altcount2=?,totcount2=? " +
					"where geneid=? and libid=? and repnum=?");
			for (int geneid : genes)
			{
				if (N%1000 == 0) System.err.print(N + "                \r"); 
				N--;
				ps.setInt(4, geneid);
				rs = mDB.executeQuery("select geneid,libid,repnum," +
						" sum(refcount2), sum(altcount2), sum(totcount2)  from transLib " + 
						" join trans on transLib.transid=trans.transid " +
						" where geneid=" + geneid + " group by libid,repnum");
				while (rs.next())
				{
					ps.setInt(1, rs.getInt(4));
					ps.setInt(2, rs.getInt(5));
					ps.setInt(3, rs.getInt(6));
					ps.setInt(5, rs.getInt(2));
					ps.setInt(6, rs.getInt(3));
					ps.addBatch();
					batched++;
					cntUpdate++;
					if (batched == 100) 
					{
						ps.executeBatch();
						batched = 0;
					}
				}
				if (batched > 0) ps.executeBatch();
			}   
			LogTime.PrtSpMsg(2, "Update gene lib: " + cntUpdate);
		}
		catch(Exception e) {ErrorReport.die(e,"updating Express gene counts");}
	}
	
	private void readLibDB() {
		LogTime.PrtSpMsg(1, "Loading information from database");
		ResultSet rs;
		try {	
			rs = mDB.executeQuery("Select LIBid, libName from library");
			while (rs.next()) {
				libMap.put(rs.getString(2), rs.getInt(1));
				libMap2.put(rs.getInt(1), rs.getString(2));
			}	
			rs = mDB.executeQuery("Select transid, transName,transIden from trans");
			while (rs.next()) {
				transMap.put(rs.getString(2), rs.getInt(1));
				transName.put(rs.getInt(1), rs.getString(2));
				transAlias.put(rs.getString(3), rs.getString(2));
			}
			// in case this has been run before; if this isn't done, all reps will get added
			mDB.executeUpdate("delete from transLib where refCount=0 and altCount=0");
			
			rs = mDB.executeQuery("Select TRANSid, LIBid from transLib where repNum=0");
			while (rs.next()) {
				String key = rs.getInt(1) + ":" +rs.getInt(2);
				transLib.add(key);
			}	
			LogTime.PrtSpMsg(2, "Libs=" + libMap.size() + " Trans=" + transMap.size() + " TransLib=" + transLib.size());
		}
		catch (Exception e) {ErrorReport.die(e, "reading database for Gene Coverage ");}		
	}
}
