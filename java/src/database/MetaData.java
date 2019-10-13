package database;

/*************************************************
 * A MetaData object will be created on startup, which will read all metadata
 * from the database. It contains the dynamic columns and the state, i.e. if
 * there can be differences in what can be queried, e.g. in TCW the state would
 * have hasDE, isPeptide, etc.
 */
import java.sql.ResultSet;
import java.util.Vector;
import java.util.HashMap;

import database.DBConn;
import util.ErrorReport;
import util.Globals;
import util.LogTime;

public class MetaData {

	public MetaData(DBConn dbc) {
		mDB = dbc;
		version = new Version(dbc).getVersion(); // checks to see if columns need to be added
		
		try {
			setLibraries();
			setState();
			setChr();
		}
		catch(Exception e) {ErrorReport.prtError(e, "Error updating info");}
	}	
	private void setLibraries() {
		try {
			ResultSet rs = mDB.executeQuery("SELECT strains, tissues, libAbbr, libType, hasNames FROM metaData");
			rs.next();
			String [] strPairs = rs.getString(1).split(",");
			String tisStr = rs.getString(2);
			
			String lib = rs.getString(3);
			String cond = rs.getString(4);
			hasNames = (rs.getInt(5)==1) ? true : false;
			
			String [] conditions = cond.split(",");
			if (conditions.length>=1) {
				Globals.condition1 = conditions[0];
				if (conditions[0].length()>6) Globals.cond1 = conditions[0].substring(0, 6);
				else Globals.cond1 = conditions[0];
			}
			else Globals.condition1 = Globals.cond1 = "Strain";
			
			if (conditions.length==2) {
				Globals.condition2 = conditions[1];
				if (conditions[1].length()>6) Globals.cond2 = conditions[1].substring(0, 6);
				else Globals.cond2 = conditions[1];
			}
			strains = new String [strPairs.length];
			strAbbv = new String [strPairs.length];
			for (int i=0; i< strPairs.length; i++) {
				String [] tok = strPairs[i].split(":");
				strains[i] = tok[0];
				strAbbv[i] = tok[1];
			}
			if (tisStr.contains(":")) {
				String [] tisPairs = tisStr.split(",");
				tissues = new String [tisPairs.length];
				tisAbbv = new String [tisPairs.length];
				for (int i=0; i< tisPairs.length; i++) {
					String [] tok = tisPairs[i].split(":");
					if (tok.length==2) {
						tissues[i] = tok[0];
						tisAbbv[i] = tok[1];
					}
				}	
				hasCond2=true;
			}
			else {
				tissues = new String [1];
				tisAbbv = new String [1];
				tissues[0] = tisAbbv[0] = ""; // allows tissue loops to works 
				hasCond2=false;
			}
			libAbbr = lib.split(",");
			for (int i=0; i<libAbbr.length; i++) libAbbr[i] = libAbbr[i].trim(); //split does not remove blanks
			
			rs = mDB.executeQuery("SELECT LIBid, libName, remark, reps FROM library");
			while (rs.next()) {
				int reps = rs.getInt(4);
				if (reps==0) continue; // this covers up a bug that add libraries
				String libName=rs.getString(2);
				libMap.put(libName, rs.getInt(1));
				if (rs.getString(3).contains("hybrid") || rs.getString(3).contains("yes")) 
					hybLibs.add(libName);
			}
			// XXX heuristic as to whether is worth having group selection  in variant and trans tables
			if (hasCond2 && strains.length>3 && tissues.length>3) hasManyCond=true;
			else if (strains.length>4) hasManyCond=true;
			else hasManyCond=false;
		}
		catch(Exception e) {ErrorReport.prtError(e, "Error lib columns");}
	}
	// set flags as to what data is in the database
	private void setState() {
		try {
			int cnt;
			// RIGHT now, missense, coding, damaging, cDNApos, CDSpos, etc read from Ensembl Variant
			// so all or none of all of this
			cnt = mDB.executeCount("SELECT count(*) from trans where cntMissense>0");
			hasMissense = (cnt>0) ? true : false;
			
			cnt = mDB.executeCount("SELECT count(*) from trans where cntDamage>0");
			hasDamaging = (cnt>0) ? true : false;
			
			cnt = mDB.executeCount("SELECT count(*) from trans where refProLen>0");
			hasProtein = (cnt>0) ? true : false;
			
			cnt = mDB.executeCount("SELECT count(*) from transLib where totCount2>0");
			hasReadCnt = (cnt>0) ? true : false;
			
			cnt = mDB.executeCount("SELECT count(*) from trans where cntIndel>0");
			hasIndel = (cnt>0) ? true : false;
			
			cnt = mDB.executeCount("SELECT count(*) from trans where descript is not null");
			hasDesc = (cnt>0) ? true : false;
			
			cnt = mDB.executeCount("SELECT count(*) from SNPtrans where CDSpos>0");
			hasCDSpos = (cnt>0) ? true : false;
			
			cnt = mDB.executeCount("SELECT count(*) from SNPtrans where cDNApos>0");
			hasCDNApos = (cnt>0) ? true : false;
			
			cnt = mDB.executeCount("SELECT count(*) from SNPtrans where included=0");
			hasInclude = (cnt>0) ? true : false;
			
			cnt = mDB.executeCount("SELECT count(*) from SNPtrans where dist>0");
			hasVarDist = (cnt>0) ? true : false;
			
			cnt = mDB.executeCount("SELECT count(*) from SNPtrans where rscu is not null");
			hasRSCU = (cnt>0) ? true : false;
			
			cnt = mDB.executeCount("SELECT count(*) from sequences");
			hasAAseqs = (cnt>0) ? true : false;
			
			cnt = mDB.executeCount("SELECT count(*) from trans where gtfRmk is not null and gtfRmk != ''");
			hasGtfRmk = (cnt>0) ? true : false;
		}
		catch(Exception e) {ErrorReport.prtError(e, "Error setState");}
	}
	
