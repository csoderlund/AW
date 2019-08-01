package viewer.table;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Vector;
import java.math.BigDecimal;
import java.math.BigInteger;

import java.sql.ResultSet;

import javax.swing.JTable;
import javax.swing.JTextField;

import util.ErrorReport;
import util.Globals;
import viewer.panels.LibListTable;
import viewer.panels.GeneTable;
import viewer.panels.TransTable;
import viewer.panels.LibraryTable;
import viewer.panels.SNPTable;
import viewer.panels.SNPRepTable;
import viewer.panels.ExonTable;

public class TableData implements Serializable {
    private static final long serialVersionUID = 8279185942173639084L;
    	private static final long DISPLAY_INTERVAL = 1000; 
    	private static final String BLANK=SortTable.BLANK;
   
    	/*********************************
    * Shared routines
    */	
    	public void sortMasterList(String columnName) {
		if (theGeneTable != null) theGeneTable.sortMasterColumn(columnName);
		else if (theLibraryTable!=null) theLibraryTable.sortMasterColumn(columnName);
		else if (theLibListTable!=null) theLibListTable.sortMasterColumn(columnName);
		else if (theSNPTable!=null) theSNPTable.sortMasterColumn(columnName);
		else if (theTransTable!=null) theTransTable.sortMasterColumn(columnName);
		else if (theExonTable!=null) theExonTable.sortMasterColumn(columnName);
		else if (theSNPRepTable!=null) theSNPRepTable.sortMasterColumn(columnName);
    }    	     	
  	/*****************************************************************
  	 * XXX called from every table to show the row based on the SQL query
  	 */
    public void addRowsWithProgress(ResultSet rset, String [] symbols, JTextField progress) {
	    	try {
	    		boolean firstRow = true;
	    		boolean [] isSet = new boolean [symbols.length];
	    		for (int i=0; i<isSet.length; i++) isSet[i] = false;
	    		boolean cancelled = false;
	    		while(rset.next() && !cancelled) {
	    			
	        		Vector<Object> rowData = new Vector<Object> ();
	    			rowData.setSize(symbols.length);
	    			if(firstRow)
	    				vHeaders.get(0).setColumnClass(Integer.class);
	    			
	    			for(int x=0; x<symbols.length; x++) {
					Object dataVal = rset.getObject(symbols[x]);
					String head = vHeaders.get(x).getColumnName();
							
					if (head.contains("/")) { // Score, compute ref/(ref+alt)
						String v = (String) dataVal;
						if (v!=null && v.contains(":")) {
							String [] t = v.split(":");
							double ref = Double.parseDouble(t[0]);
							double alt = Double.parseDouble(t[1]);
							
							if (!head.contains("RSCU") && ref+alt <Globals.MIN_READS) 
								dataVal=Globals.NO_PVALUE;
							else {
								double val = ref/(ref+alt);
								if (val>1.0) val = 1.0;
								dataVal = val;
								
							}
						}	
						else dataVal=Globals.NO_PVALUE;	
					}
					else if (head.startsWith(Globals.PRE_Sratio) || head.startsWith(Globals.PRE_Tratio)) {
						String v = (String) dataVal;
						if (v!=null && v.contains(":")) {
							String [] t = v.split(":");
							double ref = Double.parseDouble(t[0]);
							double alt = Double.parseDouble(t[1]);
							if (ref>alt) dataVal = ">" + t[0] + ":" + t[1];
							else if (ref<alt) dataVal = "<" + t[0] + ":" + t[1];
							else if (ref==0 && alt==0) dataVal = t[0] + ":" + t[1];
							else dataVal = "=" + t[0] + ":" + t[1];
						}
					}
		  			rowData.set(x, dataVal);
					if(!isSet[x] && dataVal != null) {
						// XXX this overwrites what the user sets 
						vHeaders.get(x).setColumnClass(dataVal.getClass());
						isSet[x] = true;
					}
	    			}      			
	    			firstRow = false;
	    				
	    			if(progress != null && (vData.size() % DISPLAY_INTERVAL == 0)) {
	    				if(progress.getText().equals("Cancelled"))
	    					cancelled = true;
	    				else {
	    					progress.setText("Reading " + vData.size() + " rows");
	    				}
	    			}
	        		if (rowData.size() > 0) vData.add(rowData);
	    		}
	    		progress.setText("");
	    	} catch (Exception e) {
	    		ErrorReport.prtError(e, "Error building table");
	    	} catch(Error e) {
	    		ErrorReport.reportFatalError(e);
	    	}
	}
    /*********************************************************
     * XXX determines how tables are sorted 
     */
    private class ColumnComparator implements Comparator<Object []> {
    		public ColumnComparator(int column) {
	    		nColumn = column;
	    	}	
		public int compare(Object [] o1, Object [] o2) {
			int retval = 0;
			
			// XXX Null columns do not work, i.e. they are ignored -- but crashes if not checked -- weird
			if (o1[nColumn] == null || o2[nColumn] == null) {
				if (o1[nColumn] == null && o2[nColumn] == null) return 0;
				if (o1[nColumn] == null) return 1;
				if (o2[nColumn] == null) return -1;
			}
			/**these two lines make blanks always sort to bottom **/
			if (o1[nColumn].equals(BLANK) && o2[nColumn].equals(BLANK)) {
				return 0;
			}
			if (o1[nColumn].equals("") && o2[nColumn].equals("")) {
				return 0;
			}
			if(o1[nColumn].equals(BLANK) || o1[nColumn].equals("")) {
				return 1;
			}
			if(o2[nColumn].equals(BLANK) || o2[nColumn].equals("")) {
				return -1;
			}
			if(arrHeaders[nColumn].getColumnClass() == String.class) {
				retval = ((String)o1[nColumn]).compareTo((String)o2[nColumn]);
			}
			else if(arrHeaders[nColumn].getColumnClass() == Integer.class) {
				retval = ((Integer)o1[nColumn]).compareTo((Integer)o2[nColumn]);
			}
			else if(arrHeaders[nColumn].getColumnClass() == Long.class) {
				if(o1[nColumn] instanceof String)
					retval = (new Long((String)o1[nColumn])).compareTo(new Long((String)o2[nColumn]));
				else
					retval = ((Long)o1[nColumn]).compareTo((Long)o2[nColumn]);
			}
			else if(arrHeaders[nColumn].getColumnClass() == Float.class) // WN use absolute values for floats and doubles
			{
				Float val1 = Math.abs((Float)o1[nColumn]);
				Float val2 = Math.abs((Float)o2[nColumn]);
				retval = val1.compareTo(val2); 
			}
			else if(arrHeaders[nColumn].getColumnClass() == Double.class) 
			{
				Double val1 = Math.abs((Double)o1[nColumn]);
				Double val2 = Math.abs((Double)o2[nColumn]);
				retval = val1.compareTo(val2); 
			}
			else if(arrHeaders[nColumn].getColumnClass() == BigDecimal.class) {
				retval = ((BigDecimal)o1[nColumn]).compareTo((BigDecimal)o2[nColumn]);
			}
			else if(arrHeaders[nColumn].getColumnClass() == BigInteger.class) {
				retval = ((BigInteger)o1[nColumn]).compareTo((BigInteger)o2[nColumn]);
			}
			else if(arrHeaders[nColumn].getColumnClass() == Boolean.class) {
				retval = ((Boolean)o1[nColumn]).compareTo((Boolean)o2[nColumn]);
			}
			else {
				System.out.println(nColumn + " error column " + arrHeaders[nColumn].getColumnClass());
			}
			
			if (arrHeaders[nColumn].isAscending()) return retval;
			else return retval * -1;
		}	
		private int nColumn;
	}
    
