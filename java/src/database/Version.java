package database;

/************************************************
 * Not used yet, but when we start having releases, we will need to
 * to update old databases
 */
import java.sql.ResultSet;

import database.DBConn;
import util.ErrorReport;
import util.Globals;

public class Version {
	
	public Version (DBConn mDB) { 
		checkVersion(mDB);
		if (!version.equals(Globals.VERSION)) {
			System.out.println("Created with v" + version);
			updateDB();
		}
	}
	public String getVersion () {return version;}
	
	// all new columns and tables are checked here and added if not exists
	// new columns and tables are also put into Schema.java (no database new versions yet)
	private void updateDB() {
		//System.out.println("No DB update");
	}
	private void checkVersion(DBConn mDB) {
		try {
			ResultSet rs = mDB.executeQuery("SELECT version from metaData");
			if (rs.next()) version = rs.getString(1);
			rs.close();
		}
		catch (Exception e){ErrorReport.die(e, "Cannot get version");}
	}
	String version=null;
}
