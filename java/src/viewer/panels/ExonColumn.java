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
import database.MetaData;

public class ExonColumn {
	public ExonColumn(ViewerFrame pF, long start) {
		theViewerFrame = pF;
		theColumnSync = pF.getExonColumnSync();
		theMetaData = pF.getMetaData();
		createColumns(start);
	}
		
	private int numModeCols=0; 
	private int numMaxCols=15; // make sure this is big enough for all columns
	
	private void createColumns(long start) {
		theColumnData = new ColumnData();
		allColLabels = new String [numMaxCols];	
		allColDefaults = new boolean [numMaxCols];
		rowBreaks = new Vector <Integer> (); 
		String table = Globals.EXON_TABLE;
		
		addColumn("Row #", false, Integer.class, null, null);
		addColumn("EXONid", false, Integer.class, table, "EXONid");
		if (theMetaData.hasNames()) addColumn(Globals.TRANSNAME, false, String.class,  table, "transName");
		else addColumn(Globals.TRANSNAME, false, String.class,  table, "transIden");
		addColumn("Exon", true, Integer.class, table, "nExon");
		addColumn("#SNP", true, Integer.class, table, "cntSNP");
		addColumn("#Indel", true, Integer.class, table, "cntIndel");
		addColumn("Frame", false, Integer.class, table, "frame");
		rowBreaks.add(numModeCols);
		
		addColumn("Start", true, Integer.class, table, "cStart");
		addColumn("End", true, Integer.class, table, "cEnd");
		if (start != -1) {
			addColumn("Rstart", false, Integer.class, null, ("ABS("+table+".cStart-" +start+")"));
			addColumn("Rend", false, Integer.class, null, ("ABS("+table+".cEnd-" +start+")"));
		}
		addColumn("Len", true, Integer.class, table, "cEnd-cStart+1");
		addColumn("NextDiff", false, Integer.class, table, "intron");
		addColumn("Remark", true, String.class, table, "remark");
			
		theColumnSync.setColDefaults(numMaxCols, 0, allColDefaults);
	   	allColIsChks = theColumnSync.getColIsChk();
	}
	private  void addColumn(String label, boolean show,  Class<?> type, String sqlTable, String sqlCol) {
		if (numModeCols==numMaxCols) ErrorReport.die("ExonColumns increase numMaxCols ");
		
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
    public JPanel createColumnPanel(ExonTable table) {   	
    		theTable = table;
		columnChange = new ActionListener() {
			public void actionPerformed(ActionEvent arg0)  {
				columnChange();
			}
		};
		allColChks = new JCheckBox[numModeCols];
		
	    	JPanel page = CreateJ.panelPage();	  
		page.add(new JLabel("Basic"));	
		CreateJ.addColumnRows(0, numModeCols, page, allColChks, allColLabels, 
				allColIsChks, columnChange, rowBreaks);	
	 	
	    	page.setBorder(BorderFactory.createTitledBorder("Columns"));
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
	    	int selectedCount = 0;
	    	for(int x=0; x<allColChks.length; x++) 
	    		if(allColChks[x].isSelected())
	    			selectedCount++;
	    	
	    	String [] columns = new String[selectedCount];
	    	int targetIndex = 0;
	    	for(int x=0; x<allColChks.length; x++) 
	    		if(allColChks[x].isSelected()) {
	    			columns[targetIndex] = allColChks[x].getText();
	    			targetIndex++;
	    		}	    	
	    	return columns;
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
		
	private JCheckBox[]  allColChks = null;
	private String  [] allColLabels = null;
	private boolean [] allColIsChks = null;
	private boolean [] allColDefaults = null;	
	
	Vector <Integer> rowBreaks = null;
	private ActionListener columnChange = null;
	
	private ViewerFrame theViewerFrame = null;
	private MetaData theMetaData = null;
	private ColumnSync theColumnSync = null;
	private ExonTable	theTable = null;
	private ColumnData theColumnData = null;
}
