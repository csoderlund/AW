package database;

/************************************************
 * Reads HOSTS.cfg and returns connection
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import util.ErrorReport;
import util.Globals;
import util.LogTime;

public class HostCfg {
	private DBConn mDB=null;
	private String DBprefix= Globals.DBprefix;
	private String hostFile = Globals.hostFile;
	private String DBhost="localhost"; // do we want to make this more general?
	private String DBuser="", DBpass="", DBname="";
	
	public HostCfg() {}
	
	public boolean readHosts() {
		try {
			File f = new File(hostFile);
			if (!f.exists()) {
				hostFormat(hostFile + " not found");
				return false;
			}	
			BufferedReader reader = new BufferedReader (new FileReader ( hostFile ) );
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("#")) continue;
				if (line.equals("")) continue;
				
				String [] tok = line.split("=");
				if (tok.length!=2) System.out.println("Invalid line: " + line);
				else {
					String key = tok[0].toLowerCase();
					String val = tok[1].trim();
					if (key.contains("user")) DBuser=val;
					else if (key.contains("pass")) DBpass=val;
					else if (key.contains("host")) continue;
					else System.out.println("Invalid line: " + line);
				}
			}
			if (DBuser.equals("") || DBpass.equals("")) {
				hostFormat("Incorrect " + hostFile);
				return false;
			}
			return true;
		}
		catch (Exception e) {
			e.printStackTrace();
			hostFormat("Error reading HOSTS.cfg");
		}
		return false;
	}
	/**********************************************************
	 * Database open/create/exists
	 */
	public DBConn openDB(String dbSuffix, int action) {
		try {	
			if (DBuser.equals("")) {
				if (!readHosts()) return null;
			}	
			DBname = dbSuffix;
			boolean doesDBExist = existsDB(dbSuffix); // add prefix if needed
			
			if (!doesDBExist)  {
				if (LogTime.yesNo("Create database " + DBname)) 
					return createDB();
				else { 
					LogTime.PrtWarn("User terminated");
					return null;
				}
			}
			
			if (action<=1) {
				if (!LogTime.yesNo("Delete database '" + DBname + "' and recreate ")) LogTime.die("Users request");
				
				DBConn.deleteMysqlDB(DBhost, DBname, DBuser, DBpass); 
				System.out.println("Database '" + DBname + "' deleted");
				return createDB();
			}
			else {
				LogTime.PrtSpMsg(1, "Use existing database " + DBname + " on " + DBhost);
				mDB = getDBConnection();
				return mDB;
			}
		}
		catch (Exception e) {
			ErrorReport.die(e, "Cannot open " + DBname + " on " + DBhost);
		}
		return null;
	}
	private DBConn createDB() {
		try {
			DBConn.createMysqlDB(DBhost, DBname, DBuser, DBpass);
			mDB = getDBConnection();
			LogTime.PrtSpMsg(1, "Loading schema database");
			new Schema(mDB);
	
			return mDB;
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Cannot load schema");
			return null;
		}
	}
	
	public DBConn openDB(String dbName) {		
		try {	
			if (DBuser.equals("")) {
				if (!readHosts()) return null;
			}	
			if (!existsDB(dbName))  {
				LogTime.PrtError("Database does not exist: " + dbName + " on " + DBhost);
				return null;
			}
			return getDBConnection();
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Cannot open " + DBname + " on " + DBhost);
		}
		return null;
	}

	public DBConn getDBConnection() throws Exception
	{
		String dbstr = "jdbc:mysql://" + DBhost + "/" 	+ DBname;
		mDB = new DBConn(dbstr, DBuser, DBpass);
		return mDB;
	}
	public DBConn renew() 
	{
		try {
			if (mDB==null) return getDBConnection();
			else return mDB;
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Cannot renew connection for " + DBname);
		}
		return null;
	}
	public boolean existsDB(String db)
	{
		try {
			if (DBuser.equals("")) {
				if (!readHosts()) return false;
			}
			db = db.trim();
			if (db.startsWith(DBprefix)) DBname = db;
			else DBname= DBprefix + db;
			
			return DBConn.checkMysqlDB(DBhost, DBname, DBuser, DBpass);
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Checking database: " + db);
			return false;
		}
	}
	public boolean deleteDB(String db) {
		if (DBuser.equals("")) {
			if (!readHosts()) return false;
		}
		try {
			DBConn.deleteMysqlDB(DBhost,db, DBuser, DBpass);
			return true;
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Could not delete database: " + db);
			return false;
		}
	}
	
	public String getDBhost() {return DBhost;}
	public String getDBuser() { return DBuser;}
	public String getDBname() { return DBname;}
	public String getDBpass() { return DBpass;}

	private void hostFormat(String msg) {
		System.out.println(msg);
		System.out.println(hostFile + " must contain:");
		System.out.println("user = <mysql write access userid>");
		System.out.println("password = <mysql write assess password");
		System.out.println("  with the userid and password for your mySQL database");
	}
}
