package database;
/*******************************************
 * All database transactions are made through these calls
 */

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import util.ErrorReport;

// WARNING: Does not work for nested queries (because it uses one Statement for all queries). 

public class DBConn 
{
	String mDBStr;
	String mUser;
	String mPass;
	Connection mConn = null;
	Statement mStmt = null;
	boolean autoCommit = false;

	public DBConn(String dbstr, String user, String pass) throws Exception
	{
		mDBStr = dbstr;
		mUser = user;
		mPass = pass;
		renew();
	}
	
	// make a database call using a utilities function
	public Connection getDBconn() { return mConn;}
	
	public void renew() throws Exception
	{
		Class.forName("com.mysql.jdbc.Driver");
		mStmt = null;
		if (mConn != null && !mConn.isClosed()) mConn.close();
		for (int i = 0; i < 100; i++)
		{
			Thread.sleep(1000);
			try
			{
				mConn = DriverManager.getConnection(mDBStr, mUser,mPass);
				mConn.setAutoCommit(autoCommit);
				break;
			} 
			catch (SQLException e)
			{
				if (i == 99)
				{
					ErrorReport.die(e, "Unable to connect to " 	+ mDBStr
								+ "\nError message: "
								+ e.getMessage());
				}
			}
		}
	}
	public static void checkMysqlServer(String host, String user, String pass) 
	{
		try
		{
			Class.forName("com.mysql.jdbc.Driver");	
		}
		catch(Exception e)
		{
			System.err.println("Unable to find MySQL driver");
			ErrorReport.die(e, "Unable to find MySQL driver");
		}
		String dbstr = "jdbc:mysql://" + host;
		try
		{
			Connection con = DriverManager.getConnection(dbstr, user, pass);
			con.close();
		} 
		catch (Exception e)
		{
			ErrorReport.die("Cannot connect to database with host=" + host + ",user=" + user + ",pass=" + pass);
		}
		//System.out.println("Connected to database server " + host);
	}
	public static boolean checkMysqlDB(String host, String db, String user, String pass) 
	{
		String dbstr = "jdbc:mysql://" + host + "/" + db;
		Connection con = null; 
		checkMysqlServer(host, user, pass);

		try
		{
			Class.forName("com.mysql.jdbc.Driver");
			con = DriverManager.getConnection(dbstr, user, pass);
			con.close();
			return true;
		} 
		catch (Exception e){   /* calling method prints error */}
		return false;
	}
	
	public static void createMysqlDB(String host, String db, String user, String pass) throws Exception
	{
		String dbstr = "jdbc:mysql://" + host;
		Connection con = null; 

		try
		{
			Class.forName("com.mysql.jdbc.Driver");
			con = DriverManager.getConnection(dbstr, user, pass);
			Statement st = con.createStatement();
			st.execute("create database " + db);
			con.close();
		} 
		catch (Exception e)
		{
			ErrorReport.die(e, "Cannot create MySQL");
		}
	}
	
	public static void deleteMysqlDB(String host, String db, String user, String pass) throws Exception
	{
		Class.forName("com.mysql.jdbc.Driver");
		String dbstr = "jdbc:mysql://" + host;
		Connection con = null; 

		try
		{
			con = DriverManager.getConnection(dbstr, user, pass);
			Statement st = con.createStatement();
			st.execute("drop database " + db);
			con.close();
		} 
		catch (Exception e)
		{
			ErrorReport.prtError(e, "Cannot delete mySql");
		}
	}
	
	public boolean hasTables() throws Exception
	{
		ResultSet rs = executeQuery("show tables");
		boolean retVal = rs.first();
		rs.close();
		return retVal;
	}
	public boolean hasTable(String name) throws Exception
	{
		ResultSet rs = executeQuery("show tables like '" + name + "'");
		boolean retVal = rs.first();
		rs.close();
		return retVal;
	}
	public boolean hasTableColumn(String table, String column) throws Exception
	{
		ResultSet rs = executeQuery("show columns from " + table + " where field='" + column + "'");
		boolean ret = rs.first();
		rs.close();
		return ret;
	}
	public Statement getStatement() throws Exception
	{
		if (mStmt == null)
		{
			mStmt = mConn.createStatement();
			mStmt.setQueryTimeout(10000);
		}
		return mStmt;
	}
	public ResultSet executeQuery(String sql) throws Exception
	{
		if (mConn == null || mConn.isClosed()) renew();
		Statement stmt = getStatement();
		ResultSet rs = null;

		try
		{
			rs = stmt.executeQuery(sql);
		}
		catch (Exception e)
		{
			mStmt.close();
			mStmt = null;
			renew();
			stmt = getStatement();
			rs = stmt.executeQuery(sql);
			if (rs==null) ErrorReport.die("Failed on retry");
		}
		return rs;
	}

	public int executeUpdate(String sql) throws Exception
	{
		if (mConn == null || mConn.isClosed()) renew();
		Statement stmt = getStatement();
		int ret = 0;
		try
		{
			ret = stmt.executeUpdate(sql);
		}
		catch (Exception e)
		{
			System.err.println("Warning: Update failed, retrying:" + sql);
			mStmt.close();
			mStmt = null;
			renew();
			stmt = getStatement();
			ret = stmt.executeUpdate(sql);
		}
		return ret;
	}
	public int executeCount(String sql) throws Exception
	{
		try {
			ResultSet rs = executeQuery(sql);
 			rs.next();
 			int n = rs.getInt(1);
			rs.close();
			return n;
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Executing count");
			return 0;
		}
	}
	public Statement createStatement() throws Exception
	{
		return mConn.createStatement();
	}

	// NOT THREAD SAFE unless each thread is using its own DB connection.
	// Each thread should call Assem.getDBConnection() to get one. 
	
	public Integer lastID() throws Exception
	{
		String st = "select last_insert_id() as id";
		ResultSet rs = executeQuery(st);
		int i = 0;
		if (rs.next())
		{
			i = rs.getInt("id");
		}
		rs.close();
		return i;
	}

	public void deleteTable(String table) {	   
	   	try {
	       executeUpdate ("DELETE FROM " + table);
	       // sets the indexing back to one
	       ResultSet rset = executeQuery("SELECT COUNT(*) FROM " + table);
	       rset.next();
	       int cnt = rset.getInt(1);
	       rset.close();
	       
	       executeUpdate("ALTER TABLE " + table + " AUTO_INCREMENT = " + cnt+1);
	    }
	    catch(Exception e) {
		    	 ErrorReport.prtError(e,"Fatal error deleting table " + table);
		    	 System.exit(-1);
	    }
   }

	public  PreparedStatement prepareStatement(String st) throws SQLException
	{
		return mConn.prepareStatement(st);
	}
	public void close() throws Exception
	{
		mConn.close();
		mConn = null;
	}
	
	public void setAutoCommit(boolean b) {
		try {
			autoCommit = b;
			mConn.setAutoCommit(autoCommit);
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Could not set AutoCommit on database");
		}
	}
	public void commitChanges ( ) throws Exception
	{
		mConn.commit();
	}
}

