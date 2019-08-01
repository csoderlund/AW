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

import database.MetaData;

import util.ErrorReport;
import util.Globals;
import viewer.ViewerFrame;
import viewer.controls.CreateJ;
import viewer.table.ColumnData;
import viewer.table.ColumnSync;
import viewer.table.TableData;

/*********************************************************
 * This is created in ViewerFrame at startup and shared by all Gene tables
 * so that selected in one table will be used in the next created table
 */
public class GeneColumn extends JPanel {
	private static final long serialVersionUID = -3559502948519419077L;
	
	public GeneColumn(ViewerFrame pF) {
		theViewerFrame = pF;
		theMetaData = pF.getMetaData();
		libNames = theMetaData.getLibAbbr();
		hasTotals = theMetaData.hasReadCnt();
		hasCond2 = theMetaData.hasCond2();
		theColumnSync = pF.getGeneColumnsSync();
		createColumns();
	}
	private final static String [] prefix = {"", 
		Globals.PRE_Sratio, Globals.PRE_Sscore, 
		Globals.PRE_Tratio, Globals.PRE_Tscore, 
		Globals.PRE_Spval, Globals.PRE_Rpval 
	};
	private int numModeCols=0; 	// computed number of columns	
	private int startLibCols=0;	// start of static 
	private final int nDynSet = prefix.length-1; // number of dynamic sets
	private final static int nHybDynSet = 3;
	private int numMaxCols = 21; /** enter maximum static (ok to be too large) **/
	
	private void createColumns() {
		theColumnData = new ColumnData();
		
		numMaxCols += (nHybDynSet*libNames.length) + (nDynSet*libNames.length);
		allColLabels = new String [numMaxCols];	
		allColDefaults = new boolean [numMaxCols];
		rowBreaks = new Vector <Integer> (); 
		
		/** static row **/
		addColumn("Row #", true, Integer.class, null, null);
		addColumn(Globals.GENESQLID, false,  Integer.class, Globals.GENE_TABLE, "GENEid");
		addColumn(Globals.GENENAME, true, String.class, Globals.GENE_TABLE, "geneName");
		addColumn("Ident", false, String.class, Globals.GENE_TABLE, "geneIden");
		addColumn("Descript",false,  String.class, Globals.GENE_TABLE, "descript");
		addColumn("Synonyms",false,  String.class, Globals.GENE_TABLE, "synonyms");
		
		rowBreaks.add(numModeCols);
		addColumn("Chr", false, String.class, Globals.GENE_TABLE,  "chr");
		addColumn("Strand", false, String.class, Globals.GENE_TABLE, "strand");
		addColumn(Globals.START, false, Integer.class, Globals.GENE_TABLE, "start");
		addColumn(Globals.END, false, Integer.class, Globals.GENE_TABLE, "end");
		//addColumn("Type", false, String.class, Globals.GENE_TABLE, "type");
		addColumn("Length", false, Integer.class, null, "abs(gene.end-gene.start)+1");
		addColumn("#Trans", true, Integer.class, Globals.GENE_TABLE, "cntTrans");
		addColumn("#UniqExon", true, Integer.class, Globals.GENE_TABLE, "cntUniqueExons");
		rowBreaks.add(numModeCols);
		
		addColumn("#Libs", false, Integer.class, Globals.GENE_TABLE, "cntLib");
		addColumn("#LibAI", false, Integer.class, Globals.GENE_TABLE, "cntLibAI");
		addColumn("#Indel", true, Integer.class, Globals.GENE_TABLE, "cntIndel");
		addColumn("#SNPs", true, Integer.class, Globals.GENE_TABLE, "cntSNP");
		addColumn("#SNPCov", true, Integer.class, Globals.GENE_TABLE, "cntSNPCov");
		addColumn("#SNPAI", true, Integer.class, Globals.GENE_TABLE, "cntSNPAI");
		addColumn("#Mis", true, Integer.class, Globals.GENE_TABLE, "cntMissense");

		startLibCols=numModeCols;
		if (numMaxCols<numModeCols) ErrorReport.die("GeneColumns: change numMaxCols to " + numModeCols);
		
		/** dynamic fields **/
		String R = Globals.PRE_REFCNT;
		String A = Globals.PRE_ALTCNT;
		String S = Globals.SUF_TOTCNT;
	
		String [] libs = theMetaData.getLibAbbr();
		for (int i=0; i<libs.length; i++) {
			String computeCol = "CONCAT(gene." + R+libs[i] + ",':',gene." + A+libs[i] + ")";
			addColumn(prefix[1]+libs[i], false, String.class, null, computeCol);	
		}
		for (int i=0; i<libs.length; i++) {
			String computeCol = "CONCAT(gene." + R+libs[i] + ",':',gene." + A+libs[i] + ")";
			addColumn(prefix[2]+libs[i], false, Float.class, null, computeCol);	
		}	
		for (int i=0; i<libs.length; i++) {
			String colName = "gene."+libs[i];
			addColumn(prefix[5]+libs[i],false, Float.class, null, colName);
		}
		if (hasTotals) {
			for (int i=0; i<libs.length; i++) {
				String computeCol = "CONCAT(gene."+R+libs[i]+S + ",':',gene." + A+libs[i]+S + ")";
				addColumn(prefix[3]+libs[i], false, String.class, null, computeCol);	
			}
			for (int i=0; i<libs.length; i++) {
				String computeCol = "CONCAT(gene."+R+libs[i]+S + ",':',gene." + A+libs[i]+S + ")";
				addColumn(prefix[4]+libs[i], false, Float.class, null, computeCol);	
			}
			for (int i=0; i<libs.length; i++) {
				String colName = "gene."+libs[i] + S;
				addColumn(prefix[6]+libs[i],false, Float.class, null, colName);	
			}
		}
	   	theColumnSync.setColDefaults(numMaxCols, 0, allColDefaults);
	   	allColIsChk = theColumnSync.getColIsChk();
	}
	
