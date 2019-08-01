package viewer.controls;

import java.sql.ResultSet;

import util.ErrorReport;
import viewer.ViewerFrame;
import database.DBConn;

/**************************************************
 * Random functions used by multiple routines
 */
public class Static {
	static public String [] str2arr(String theList) {
		if(theList.contains(",")) {
			String [] retVal = theList.split(",");
			for(int x=0; x<retVal.length; x++)
				retVal[x] = retVal[x].trim();
			return retVal;
		}
		String [] retVal = new String[1];
		retVal[0] = theList;
		return retVal;
	}
	static public String [] str2IntArr(String theList) {
		if(theList.contains(",")) {
			String [] retVal = theList.split(",");
			for(int x=0; x<retVal.length; x++) {
				retVal[x] = retVal[x].trim();
				try {
					Integer.parseInt(retVal[x]);
				}
				catch (Exception e){
					return null;
				}
			}
			return retVal;
		}
		String [] retVal = new String[1];
		retVal[0] = theList;
		return retVal;
	}
	
	// determines if exact or substring
	static public String getSubStr(String val, String table, String column) {
		try {
			String searchStr = val.replace('*', '%');		
			String [] mods = new String[3];
			
			mods[0] = searchStr;
			mods[1] = searchStr + "%";
			mods[2] = "%" + searchStr + "%";
			
			String retVal = "";		
			DBConn conn = ViewerFrame.getDBConnection();
			ResultSet rset = null;
			
			for(int x=0; x<mods.length && retVal.length() == 0; x++) {
				rset = ViewerFrame.executeQuery(conn, "SELECT count(*) FROM " + table +
						" WHERE " + column + " LIKE '" + mods[x] + "'", null);
				rset.next();
				if(rset.getInt(1) > 0) retVal = mods[x];
				ViewerFrame.closeResultSet(rset);
			}			
			conn.close();
			
			if(retVal.length() > 0) return retVal;
			else return mods[2];
		}
		catch(Exception e) {
			ErrorReport.prtError(e, "Failed on SQL query for column " + column);
		}
		catch(Error e) {
			ErrorReport.reportFatalError(e);
		}
		return null;
	}
}