    public void finalize() {
        arrHeaders = new TableDataHeader[vHeaders.size()];
        vHeaders.copyInto(arrHeaders);
        vHeaders.clear();

        arrData = new Object[vData.size()][];
        Iterator<Vector<Object>> iter = vData.iterator();
        int x = 0;
        Vector<Object> tempV;
        while(iter.hasNext()) {
                arrData[x] = new Object[arrHeaders.length];
                tempV = iter.next();
                tempV.copyInto(arrData[x]);
                tempV.clear();
                x++;
        }
        vData.clear();

        bReadOnly = true;
    }
    	public static String [] orderColumns(JTable sourceTable, String [] selectedColumns) {
    		String [] retVal = new String[selectedColumns.length];
    		Vector<String> columns = new Vector<String> ();
    		
    		for(int x=0; x<selectedColumns.length; x++) // CAS 1/29/14 - was adding backwards
    			columns.add(selectedColumns[x]);
    		
    		int targetIndex = 0;
    		for(int x=0; x<sourceTable.getColumnCount(); x++) {
    			String columnName = sourceTable.getColumnName(x);
    			
    			int columnIdx = columns.indexOf(columnName);
    			if(columnIdx >= 0) {
    				retVal[targetIndex] = columnName;
    				targetIndex++;
    				columns.remove(columnIdx);
    			}
    		}  		
    		while(columns.size() > 0) {
    			retVal[targetIndex] = columns.get(0);
    			columns.remove(0);
    			targetIndex++;
    		}   		
    		return retVal;
    	}

