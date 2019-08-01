package viewer.panels;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;

import util.ErrorReport;
import util.Globals;
import viewer.ViewerFrame;
import viewer.controls.CreateJ;
import viewer.table.ColumnData;
import viewer.table.ColumnSync;
import viewer.table.TableData;

public class LibraryColumn {

	public LibraryColumn(ViewerFrame pF) {
		theViewerFrame = pF;
		theColumnSync = pF.getLibraryColumnSync();
		hasRead = pF.getMetaData().hasReadCnt();
		hasCond2 = pF.getMetaData().hasCond2();
		createColumns();
	}
	
	private int numModeCols=0; 
	private int numMaxCols=15; // make sure this is big enough for all columns
	
	private void createColumns() {
		theColumnData = new ColumnData();
		allColLabels = new String [numMaxCols];	
		allColDefaults = new boolean [numMaxCols];
		rowBreaks = new Vector <Integer> (); 
		String table = Globals.LIBRARY_TABLE;
		
		addColumn("Row #", true, Integer.class, null, null);
		addColumn(Globals.LIBSQLID, false, Integer.class, table, "LIBid");
		addColumn(Globals.LIBNAME, true, String.class,  table, "libName");
		addColumn("Hybrid", false, String.class,  table, "remark");
		addColumn(Globals.condition1, false, String.class, table, "strain");
		if (hasCond2) addColumn(Globals.condition2, false, String.class, table, "tissue");
		addColumn("#Reps", true, Integer.class, table, "reps");
		rowBreaks.add(numModeCols);
		
		addColumn("SNP Ref", true, Integer.class, table, "varRefSize");
		addColumn("SNP Alt", true, Integer.class, table, "varAltSize");
		addColumn("SNP All", true, Integer.class, table, "varLibSize");
		if (hasRead) {
			addColumn(Globals.COUNT2 + " Ref", true, Integer.class, table, "readRefSize");
			addColumn(Globals.COUNT2 + " Alt", true, Integer.class, table, "readAltSize");
			addColumn(Globals.COUNT2 + " All", true, Integer.class, table, "readLibSize");
		}
		
		theColumnSync.setColDefaults(numMaxCols, 0, allColDefaults);
	   	allColIsChks = theColumnSync.getColIsChk();
	}
	private  void addColumn(String label, boolean show,  Class<?> type, String sqlTable, String sqlCol) {
		if (numModeCols==numMaxCols) ErrorReport.die("LibraryColumns increase numMaxCols ");
		
		allColLabels[numModeCols] = label;
		allColDefaults[numModeCols] = show;
		theColumnData.addColumn(label, type, sqlTable, sqlCol, "X"+numModeCols);
		numModeCols++;
	}
	public ColumnData getColumnSQLMap() {return theColumnData;}
	
	public void columnMoved(JTable tab) {
		theColumnSync.setOrderedColumns(
				TableData.orderColumns(tab, getSelectedColumns()));
	}
	 private void columnChange() {
			theTable.columnChange();
			setLastSelectedColumns();
			theColumnSync.setOrderedColumns(
					TableData.orderColumns(theTable.getJTable(), getSelectedColumns()));
	}
	 public JPanel createColumnPanel(LibraryTable table) {
		theTable = table;
 		columnChange = new ActionListener() {
 			public void actionPerformed(ActionEvent arg0)  {
 				columnChange();
 			}
 		};
 		allColChks = new JCheckBox[numModeCols];
 		
	    	JPanel page = CreateJ.panelPage();
			  
		page.add(new JLabel("Basic Selection"));	
		CreateJ.addColumnRows(0, numModeCols, page, 
				allColChks, allColLabels, allColIsChks, columnChange, rowBreaks);	
	 	
	    	page.setBorder(BorderFactory.createLineBorder(Globals.COLOR_BORDER));
	    	page.setMaximumSize(page.getPreferredSize());
	    	return page;
    }
	 	
    private void setLastSelectedColumns() {
		String [] columns = getSelectedColumns();
		if(columns == null || columns.length == 0) return;
		
		Vector<String> sels = new Vector<String> ();	 
    		for(int x=0; x<columns.length; x++) sels.add(columns[x]);
    		for(int x=0; x<allColLabels.length; x++) 
    			allColIsChks[x] = sels.contains(allColLabels[x]);	  
     	theColumnSync.setColIsChk(allColIsChks);
	}
    
    // called by Table for display
    public String [] getSelectedColumns() {
	    	String [] retVal = null;
	    	
	    	int selectedCount = 0;
	    	for(int x=0; x<allColChks.length; x++) 
	    		if(allColChks[x].isSelected())
	    			selectedCount++;
	    	
	    	retVal = new String[selectedCount];
	    	int targetIndex = 0;
	    	for(int x=0; x<allColChks.length; x++) 
	    		if(allColChks[x].isSelected()) {
	    			retVal[targetIndex] = allColChks[x].getText();
	    			targetIndex++;
	    		}
	    	
	    	return retVal;
	}
    public String [] getOrderedColumns() {
      	String [] orderedCol = theColumnSync.getOrderedColumns();
	   	if (orderedCol==null) return getSelectedColumns();
	   
	    	int selectedCount = 0;
	    	for(int x=0; x<allColChks.length; x++) 
	    		if(allColChks[x].isSelected()) selectedCount++;
	    String [] columns = new String[selectedCount];
	    
	    boolean [] added = new boolean[allColChks.length];
	    for (int i=0; i<allColChks.length; i++) added[i]=false;
	     
	    	int targetIndex = 0;
	    	for (int i=0; i<orderedCol.length; i++) {
	    		String col = orderedCol[i];
	    		for(int x=0; x<allColChks.length; x++) {
		    		if(col.equals(allColLabels[x]) && allColChks[x].isSelected()) {
		    			added[x]=true;
		    			columns[targetIndex++] = col;
		    			break;
		    		}
	    		}
	    	}
	    	for(int x=0; x<allColChks.length; x++) 
	    		if(allColChks[x].isSelected() && !added[x]) {
	    			columns[targetIndex] = allColChks[x].getText();
	    			targetIndex++;
	    		}	    	
	    	return columns;
    }
    private void setClearColumns() {
		for (int x=0; x<allColChks.length; x++) 
			allColChks[x].setSelected(false);
		allColChks[2].setSelected(true);
		columnChange();
    }    
	private boolean hasRead = false, hasCond2;	
	private JCheckBox[]  allColChks = null;
	private String  [] allColLabels = null;
	private boolean [] allColIsChks = null;
	private boolean [] allColDefaults = null;
	Vector <Integer> rowBreaks = null;
	private ActionListener columnChange = null;
	
	private ViewerFrame theViewerFrame = null;
	private ColumnSync theColumnSync = null;
	private LibraryTable	theTable = null;
	private ColumnData theColumnData = null;
}