	private  void addColumn(String label, boolean show,  Class<?> type, String sqlTable, String sqlCol) {
		if (numModeCols==numMaxCols) ErrorReport.die("GeneColumns increase numMaxCols");
		
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
	 public JScrollPane createColumnPanel(GeneTable table) {   
 		boolean hasTotal = theMetaData.hasReadCnt();
 		
 		theTable = table;
 		columnChange = new ActionListener() {
			public void actionPerformed(ActionEvent arg0)  {
				columnChange();
			}
		};
		allColChks = new JCheckBox[numModeCols];
		
	    JPanel page = CreateJ.panelPage(); 
	    JPanel row;
	
	// add static columns
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
		CreateJ.addColumnRows(0, startLibCols, page, allColChks, 
				allColLabels, allColIsChk, columnChange, rowBreaks);	
	//---------------------------------------------------
	// Groups
		page.add(new JSeparator());
		page.add(Box.createVerticalStrut(5));
		
		row = CreateJ.panelTextLine("Library Group Selection (select at least one of each)");	
		row.add(Box.createHorizontalStrut(5));
		btnApplySelecton = CreateJ.buttonApply();
		btnApplySelecton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				columnApply();
			}
		});
		row.add(btnApplySelecton);
		page.add(row);
		row.add(Box.createHorizontalStrut(20));
		page.add(Box.createVerticalStrut(5));
		
		// Tissues and strains
		String [] tissueAbbr = theViewerFrame.getMetaData().getTisAbbv();
		String [] strainAbbr = theViewerFrame.getMetaData().getStrAbbv();
		int numCol = (hasCond2) ? tissueAbbr.length + strainAbbr.length : strainAbbr.length;
		libChks = new JCheckBox [numCol];
		
		row = CreateJ.panelTextLine("  " + Globals.cond1 + ":", 60);
		CreateJ.addToColumnRow(row, 0, libChks, strainAbbr, null);
		page.add(row);
		page.add(Box.createVerticalStrut(1));
		
		if (hasCond2) {
			row = CreateJ.panelTextLine("  " + Globals.cond2 + ":",60);
			CreateJ.addToColumnRow(row, strainAbbr.length, libChks, tissueAbbr, null);
			page.add(row);
			page.add(Box.createVerticalStrut(1));
		}
		
		// Type groups: 	
		Vector <String> abbr = new Vector <String> ();
		abbr.add("SNP Ratio"); 
		abbr.add("SNP Score");
		abbr.add("SNP Pval");
		if (hasTotal) {
			abbr.add(Globals.COUNT2+" Ratio"); 
			abbr.add(Globals.COUNT2+" Score");
			abbr.add(Globals.COUNT2+" Pval");
		}
	
		String [] typeAbbr = abbr.toArray(new String[0]);	
		typeChks = new JCheckBox [typeAbbr.length];
		typeIndices = new int [typeAbbr.length+1]; 
		
		row = CreateJ.panelTextLine("  RefAlt:", 60);
		CreateJ.addToColumnRow(row, 0, typeChks, typeAbbr, null);
		page.add(row);
		page.add(Box.createVerticalStrut(3));	
		
		// XXX
		initLibChks(); // sets the libChks based on the syncColumns
		
		//-----------------------------------------------------
	// Ref:Alt: dynamic ratio columns
		page.add(new JSeparator());
		page.add(Box.createVerticalStrut(5));
		page.add(new JLabel("Library Ref:Alt Selection"));	
		page.add(Box.createVerticalStrut(10));
		
		int ti=0;
		page.add(new JLabel(" SNP Ratio"));
	   	int offset = startLibCols;
	   	CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
	   			allColLabels, allColIsChk,columnChange);
	   	typeIndices[ti++] = offset;
	  	
	   	page.add(new JLabel(" SNP Score"));
		offset += libNames.length;
		CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
				allColLabels, allColIsChk, columnChange);
		typeIndices[ti++] = offset;
		
		page.add(new JLabel(" SNP Pval"));
		offset += libNames.length;
		CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
				allColLabels, allColIsChk, columnChange);
		typeIndices[ti++] = offset;

		if (hasTotal) {
			page.add(new JLabel(" " + Globals.COUNT2 + " Ratio"));
			offset += libNames.length;
		   	CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
		   			allColLabels, allColIsChk,columnChange);
			typeIndices[ti++] = offset;
		   	
		 	page.add(new JLabel(" " +Globals.COUNT2 + " Score"));
			offset += libNames.length;
			CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
					allColLabels, allColIsChk, columnChange);
			typeIndices[ti++] = offset;
	
		 	page.add(new JLabel(" " +Globals.COUNT2 + " Pval"));
			offset += libNames.length;
			CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
					allColLabels, allColIsChk, columnChange);	
			typeIndices[ti++] = offset;
		}
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
	public JScrollPane createColumnPanel1(GeneTable table) {   
		theTable = table;
		columnChange = new ActionListener() {
			public void actionPerformed(ActionEvent arg0)  {
				columnChange();
			}
		};
		allColChks = new JCheckBox[numModeCols];
		
	    	JPanel page = CreateJ.panelPage(); 
	    	JPanel row;
	
	    	// add static columns
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
		CreateJ.addColumnRows(0, startLibCols, page, allColChks, 
				allColLabels, allColIsChk, columnChange, rowBreaks);	
			
		// Ref:Alt: dynamic ratio columns
		page.add(new JSeparator());
		page.add(Box.createVerticalStrut(5));
		page.add(new JLabel("Library RefAlt Selection"));	
		page.add(Box.createVerticalStrut(10));
		
		page.add(new JLabel(" SNP Ratio"));
	   	int offset = startLibCols;
	   	CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
	   			allColLabels, allColIsChk,columnChange);
	  	
	   	page.add(new JLabel(" SNP Score"));
		offset += libNames.length;
		CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
				allColLabels, allColIsChk, columnChange);
		
		page.add(new JLabel(" SNP Pvalue"));
		offset += libNames.length;
		CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
				allColLabels, allColIsChk, columnChange);

		if (hasTotals) {
			page.add(new JLabel(" " + Globals.COUNT2 + " Ratio"));
			offset += libNames.length;
		   	CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
		   			allColLabels, allColIsChk,columnChange);
		   	
		 	page.add(new JLabel(" " + Globals.COUNT2 + " Score"));
			offset += libNames.length;
			CreateJ.addColumnRows(offset, libNames.length, page, allColChks, 
					allColLabels, allColIsChk, columnChange);	
		}
		page.setBorder(BorderFactory.createLineBorder(Globals.COLOR_BORDER));
	    	page.setMaximumSize(page.getPreferredSize());
	    	
	    	JScrollPane sPane =  new JScrollPane (page);
 		sPane.setBorder( null );
 		sPane.setPreferredSize(theViewerFrame.getSize());
 		sPane.getVerticalScrollBar().setUnitIncrement(15);
 		sPane.setAlignmentX(Component.LEFT_ALIGNMENT);
 		sPane.setAlignmentX(Component.LEFT_ALIGNMENT);
	 		
	    	return sPane;	
   }
	 /** 
     * Called by GeneTable for columns labels to display 
     * **/
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
  		
	private void setLastSelectedColumns() {
		String [] columns = getSelectedColumns();
		if(columns == null || columns.length == 0) return;
		
		Vector<String> sels = new Vector<String> ();	 
   		for(int x=0; x<columns.length; x++) sels.add(columns[x]);
   		for(int x=0; x<allColLabels.length; x++) 
   			allColIsChk[x] = sels.contains(allColLabels[x]);	 
   		theColumnSync.setColIsChk(allColIsChk);
	}
		
   // called by TransTablePanel on Clear
   public void setClearColumns() {
   		for (int x=0; x<allColChks.length; x++) 
   			allColChks[x].setSelected(false);
   		allColChks[2].setSelected(true);
   		columnChange();
   }   
   /**************************************************
  	 * XXX setSyncColumns: called when the panel is created to set 
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
  		if (type!=null) {
  			for (int i=0; i<typeChks.length; i++) 
  				typeChks[i].setSelected(type[i]);
  		}
  	 }
   /******************************************
    * Apply Selection button 
    * XXX 
    */
   private void columnApply() {
   		syncAbbr = new Vector <String> ();
		for (int j=0;  j < libChks.length; j++) {
			JCheckBox libBox = libChks[j];
			if (libBox.isSelected()) syncAbbr.add(libBox.getText());
		}
		theColumnSync.setAbbr(syncAbbr);
		
		syncLibs = new Vector <String> ();
		
		// this doesn't depend on unique prefix/suffix
		if (hasCond2) {
			Vector <String> candLib = new Vector <String> ();
			for (int i=0; i<syncAbbr.size()-1; i++) {
				for (int j=i+1; j<syncAbbr.size(); j++) {
					candLib.add(syncAbbr.get(i) + syncAbbr.get(j));
					candLib.add(syncAbbr.get(j) + syncAbbr.get(i));
				}
			}
			for (String lib : libNames) {						
				for (String clib : candLib) {
					if (lib.equals(clib)) {
						syncLibs.add(lib);
						break;
					}
				}
			}
		}
		else {
			for (String lib : libNames) {						
				for (String name : syncAbbr) {
					if (lib.equals(name)) {
						syncLibs.add(lib);
						break;
					}
				}
			}
		}
		theColumnSync.setLibs(syncLibs);
		boolean [] type = new boolean [Globals.numType]; 
		for (int i=0; i<Globals.numType; i++) type[i]=false;
		
   		for (int i=startLibCols; i<allColChks.length; i++) {
   			JCheckBox colBox = allColChks[i];
   			colBox.setSelected(true); // true until find an corresponding unchecked box
   			boolean isSel=true;
   			
   			// check libChks (where the syncLibs is already updated)
   			String libCol = colBox.getText().substring(Globals.LIB_PREFIX); // remove prefix
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
		
   private boolean hasCond2;
   private String [] libNames = null;
   private boolean hasTotals;
	// the indexes on these four arrays are in sync
	private JCheckBox[]  allColChks = null;
	private String  [] allColLabels = null;
	private boolean [] allColIsChk = null;
	private boolean [] allColDefaults = null;
	private JCheckBox [] libChks = null;
	private JCheckBox [] typeChks = null;
	private int [] typeIndices = null; // for Ratio, etc
	
	private JButton btnApplySelecton=null;
	private JButton btnClearColumns=null;
	private Vector <Integer> rowBreaks = null;
	private ActionListener columnChange = null;
	
	Vector <String> syncLibs = null;
	Vector <String> syncAbbr = null;
	
	private ViewerFrame theViewerFrame = null;
	private MetaData theMetaData = null;
	private GeneTable theTable = null;
	private ColumnSync theColumnSync = null;
	private ColumnData theColumnData = null;
}