    public void setColumnHeaders(String [] columnNames, Class<?> [] columnTypes) {
	    	vHeaders.clear();
	    	for(int x=0; x<columnNames.length; x++) {
	    		addColumnHeader(columnNames[x], columnTypes[x]);
	    	}
    }
    
    public void addColumnHeader(String columnName, Class<?> type) {
        	try {
        		if(bReadOnly) throw (new Exception());
        		vHeaders.add(new TableDataHeader(columnName, type));
        	} 
        	catch(Exception e) {
        		ErrorReport.prtError(e, "Error adding column");
        	}
        	catch(Error e) {
        		ErrorReport.reportFatalError(e);
        	}
    }
    
    public void insertColumnHeader(int pos, String columnName, Class<?> type) {
        	try {
        		if(bReadOnly) throw (new Exception());
        		vHeaders.insertElementAt(new TableDataHeader(columnName, type), pos);
        	} 
        	catch(Exception e) {
        		ErrorReport.prtError(e, "Error inserting column");
        	}
        	catch(Error e) {
        		ErrorReport.reportFatalError(e);
        	}
    }
    
    public int getColumnHeaderIndex(String columnName) {
        	int retVal = -1, x=0;
        	
        	if(bReadOnly) {
        		for(;x<arrHeaders.length && retVal<0;x++) {
        			if(arrHeaders[x].getColumnName().equals(columnName))
        				retVal = x;
        		}
        	}
        	else {
        		Iterator<TableDataHeader> iter = vHeaders.iterator();
        		for(;iter.hasNext() && retVal<0; x++) 
        			if(iter.next().getColumnName().equals(columnName))
        				retVal = x;
        	}        	
        	return retVal;
    }
    
    public Object getValueAt(int row, int column) {
        	try {
        		if(!bReadOnly) throw (new Exception());
        		return arrData[row][column];
        	}
        	catch(Exception e) {
        		ErrorReport.prtError(e, "Error getting table value");
        		return null;
        	}
        	catch(Error e) {
        		ErrorReport.reportFatalError(e);
        		return null;
        	}
    }
    
    public void setValueAt(Object obj, int row, int column) {
        	try {
        		if(!bReadOnly) throw (new Exception());
        		arrData[row][column] = obj;
        	}
        	catch(Exception e) {
        		ErrorReport.prtError(e, "Error setting table value");
        	}
        	catch(Error e) {
        		ErrorReport.reportFatalError(e);
        	}
    }

    public Object [] getRowAt(int row) {
        try {
            if(!bReadOnly) throw (new Exception());
            return arrData[row];
        }
        catch(Exception e) {
            ErrorReport.prtError(e, "Error getting table row");
            return null;
        }
        catch(Error e) {
        		ErrorReport.reportFatalError(e);
        		return null;
        }
    }

    public String getColumnName(int column) {
        try {
            if(!bReadOnly) throw (new Exception());
            return arrHeaders[column].getColumnName();
        } catch(Exception e) {
            ErrorReport.prtError(e, "Error getting table column name");
            return null;
        } catch(Error e) {
        		ErrorReport.reportFatalError(e);
        		return null;
        }
    }

    public Class<?> getColumnType(int column) {
        	try {
        		if(!bReadOnly) throw (new Exception());
        		return arrHeaders[column].getColumnClass();
        	}
        	catch(Exception e) {
        		ErrorReport.prtError(e, "Error getting table column type");
        		return null;
        	}
        	catch(Error e) {
        		ErrorReport.reportFatalError(e);
        		return null;
        	}
    }
    
    public boolean isAscending(int column) {
        	try {
        		if(!bReadOnly) throw (new Exception());
        		return arrHeaders[column].isAscending();
        	}
        	catch(Exception e) {
        		ErrorReport.prtError(e, "Error table is not finalized");
        		return false;
        	}
        	catch(Error e) {
        		ErrorReport.reportFatalError(e);
        		return false;
        	}
    }

