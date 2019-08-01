package viewer.panels;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;

import util.ErrorReport;
import util.Globals;
import database.MetaData;
import viewer.ViewerFrame;
import viewer.controls.CreateJ;
import viewer.table.ColumnData;
import viewer.table.ColumnSync;
import viewer.table.TableData;

public class SNPColumn {
	public SNPColumn(ViewerFrame pF, int mode, long start) {
		theViewerFrame = pF;
		theMetaData = pF.getMetaData();
		theColumnSync = pF.getSNPColumnSync();
		libNames = theMetaData.getLibAbbr();
		hasManyCond = theMetaData.hasManyCond();
		modeTable = mode;
		createColumns(start);
	}
	/********************************************************* */
	private final static int LIB_PREFIX=2;
	private final static String [] prefix = {"", Globals.PRE_Sratio, 
		Globals.PRE_Sscore, Globals.PRE_Spval};
	
	private int numModeCols=0; 	// computed number of columns
	private int numMaxCols = 28; // enter maximum static (ok to be too large)
	private int startLibCols=0;	// start of static 
	private final int nDynSet = prefix.length-1; // number of dynamic sets
	
	private void createColumns(long start) {
		boolean hasCDSpos = theMetaData.hasCDSpos();
		boolean hasRSCU	= theMetaData.hasRSCU();
		boolean hasCDNApos = theMetaData.hasCDNApos();
		boolean hasInclude = theMetaData.hasInclude();
		
		theColumnData = new ColumnData();
		
		numMaxCols += nDynSet*libNames.length;
		allColLabels = new String [numMaxCols];	
		allColDefaults = new boolean [numMaxCols];
		rowBreaks = new Vector <Integer> (); 
		
		addColumn("Row #", true, Integer.class, null, null);
		addColumn(Globals.SNPSQLID, false, Integer.class, Globals.SNP_TABLE, "SNPid");
		addColumn(Globals.SNPNAME, true, String.class, Globals.SNP_TABLE, "rsID");
		addColumn("Qual", false, float.class, Globals.SNP_TABLE, "qual");
		addColumn("Ref", false, String.class, Globals.SNP_TABLE, "ref");
		addColumn("Alt", false, String.class, Globals.SNP_TABLE, "alt");
		rowBreaks.add(numModeCols);

		addColumn("Chr", false, Integer.class, Globals.SNP_TABLE, "chr");	
		addColumn("Pos", false, Integer.class, Globals.SNP_TABLE, "pos");
		addColumn("#LibCov", false, Integer.class, Globals.SNP_TABLE, "cntLibCov");
		addColumn("#LibAI", false, Integer.class, Globals.SNP_TABLE, "cntLibAI");
		addColumn("#Cov", false, Integer.class, Globals.SNP_TABLE, "cntCov");
		addColumn("isSNP", false, Integer.class, Globals.SNP_TABLE, "isSNP");
		addColumn("odRmk", false, float.class, Globals.SNP_TABLE, "remark");
		rowBreaks.add(numModeCols);
		
		if (modeTable==Globals.MODE_TRANS || modeTable==Globals.MODE_SNP_TRANS) {		
			addColumn("isCoding", false, Integer.class, Globals.SNPTRANS_TABLE, "isCoding");
			addColumn("isMis", false, Integer.class, Globals.SNPTRANS_TABLE, "isMissense");
			addColumn("isDamage", false, Integer.class, Globals.SNPTRANS_TABLE, "isDamaging");
			addColumn("Effect", true, String.class, Globals.SNPTRANS_TABLE, "effect");
			addColumn("nExon", false, Integer.class, Globals.SNPTRANS_TABLE, "nExon");
			addColumn(Globals.TRANSNAME, false, String.class, Globals.SNPTRANS_TABLE, "transName");
			if (start!=-1)
				addColumn("Rpos", false, Integer.class, null, 
							("ABS("+Globals.SNP_TABLE +".pos-" +start+")+1"));
			rowBreaks.add(numModeCols);
			
			if (hasCDNApos) {
				addColumn("cDNApos", false, Integer.class, Globals.SNPTRANS_TABLE, "cDNApos");
				addColumn("Dist", false, String.class, Globals.SNPTRANS_TABLE, "dist");
				if (hasInclude) addColumn("Include", false, String.class, Globals.SNPTRANS_TABLE, "included");
			}
			if (hasCDSpos) addColumn("CDSpos", false, Integer.class, Globals.SNPTRANS_TABLE, "CDSpos");
			
			addColumn(Globals.COL_AAPOS, false, Integer.class, Globals.SNPTRANS_TABLE, "AApos");
			addColumn("AAs", false, Integer.class, Globals.SNPTRANS_TABLE, "AAs");
			addColumn("Codons", false, Integer.class, Globals.SNPTRANS_TABLE, "codons");
			if (hasRSCU) {
				addColumn("RSCU", false, String.class, Globals.SNPTRANS_TABLE, "rscu");
				addColumn("S/RSCU", false, Double.class, Globals.SNPTRANS_TABLE, "rscu");
				addColumn("BioChe", false, Integer.class, Globals.SNPTRANS_TABLE, "bioChem");
			}
		}
		else { 
			addColumn("isCoding", false, Integer.class, Globals.SNP_TABLE, "isCoding");
			addColumn("isMis", false, Integer.class, Globals.SNP_TABLE, "isMissense");
			addColumn("isDamage", false, Integer.class, Globals.SNP_TABLE, "isDamaging");
			addColumn("Effect", true, String.class, Globals.SNP_TABLE, "effectList");
			addColumn("ExonList", false, String.class, Globals.SNP_TABLE, "exonList");
			if (modeTable==Globals.MODE_GENE) {
				addColumn(Globals.GENENAME, false, String.class, Globals.SNPGENE_TABLE, "geneName");
				if (start!=-1)
					addColumn("Rpos", true, Integer.class, null, ("ABS("+Globals.SNP_TABLE +".pos-" +start+")"));
			}
		}
		startLibCols=numModeCols;
		
		String R = Globals.PRE_REFCNT;
		String A = Globals.PRE_ALTCNT;
		for (int i=0; i<libNames.length; i++) {
			String computeCol = "CONCAT(SNP." + R+libNames[i] + ",':',SNP." + A+libNames[i] + ")";
			addColumn(prefix[1]+libNames[i], false, String.class, null, computeCol);
		}
		// the column header has "/" in it, so TableData.java performs the division for display
		for (int i=0; i<libNames.length; i++) {
			String computeCol = "CONCAT(SNP." + R+libNames[i] + ",':',SNP." + A+libNames[i] + ")";
			addColumn(prefix[2]+libNames[i], false, Float.class, null, computeCol);	
		}
		for (int i=0; i<libNames.length; i++) {
			String colName = "SNP."+libNames[i];
			addColumn(prefix[3]+libNames[i],false, Float.class, null, colName);
		}
		
		// set if first time; must be Max because shared between all LibListTables
	   	theColumnSync.setColDefaults(numMaxCols, 0, allColDefaults);
	   	allColIsChk = theColumnSync.getColIsChk();
	}
	
