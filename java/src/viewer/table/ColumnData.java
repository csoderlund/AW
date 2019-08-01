package viewer.table;

/**************************************
 * NOTE: column type set in the different Column classes is overwritten 
 * by the actual type in TableData during display!!
 * 
 * Called by all Table classes to build table and columns for query
 * TODO: (1) provide computed columns, (2) only use symbol for DB computed columns
 */
import java.util.Vector;
import java.util.Iterator;

public class ColumnData {
	
	public ColumnData() {
		theColumns = new Vector<ColumnItem> ();
		//theJoins = new Vector<JoinItem> ();  obsolete
	}

	/*********************************************
	 * the following 3 methods are called by buildTable in all Table.java classes
	 */
	public String [] getDisplayColumns() {
		if(theColumns.size() == 0) return null;

		Vector <String> list= new Vector<String> ();
		for (ColumnItem item : theColumns) {	
			list.addElement(item.getColumnName());
		}
		return list.toArray(new String[list.size()]);
	}

	public String [] getDisplayColumnsSymbols() {
		if(theColumns.size() == 0) return null;

		Vector <String> list= new Vector<String> ();
		for (ColumnItem item : theColumns) {	
			list.addElement(item.getDBSymbolName());
		}
		return list.toArray(new String[list.size()]);
	}

	public Class<?> [] getDisplayTypes() {
		if(theColumns.size() == 0) return null;

		Vector <Class <?>> list= new Vector<Class<?>> ();
		for (ColumnItem item : theColumns) {	
			Class<?> type = item.getColumnType();
			list.addElement(type);
		}
		return list.toArray(new Class<?> [list.size()]);
	}
	
	/*********************************************************
	 * Called by all Table.java classes to build the SQL string
	 */
	public String getDBColumnQueryList() {
		String list=null;
		for (ColumnItem item : theColumns) {	
			if (list==null) list = getColumnClause(item);
			else list += ", " + getColumnClause(item);
		}
		return list;
	}
	
	private String getColumnClause(ColumnItem item) {
		String retVal;
		String col = item.getDBColumnName();
		String tab = item.getDBTable();
		String sym = item.getDBSymbolName();
		
		if(col == null) retVal = "NULL";
		else if (tab==null) retVal = col;
		else retVal = tab + "." + col;
		
		if(sym.length() > 0) retVal += " AS " + sym;
		
		return retVal;
	}	
	/******************
	 * Called by all Column.java classes to add columns
	 * @param fieldName column in displayed table
	 * @param type		integer, string
	 * @param dbTable	SQL table
	 * @param dbField	SQL column
	 * @param dbSymbol	will be queried as "Column as Symbol", so can have table.X-table.Y as DIFF
	 */
	public void addColumn(	String fieldName, Class<?> type, String dbTable, String dbField, String dbSymbol) {
		ColumnItem fd = new ColumnItem(fieldName, type);
		fd.setQuery(dbTable, dbField, dbSymbol);
		theColumns.add(fd);
	}
	
	private Vector<ColumnItem> theColumns = null;

	/**********************************************************************
	 * Class Field Item
	 */
	public class ColumnItem {
		public ColumnItem(String fieldName, Class<?> type) {
			strTableName = fieldName;
			colDataType = type;
 		}

		public void setQuery(String table, String field, String symbol) {
			strDBTable = table;
			strDBColumn = field;
			strDBSymbol = symbol;
		}

		public String getColumnName() { return strTableName; }
		public void setColumnName(String name) { strTableName = name; }

		public Class<?> getColumnType() { return colDataType; }
		public void setColumnType(Class<?> newType) { colDataType = newType; }

		public String getDBTable() { return strDBTable; }
		public void setDBTable(String dbTableName) { strDBTable = dbTableName; }

		public String getDBColumnName() { return strDBColumn; }
		public void setDBColumnName(String dbColName) { strDBColumn = dbColName; }
		
		public String getDBSymbolName() { return strDBSymbol; }
		public void setDBSymbolName(String dbSymbol) { strDBSymbol = dbSymbol; }

		private String strTableName;
		private Class<?> colDataType = null;
		//True=value read directly from DB, False=value calculated
		//Source table for the value
		private String strDBTable;
		//Source field in the named DB table
		private String strDBColumn;
		//Symbolic name for query value; this is necessary for SQL ops such as X-Y
		private String strDBSymbol = "";
	}

  	public int getNumDisplayColumns() {
		return theColumns.size();
	}
  	public int getNumColumns() { return theColumns.size(); }
	//****************************************************************************
	//* Methods by position
	//****************************************************************************
	public String getColumnNameAt(int pos) { return theColumns.get(pos).getColumnName(); }

	public int getColumnPosition(String field) {
		int position = -1;
		for(int x=0; x<theColumns.size() && position < 0; x++) {
			if(theColumns.get(x).getColumnName().equals(field)) {
				position = x;
			}
		}
		return position;
	}

	public int getColumnSymbolPosition(String symbol) {
		int position = -1;
		for(int x=0; x<theColumns.size() && position < 0; x++) {
			if(theColumns.get(x).getDBSymbolName().equals(symbol)) {
				position = x;
			}
		}
		return position;
	}
	public String [] getDBColumns() {
		if(theColumns.size() == 0) return null;

		String [] retVal = new String[getNumColumns()];
		Iterator<ColumnItem> iter = theColumns.iterator();
        int x = 0;
        ColumnItem item = null;

        while(iter.hasNext()) {
        		item = iter.next();
        		retVal[x++] = item.getColumnName();
        }
        return retVal;
	}
	// not used
	public Class<?> [] getDBColumnTypes() {
		if(theColumns.size() == 0) return null;

		Class<?> [] retVal = new Class<?>[getNumColumns()];
		Iterator<ColumnItem> iter = theColumns.iterator();
		int x = 0;
		ColumnItem item = null;

		while(iter.hasNext()) {
			item = iter.next();
			retVal[x++] = item.getColumnType();
		}
		return retVal;
	}
	/** Joins have become obsolete as they are just put in the Table code
	public void addJoin(String table, String condition, String strSymbol) { 
		theJoins.add(new JoinItem(table, condition, strSymbol)); 
	}
	public void setJoins(Vector<JoinItem> joins) {
		if(joins != null) {
			theJoins.clear();
			Iterator<JoinItem> iter = joins.iterator();
			while(iter.hasNext())
				theJoins.add(iter.next());
		}
	}
	public boolean hasJoins() { return !theJoins.isEmpty(); }
	public String getJoins() { 
		Iterator<JoinItem> iter = theJoins.iterator();
		String retVal = "";
		while(iter.hasNext()) {
			if(retVal.length() == 0)
				retVal = iter.next().getJoin();
			else
				retVal += " " + iter.next().getJoin();
		}
		return retVal;
	}
	private Vector<JoinItem> theJoins = null;
	
	public class JoinItem {
		public JoinItem(String table, String condition, String symbol) {
			strTable = table;
			strCondition = condition;
			strSymbol = symbol;
		}
		public String getJoin() {
			String retVal = "JOIN " + strTable;
			if(strSymbol.length() > 0)
				retVal += " AS " + strSymbol;
			retVal += " ON " + strCondition;
			return  retVal;
		}
		private String strTable = "";
		private String strCondition = "";
		private String strSymbol = "";
	}
	**/
}