    public int getNumColumns() {
    		if(bReadOnly)
    			return arrHeaders.length;
    		return vHeaders.size();
    }

    public int getNumRows() {
        if(bReadOnly)
            return arrData.length;
        return vData.size();
    }

    public void sortByColumn(int column, boolean ascending) {
        	arrHeaders[column].setAscending(ascending);
        	sortByColumn(column);
    }
    // XXX gets this error, but still works. 
    public void sortByColumn(int column) {
    		try {
    			Arrays.sort(arrData, new ColumnComparator(column));
    			arrHeaders[column].flipAscending();
    		}
    		catch (Exception e) {
    			ErrorReport.prtError(e, "error sorting column#" + column);
    		}
    }

    public void clear() {
        	arrHeaders = null;
        	if(arrData != null) {
        		for(int x=0; x<arrData.length; x++) 
        			arrData[x] = null;
        		arrData = null;
        	}        	
        	vHeaders.clear();
        	for(int x=0; x<vData.size(); x++)
        		vData.get(x).clear();
        	vData.clear();
    }
    public void printHeaders() { // for debugging
    		for (int i=0; i< arrHeaders.length; i++)
    			System.out.println(i + ". " + arrHeaders[i].getColumnName());
    }
    

    /*************************************************
	 * XXX TABLE specific 
	 */
 	/******************************
	 * LibGeneTable
	 */
	public TableData(LibListTable parent) {
		vData = new Vector<Vector<Object>>();
		vHeaders = new Vector<TableDataHeader>();	      	
		theLibListTable = parent;
	}
	public static TableData createModel(String [] columns, TableData table, LibListTable parent) {
		TableData retVal = new TableData(parent);
		
		retVal.arrData = new Object[table.arrData.length][columns.length];
		retVal.arrHeaders = new TableDataHeader[columns.length];
		
		for(int x=0; x<columns.length; x++) {
			int sourceColumnIdx = table.getColumnHeaderIndex(columns[x]);
			retVal.arrHeaders[x] = table.arrHeaders[sourceColumnIdx];
			for(int y=0; y<table.arrData.length; y++) {
				retVal.arrData[y][x] = table.arrData[y][sourceColumnIdx];
			}
		}
		retVal.bReadOnly = table.bReadOnly;	
		return retVal;
	}
	
	/*****************************
	 *  TransTable
	 */
	public TableData(TransTable parent) {
		vData = new Vector<Vector<Object>>();
		vHeaders = new Vector<TableDataHeader>();	      	
		theTransTable = parent;
	}   
	public static TableData createModel(String [] columns, TableData table, TransTable parent) {
		TableData retVal = new TableData(parent);
		
		retVal.arrData = new Object[table.arrData.length][columns.length];
		retVal.arrHeaders = new TableDataHeader[columns.length];
		
		for(int x=0; x<columns.length; x++) {
			int sourceColumnIdx = table.getColumnHeaderIndex(columns[x]);
			retVal.arrHeaders[x] = table.arrHeaders[sourceColumnIdx];
			for(int y=0; y<table.arrData.length; y++) {
				retVal.arrData[y][x] = table.arrData[y][sourceColumnIdx];
			}
		}
		retVal.bReadOnly = table.bReadOnly;		
		return retVal;
	}
	
	/*****************************
	 *  GeneTable
	 */
	public TableData(GeneTable parent) {
	     vData = new Vector<Vector<Object>>();
	     vHeaders = new Vector<TableDataHeader>();	      	
	     theGeneTable = parent;
	}	 
	public static TableData createModel(String [] columns, TableData table, GeneTable parent) {
		TableData retVal = new TableData(parent);
		
		retVal.arrData = new Object[table.arrData.length][columns.length];
		retVal.arrHeaders = new TableDataHeader[columns.length];
		
		for(int x=0; x<columns.length; x++) {
			int sourceColumnIdx = table.getColumnHeaderIndex(columns[x]);
			retVal.arrHeaders[x] = table.arrHeaders[sourceColumnIdx];
			for(int y=0; y<table.arrData.length; y++) {
				retVal.arrData[y][x] = table.arrData[y][sourceColumnIdx];
			}
		}
		retVal.bReadOnly = table.bReadOnly;
		
		return retVal;
	} 	
	/*****************
	 * LibraryTable
	 */
	public TableData(LibraryTable parent) {
		vData = new Vector<Vector<Object>>();
		vHeaders = new Vector<TableDataHeader>();	      	
		theLibraryTable = parent;
	}  
	public static TableData createModel(String [] columns, TableData table, LibraryTable parent) {
		TableData retVal = new TableData(parent);
		
		retVal.arrData = new Object[table.arrData.length][columns.length];
		retVal.arrHeaders = new TableDataHeader[columns.length];
		
		for(int x=0; x<columns.length; x++) {
			int sourceColumnIdx = table.getColumnHeaderIndex(columns[x]);
			retVal.arrHeaders[x] = table.arrHeaders[sourceColumnIdx];
			for(int y=0; y<table.arrData.length; y++) {
				retVal.arrData[y][x] = table.arrData[y][sourceColumnIdx];
			}
		}
		retVal.bReadOnly = table.bReadOnly;	
		return retVal;
	}
	