	private  void addColumn(String label, boolean show,  Class<?> type, String sqlTable, String sqlCol) {
		if (numModeCols==numMaxCols) ErrorReport.die("SNPColumns went over numMaxCols");
		
		allColLabels[numModeCols] = label;
		allColDefaults[numModeCols] = show;
		theColumnData.addColumn(label, type, sqlTable, sqlCol, "X"+modeTable+numModeCols);
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
	
    public JScrollPane createColumnPanel(SNPTable table) {
    		theTable = table;
    		columnChange = new ActionListener() {
    			public void actionPerformed(ActionEvent arg0)  {
    				columnChange();
    			}
    		};
    		
    		allColChks = new JCheckBox[numModeCols];
    		JPanel row;
	    	JPanel page = CreateJ.panelPage();
	    
	 	page.add(Box.createVerticalStrut(5));
	    row = CreateJ.panelLine();
	    row.add(new JLabel("Basic Selection"));
		btnClearColumns = CreateJ.buttonClear();
		btnClearColumns.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setClearColumns();
			}
		});
		row.add(Box.createHorizontalStrut(Globals.COLUMN_CLEAR_LOC));
		row.add(btnClearColumns);
	 	page.add(row);
		page.add(Box.createVerticalStrut(5));
		CreateJ.addColumnRows(0, startLibCols, page, allColChks, allColLabels, 
				allColIsChk, columnChange, rowBreaks);	
		//---------------------------------------------------
		// start dynamic columns
		
		row = CreateJ.panelTextLine("Library Group Selection (select at least one of each)");	
		row.add(Box.createHorizontalStrut(5));
		btnApplySelection = CreateJ.buttonApply();
		btnApplySelection.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				columnApply();
			}
		});
		row.add(btnApplySelection);
		
		page.add(new JSeparator());
		page.add(Box.createVerticalStrut(5));
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		// Tissues and strains
		String [] tissueAbbr = theViewerFrame.getMetaData().getTisAbbv();
		String [] strainAbbr = theViewerFrame.getMetaData().getStrAbbv();
		libChks = new JCheckBox [tissueAbbr.length + strainAbbr.length];
		
		row = CreateJ.panelTextLine("  Strain:", 60);
		CreateJ.addToColumnRow(row, 0, libChks, strainAbbr, null);
		
		page.add(row);
		page.add(Box.createVerticalStrut(1));
		
		row = CreateJ.panelTextLine("  " + Globals.cond2 + ":",60);
		CreateJ.addToColumnRow(row, strainAbbr.length, libChks, tissueAbbr, null);
		
		page.add(row);
		page.add(Box.createVerticalStrut(1));
		
		// Type groups: 	don't change order!! 
		String [] typeAbbr = {"Ratio", "Score", "Pvalue"};	
		typeChks = new JCheckBox [typeAbbr.length];
		typeIndices = new int [typeAbbr.length+1]; 
		
		row = CreateJ.panelTextLine("  RefAlt:", 60);
		CreateJ.addToColumnRow(row, 0, typeChks, typeAbbr, null);
		page.add(row);
		page.add(Box.createVerticalStrut(3));
		initLibChks(); // sets the libChks based on the syncColumns
		
		//-----------------------------------------------------
		// Ref:Alt: dynamic ratio columns
		page.add(new JSeparator());
		page.add(Box.createVerticalStrut(5));
		page.add(new JLabel("Library Ref:Alt Selection"));	
		page.add(Box.createVerticalStrut(10));
			
		int ti=0;
		page.add(new JLabel(" Ratio"));
	   	int offset = startLibCols;
	   	CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
	   			allColLabels, allColIsChk, columnChange, null);
	   	typeIndices[ti++] = offset;
	  	
	   	page.add(new JLabel(" Score"));
		offset += libNames.length;
		CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
				allColLabels, allColIsChk, columnChange, null);
		typeIndices[ti++] = offset;
		
		page.add(new JLabel(" Pvalue"));
		offset += libNames.length;
		CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
				allColLabels, allColIsChk, columnChange);
		typeIndices[ti++] = offset;
		typeIndices[ti] = offset+libNames.length;
		
	    	page.setBorder(BorderFactory.createLineBorder(Globals.COLOR_BORDER));
	    	page.setMaximumSize(page.getPreferredSize());
	    	
	    	JScrollPane sPane =  new JScrollPane (page);
 		sPane.setBorder( null );
 		sPane.setPreferredSize(theViewerFrame.getSize());
 		sPane.getVerticalScrollBar().setUnitIncrement(15);
 		sPane.setAlignmentX(Component.LEFT_ALIGNMENT);
	    	return sPane;
    }
   private void groupSelection(JPanel page) {
	    
		
		JPanel row = CreateJ.panelTextLine("Library Group Selection (select at least one of each)");	
		row.add(Box.createHorizontalStrut(5));
		btnApplySelection = CreateJ.buttonApply();
		btnApplySelection.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				columnApply();
			}
		});
		row.add(btnApplySelection);
		
		if (hasManyCond) {
			page.add(new JSeparator());
			page.add(Box.createVerticalStrut(5));
			page.add(row);
			page.add(Box.createVerticalStrut(5));
		}
		// Tissues and strains
		String [] tissueAbbr = theViewerFrame.getMetaData().getTisAbbv();
		String [] strainAbbr = theViewerFrame.getMetaData().getStrAbbv();
		libChks = new JCheckBox [tissueAbbr.length + strainAbbr.length];
		
		row = CreateJ.panelTextLine("  Strain:", 60);
		CreateJ.addToColumnRow(row, 0, libChks, strainAbbr, null);
		if (hasManyCond) {
			page.add(row);
			page.add(Box.createVerticalStrut(1));
		}
		row = CreateJ.panelTextLine("  " + Globals.cond2 + ":",60);
		CreateJ.addToColumnRow(row, strainAbbr.length, libChks, tissueAbbr, null);
		if (hasManyCond) {
			page.add(row);
			page.add(Box.createVerticalStrut(1));
		}
		// Type groups: 	don't change order!! 
		String [] typeAbbr = {"SNP Ratio", "SNP Score"};	
		typeChks = new JCheckBox [typeAbbr.length];
		typeIndices = new int [typeAbbr.length+1]; 
		
		row = CreateJ.panelTextLine("  RefAlt:", 60);
		CreateJ.addToColumnRow(row, 0, typeChks, typeAbbr, null);
		if (hasManyCond) {
			page.add(row);
			page.add(Box.createVerticalStrut(3));
		}
		initLibChks(); // sets the libChks based on the syncColumns
   }
    /**************************************************
   	 * setSyncColumns: called when the panel is created to set 
   	 * the Strain and Tissue from sync
   	 */
   	private void initLibChks() {
   		syncLibs = theColumnSync.getLibs();
   		syncAbbr = theColumnSync.getAbbr();
   		if (syncAbbr==null) return;	
   		
   		for (int i=0; i<libChks.length; i++) { 
   			String abbr = libChks[i].getText();
   			if (syncAbbr.contains(abbr)) {
   				 libChks[i].setSelected(true);
   			}
   			else libChks[i].setSelected(false);
   		}
   		boolean [] type = theColumnSync.getType();
   		typeChks[0].setSelected(type[0]);
   		typeChks[1].setSelected(type[1]);
   	 }
    /******************************************
     * Apply Selection button 
     */
    private void columnApply() {
    		syncAbbr = new Vector <String> ();
		for (int j=0;  j < libChks.length; j++) {
			JCheckBox libBox = libChks[j];
			if (libBox.isSelected()) syncAbbr.add(libBox.getText());
		}
		theColumnSync.setAbbr(syncAbbr);
		
		syncLibs = new Vector <String> ();
		for (String lib : libNames) {
			int parts=0;
			for (String abbr: syncAbbr) {
				if (lib.startsWith(abbr) || lib.endsWith(abbr)) parts++; 
			}
			if (parts==2) syncLibs.add(lib);
		}
		theColumnSync.setLibs(syncLibs);
		
		boolean [] type = new boolean [Globals.numType];
		
    		for (int i=startLibCols; i<allColChks.length; i++) {
    			JCheckBox colBox = allColChks[i];
    			colBox.setSelected(true); // true until find an corresponding unchecked box
    			boolean isSel=true;
    			
    			// check libChks (where the syncLibs is already updated)
    			String libCol = colBox.getText().substring(LIB_PREFIX); // remove prefix
    			if (!syncLibs.contains(libCol)){
    				colBox.setSelected(false);
    				continue;
    			} 		
    			// check typeChks
    			for (int j=0;  j < typeChks.length && isSel; j++) {
   		    		JCheckBox typeBox = typeChks[j];
   		    	
   		    		if (!typeBox.isSelected()) {
   		    			int start = typeIndices[j];
		   		    	int end = typeIndices[j+1]; // 1 greater than typeChks
   		    			if (i>=start && i<end) isSel=false;
   		    			type[j] = false;
		    		}
		    		else type[j]=true;
   		    	}
    			if (!isSel) colBox.setSelected(false);
    			theColumnSync.setType(type);
    		}
    		columnChange();
    }
    
    public Vector <String> getChkLibs() {return syncLibs;}
    public Vector <String> getChkAbbr() {return syncAbbr;}
    		   
    private void setLastSelectedColumns() {
		String [] columns = getSelectedColumns();
		if(columns == null || columns.length == 0) return;
		
		Vector<String> sels = new Vector<String> ();	 
    		for(int x=0; x<columns.length; x++) sels.add(columns[x]);
    		for(int x=0; x<allColLabels.length; x++) 
    			allColIsChk[x] = sels.contains(allColLabels[x]);	
     	theColumnSync.setColIsChk(allColIsChk);
	}
     
    /************************************
     * Both called from Table for column display
     */
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
		for (int i=0; i<libChks.length;i++)  libChks[i].setSelected(false); 
		for (int i=0; i<typeChks.length;i++)  typeChks[i].setSelected(false); 
		columnChange();
    }
		
    String [] libNames = null; 
    private int modeTable=0;
    private boolean hasManyCond;
	
	private JCheckBox[]  allColChks = null;
	private String  [] allColLabels = null;
	private boolean [] allColIsChk = null;
	private boolean [] allColDefaults = null;	
	private ActionListener columnChange = null;	

	private JButton btnClearColumns=null;
	private JButton btnApplySelection=null;
	private JCheckBox [] libChks = null;
	private JCheckBox [] typeChks = null;
	private int [] typeIndices = null; // for Ratio, etc
	private Vector <Integer> rowBreaks = null;
	
	private Vector <String> syncLibs = null;
	private Vector <String> syncAbbr = null;
		   
	private ViewerFrame theViewerFrame = null;
	private ColumnSync theColumnSync = null;
	private MetaData 	theMetaData = null;
	private SNPTable		theTable = null;
	private ColumnData theColumnData = null;
}
