package viewer.panels;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import util.ErrorReport;
import util.Globals;
import viewer.ViewerFrame;
import viewer.controls.CreateJ;
import viewer.table.ColumnData;
import viewer.table.ColumnSync;
import viewer.table.TableData;

public class SNPRepColumn {
	public SNPRepColumn(ViewerFrame pF) {
		theViewerFrame = pF;
		theColumnSync = pF.getSNPRepColumnSync();
		createColumns();
	}	
	private int numModeCols=0; 
	private int numMaxCols=8; // make sure this is big enough for all columns
	
	private void createColumns() {
		theColumnData = new ColumnData();
		allColLabels = new String [numMaxCols];	
		allColDefaults = new boolean [numMaxCols];
		rowbreaks = new Vector <Integer> (); 
		
		addColumn("Row #", true, Integer.class, null, null);
		addColumn(Globals.LIBSQLID, false, Integer.class, Globals.SNPLIB_TABLE, "LIBid");
		addColumn(Globals.LIBNAME, true, String.class,   Globals.SNPLIB_TABLE, "libName");
		addColumn(Globals.SNPNAME, true, String.class,   Globals.SNP_TABLE, "rsID");
		addColumn("SNP Ref", true, Integer.class,  Globals.SNPLIB_TABLE, "refCount");
		addColumn("SNP Alt", true, Integer.class,  Globals.SNPLIB_TABLE, "altCount");
		addColumn("Rep" + Globals.SUF_PVAL, true, Integer.class,  Globals.SNPLIB_TABLE, "pvalue");
		addColumn("RepNum", true, Integer.class,  Globals.SNPLIB_TABLE, "repNum");

	   	theColumnSync.setColDefaults(numMaxCols, 0, allColDefaults);
	   	allColIsChks = theColumnSync.getColIsChk();
	}
	private  void addColumn(String label, boolean show,  Class<?> type, String sqlTable, String sqlCol) {
		if (numModeCols==numMaxCols) ErrorReport.die("SNPRepColumns numMaxCols");
		
		allColLabels[numModeCols] = label;
		allColDefaults[numModeCols] = show;
		theColumnData.addColumn(label, type, sqlTable, sqlCol, "X"+numModeCols);
		numModeCols++;
	}
	public ColumnData getColumnMap() {return theColumnData;}
	
	private void columnChange() {
		theTable.columnChange();
		setLastSelectedColumns();	
		theColumnSync.setOrderedColumns(
				TableData.orderColumns(theTable.getJTable(), getSelectedColumns()));
	}
	
	public JPanel createColumnPanel(SNPRepTable table) {
		theTable = table;
 		columnChange = new ActionListener() {
 			public void actionPerformed(ActionEvent arg0)  {
 				columnChange();
 			}
 		};
 		allColChks = new JCheckBox[numModeCols];
 		
	    	JPanel page = CreateJ.panelPage();
			  
		page.add(new JLabel("Basic"));	
		CreateJ.addColumnRows(0, numModeCols, page, 
				allColChks, allColLabels, allColIsChks, columnChange, rowbreaks);	
	 	
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
	private ViewerFrame theViewerFrame = null;
	private ColumnSync theColumnSync = null;
	private SNPRepTable	theTable = null;
	private ColumnData theColumnData = null;
	
	private ActionListener columnChange = null;
	
	private JCheckBox[]  allColChks = null;
	private String  [] allColLabels = null;
	private boolean [] allColIsChks = null;
	private boolean [] allColDefaults = null;
	Vector <Integer> rowbreaks = null;
}