	/**************************************************************
	 * SNP table
	 */
	public TableData(SNPTable parent) {
		vData = new Vector<Vector<Object>>();
		vHeaders = new Vector<TableDataHeader>();	      	
		theSNPTable = parent;
	}
	public static TableData createModel(String [] columns, TableData table, SNPTable parent) {
		TableData retVal = new TableData(parent);
		
		retVal.arrData = new Object[table.arrData.length][columns.length];
		retVal.arrHeaders = new TableDataHeader[columns.length];
		
		for(int x=0; x<columns.length; x++) {
			int sourceColumnIdx = table.getColumnHeaderIndex(columns[x]);
			retVal.arrHeaders[x] = table.arrHeaders[sourceColumnIdx];
			for(int y=0; y<table.arrData.length; y++) {
				retVal.arrData[y][x] = table.arrData[y][sourceColumnIdx];
			}
		}
		retVal.bReadOnly = table.bReadOnly;	
		return retVal;
	}
	
	/**************************************************************
	 * Exon table
	 */
	public TableData(ExonTable parent) {
		vData = new Vector<Vector<Object>>();
		vHeaders = new Vector<TableDataHeader>();	      	
		theExonTable = parent;
	}
	public static TableData createModel(String [] columns, TableData table, ExonTable parent) {
		TableData retVal = new TableData(parent);
		
		retVal.arrData = new Object[table.arrData.length][columns.length];
		retVal.arrHeaders = new TableDataHeader[columns.length];
		
		for(int x=0; x<columns.length; x++) {
			int sourceColumnIdx = table.getColumnHeaderIndex(columns[x]);
			retVal.arrHeaders[x] = table.arrHeaders[sourceColumnIdx];
			for(int y=0; y<table.arrData.length; y++) {
				retVal.arrData[y][x] = table.arrData[y][sourceColumnIdx];
			}
		}
		retVal.bReadOnly = table.bReadOnly;		
		return retVal;
	}
	/**************************************************************
	 * SNPRep table
	 */
	public TableData(SNPRepTable parent) {
		vData = new Vector<Vector<Object>>();
		vHeaders = new Vector<TableDataHeader>();	      	
		theSNPRepTable = parent;
	}
	public static TableData createModel(String [] columns, TableData table, SNPRepTable parent) {
		TableData retVal = new TableData(parent);
		
		retVal.arrData = new Object[table.arrData.length][columns.length];
		retVal.arrHeaders = new TableDataHeader[columns.length];
		
		for(int x=0; x<columns.length; x++) {
			int sourceColumnIdx = table.getColumnHeaderIndex(columns[x]);
			retVal.arrHeaders[x] = table.arrHeaders[sourceColumnIdx];
			for(int y=0; y<table.arrData.length; y++) {
				retVal.arrData[y][x] = table.arrData[y][sourceColumnIdx];
			}
		}
		retVal.bReadOnly = table.bReadOnly;		
		return retVal;
	}
    private boolean bReadOnly = false;
    //Static data structures
    private TableDataHeader [] arrHeaders = null;
    private Object [][] arrData = null;
    //Dynamic data structures
    private Vector<TableDataHeader> vHeaders = null;
    private Vector<Vector<Object>> vData = null; 
     
    private GeneTable theGeneTable = null;
    private TransTable theTransTable = null;
    private LibraryTable theLibraryTable = null;
    private LibListTable theLibListTable = null;
    private SNPTable theSNPTable = null;
    private SNPRepTable theSNPRepTable = null;
    private ExonTable theExonTable = null;
}