package viewer.panels;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;

import util.Globals;
import util.ErrorReport;
import viewer.ViewerFrame;
import viewer.controls.CreateJ;
import viewer.table.ColumnData;
import viewer.table.ColumnSync;
import viewer.table.TableData;

public class LibListColumn {
	public LibListColumn(ViewerFrame pF, int mode) {
		theViewerFrame = pF;
		theColumnSync = pF.getListLibColumnSync();
		hasRead = pF.getMetaData().hasReadCnt();
		modeTable = mode;
		createColumns();
	}	
	private int numModeCols=0; 
	private int numMaxCols=24; // make sure this is big enough for all columns
	
	private void createColumns() {
		theColumnData = new ColumnData();
		String libTable="";
		allColLabels = new String [numMaxCols];	
		allColDefaults = new boolean [numMaxCols];
		rowbreaks = new Vector <Integer> (); 
		if (modeTable==Globals.MODE_GENE || modeTable==Globals.MODE_GENE_REPS) {
			libTable =  Globals.GENELIB_TABLE;
		}
		else if (modeTable==Globals.MODE_TRANS || modeTable==Globals.MODE_TRANS_REPS) {
			libTable =  Globals.TRANSLIB_TABLE;
		}
		else System.out.println("Internal error in liblistColumn createColumns");
		
		addColumn("Row #", true, Integer.class, null, null);
		addColumn(Globals.LIBSQLID, false, Integer.class, libTable, "LIBid");
		addColumn(Globals.LIBNAME, true, String.class,  libTable, "libName");
		
		if (modeTable==Globals.MODE_GENE || modeTable==Globals.MODE_GENE_REPS) {
			addColumn(Globals.libTabId, false, String.class, libTable, "GENEid");
			addColumn(Globals.GENENAME, true, String.class, libTable, "geneName");
		}
		else if (modeTable==Globals.MODE_TRANS || modeTable==Globals.MODE_TRANS_REPS) {
			addColumn(Globals.libTabId, false, String.class, libTable, "TRANSid");
			addColumn(Globals.TRANSNAME, true, String.class, libTable, "transName");	
		}	
		if (modeTable==Globals.MODE_GENE_REPS || modeTable==Globals.MODE_TRANS_REPS) {
			addColumn("RepNum", true, Integer.class, libTable, "repNum");
		}
		else {
			addColumn("#SNPCov", false, Integer.class, libTable, "cntSNPCov");
		}
		rowbreaks.add(numModeCols);
		
		addColumn(Globals.LIBREF, true, Integer.class, libTable, "refCount");
		addColumn(Globals.LIBALT, true, Integer.class, libTable, "altCount");
		String col="SNP " + Globals.SUF_PVAL;
		if (modeTable==Globals.MODE_GENE_REPS || modeTable==Globals.MODE_TRANS_REPS) 
			col="SNP " + Globals.SUF_REP;
		addColumn(col, true, Integer.class, libTable, "pvalue"); 
		if (hasRead) {
			addColumn(Globals.COUNT2 + " ref", false, Integer.class, libTable, "refCount2");
			addColumn(Globals.COUNT2 + " alt", false, Integer.class, libTable, "altCount2");
			addColumn(Globals.COUNT2 + " tot", false, Integer.class, libTable, "totCount2");
			col="Read " + Globals.SUF_PVAL;
			if (modeTable==Globals.MODE_GENE_REPS || modeTable==Globals.MODE_TRANS_REPS) 
				col="Read " + Globals.SUF_REP;
			addColumn(col, false, Integer.class, libTable, "pvalue2"); 
		}
	
	   	theColumnSync.setColDefaults(numMaxCols, 0, allColDefaults);
	   	allColIsChks = theColumnSync.getColIsChk();
	}
	// The Class is overwritten by the mysql class
	private  void addColumn(String label, boolean show,  Class<?> type, String sqlTable, String sqlCol) {
		if (numModeCols==numMaxCols) ErrorReport.die("LibListColumns increase numMaxCols ");
		
		allColLabels[numModeCols] = label;
		allColDefaults[numModeCols] = show;
		theColumnData.addColumn(label, type, sqlTable, sqlCol, "X"+modeTable+numModeCols);
		numModeCols++;
	}
	public ColumnData getColumnSQLMap(int mode) {return theColumnData;}
	
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

	public JPanel createColumnPanel(LibListTable table) {
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
    private void setClearColumns() {
		for (int x=0; x<allColChks.length; x++) 
			allColChks[x].setSelected(false);
		allColChks[2].setSelected(true);
		columnChange();
    }    
	private int modeTable;	
	private boolean hasRead;
		
	private JCheckBox[]  allColChks = null;
	private String  [] allColLabels = null;
	private boolean [] allColIsChks = null;
	private boolean [] allColDefaults = null;
	Vector <Integer> rowbreaks = null;
	private ActionListener columnChange = null;
	
	private ViewerFrame theViewerFrame = null;
	private ColumnSync theColumnSync = null;
	private LibListTable	theTable = null;
	private ColumnData theColumnData = null;
}