	private void setChr() {
		try {
			ResultSet rs = mDB.executeQuery("SELECT distinct(chr) FROM gene");
			while (rs.next()) chrStr.add(rs.getString(1));
			
			rs = mDB.executeQuery("SELECT chrRoot FROM metaData");
			if (rs.next()) chrRoot = rs.getString(1);
			if (chrRoot==null || chrRoot.equals("")) chrRoot = "Seqname";
		}
		catch(Exception e) {ErrorReport.prtError(e, "Error setChr");}
	}
	public String  getVersion() { return version;}
	public Vector <String> getChr() {return chrStr;}
	public String getChrRoot() { return chrRoot;}
	
	public String [] getStrains() {return strains;}
	public String [] getStrAbbv() {return strAbbv;}
	public String [] getTissues() {return tissues;}
	public String [] getTisAbbv() {return tisAbbv;}
	public String [] getLibAbbr()  {return libAbbr; }
	public HashMap <String, Integer> getLibMap() {return libMap;}
	public String[] getHybridLibs() {return hybLibs.toArray(new String[0]);} 
	public Vector <String> getHybLibs() {return hybLibs;} 
	
	public boolean hasNames() { return hasNames;}
	public boolean hasReadCnt() { return hasReadCnt;} // no eXpress run
	public boolean hasMissense() { return hasMissense;} // 
	public boolean hasDamaging() { return hasDamaging;}
	public boolean hasIndel() { return hasIndel;}
	public boolean hasProtein() { return hasProtein;}
	public boolean hasDesc() { return hasDesc;}
	public boolean hasRSCU() { return hasRSCU;}
	public boolean hasCDSpos() { return hasCDSpos;}
	public boolean hasCDNApos() { return hasCDNApos;}
	public boolean hasVarDist() { return hasVarDist;}
	public boolean hasCond2() { return hasCond2;}
	public boolean hasManyCond() {return hasManyCond;}
	public boolean hasAAseqs() {return hasAAseqs;}
	public boolean hasgtfRmk() {return hasGtfRmk;}
	public boolean hasInclude() {return hasInclude;}
		
	private String [] strains=null;
	private String [] tissues=null;
	private String [] strAbbv=null;
	private String [] tisAbbv=null;
	private String [] libAbbr=null;
	private Vector <String> hybLibs= new Vector <String> ();
	private Vector <String> chrStr = new Vector <String> ();
	private String chrRoot="";
	private String version=null;
	private boolean hasNames, hasCond2, hasManyCond, hasAAseqs;
	private boolean hasReadCnt, hasDamaging, hasMissense, hasProtein, hasIndel, hasDesc;
	private boolean hasCDSpos, hasRSCU, hasGtfRmk, hasCDNApos, hasVarDist, hasInclude;
	HashMap <String, Integer> libMap = new HashMap <String, Integer> ();

	DBConn mDB;
	
	/******************************************************
	 * Used by ViewFrame, ConfigFrame and Overview to write to file
	 * CASZ 10Oct19
	 */
	public static String getOverview(DBConn mdb) {
		try {
			ResultSet rs = mdb.executeQuery("SELECT overview, buildDate, chgDate, remark FROM metaData");
			if (!rs.next()) {
				LogTime.PrtError("viewAW cannot access overview");
				return "cannot access overview";
			}
			String cDate = rs.getString(3);
			int x = cDate.indexOf(" ");
			if (x>0) cDate = cDate.substring(0, x);
			
			String overview = rs.getString(4) + 
					"    Build: " +  rs.getDate(2) + "  Change: " + cDate;
			overview += "\n" + rs.getString(1);
		
			return overview;
		} catch (Exception e) {
			ErrorReport.prtError(e, "Error loading overview");
		} catch (Error e) {
			ErrorReport.reportFatalError(e);
		}
		return "Error getting overview";
	}
}
