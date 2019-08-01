package database;

/*****************************************
 * Creates all dynamic columns
 */
import java.util.Vector;
import java.sql.ResultSet;

import util.ErrorReport;
import util.Globals;

public class Dynamic {

	// called from VarCov
	static public void addLib(DBConn mDB) {
		try {
			Vector <String> libs = new Vector <String> ();
			ResultSet rs = mDB.executeQuery("Select libName from library");
			while (rs.next()) libs.add(rs.getString(1));
			
			boolean first=true;
			for (String libName : libs) {
				String col1 = Globals.PRE_REFCNT + libName;
				String col2 = Globals.PRE_ALTCNT + libName;
				String col3 = libName;
				
				if (first && mDB.hasTableColumn("gene", col1)) return;
				first = false;
				
				mDB.executeUpdate("alter table gene 	add " + col1 + " int default 0");
				mDB.executeUpdate("alter table gene 	add " + col2 + " int default 0");
				mDB.executeUpdate("alter table gene  add " + col3 + " float default 2.0");
				mDB.executeUpdate("alter table trans add " + col1 + " int default 0");
				mDB.executeUpdate("alter table trans add " + col2 + " int default 0");
				mDB.executeUpdate("alter table trans add " + col3 + " float default 2.0");
				mDB.executeUpdate("alter table SNP 	add " + col1 + " int default 0");
				mDB.executeUpdate("alter table SNP 	add " + col2 + " int default 0");	
				mDB.executeUpdate("alter table SNP 	add " + col3 + " float default 2.0");
			}
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Cannot add libraries");
		}
	}
	// called from GeneCov (only if eXpress is run)
	static public void addLib2(DBConn mDB) {
		try {
			Vector <String> libs = new Vector <String> ();
			ResultSet rs = mDB.executeQuery("Select libName from library");
			while (rs.next()) libs.add(rs.getString(1));
			
			boolean first=true;
			for (String libName : libs) {
				String col1 = Globals.PRE_REFCNT + libName + Globals.SUF_TOTCNT;
				String col2 = Globals.PRE_ALTCNT + libName + Globals.SUF_TOTCNT;
				String col3 = libName + Globals.SUF_TOTCNT;;
				
				if (first && mDB.hasTableColumn("gene", col1)) return;
				first = false;
				
				mDB.executeUpdate("alter table gene 	add " + col1 + " int default 0");
				mDB.executeUpdate("alter table gene 	add " + col2 + " int default 0");
				mDB.executeUpdate("alter table gene  add " + col3 + " float default 2.0");
				mDB.executeUpdate("alter table trans add " + col1 + " int default 0");
				mDB.executeUpdate("alter table trans add " + col2 + " int default 0");
				mDB.executeUpdate("alter table trans add " + col3 + " float default 2.0");
			}
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Cannot add libraries");
		}
	}
}
