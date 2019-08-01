package viewer.panels;

import java.io.FileWriter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.StringReader;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import util.Align;
import util.ErrorReport;
import util.Globals;
import util.LogTime;
import viewer.ViewerFrame;
import viewer.controls.CreateJ;
import viewer.controls.FileMethods;
import viewer.controls.MultiButton;
import viewer.controls.ViewTextPane;
import viewer.table.ColumnData;
import viewer.table.SortTable;
import viewer.table.TableData;
import database.DBConn;

public class TransTable extends JPanel {
	private static final long serialVersionUID = -12486209145350722L;
	private static final String htmlFile = "html/TransTable.html";
	
	/**********************************************************
	 * Called from SNPtable, GENEtable or TransTable; inherits libSync columns
	 */
	public TransTable(int mode, ViewerFrame pF, String tab, String query, String sum) {
		try {		
			theViewerFrame = pF;
			strTabName = tab;
			strQueryWhereSQL = query;
			strQuerySummary = sum;
					
			theColumn = new TransColumn(pF);
			theColumnScrollPane = theColumn.createColumnPanel(this);
			setName("Trans");
			
			executeSQL_InitTable();	
		} catch (Exception e) {ErrorReport.die(e, "transtable"); }
	}

	// called when a table is made, column is selected, or Clear or Sync
	public void columnChange() {
		createTable(false);
		createTablePanel();	
	}
	public TableData getTableData() {return theTableData;}
	public JTable getJTable() {return theJTable;} // XXX
	 /****************************************************
     * specific button panels
     */
    private JPanel createTableButtonPanel() {
	    	JPanel row1 = createTopButtons();
	    	
	    	JPanel buttonPanel = CreateJ.panelPage();
        buttonPanel.add(row1);
        
        return buttonPanel;
    }
    private JPanel createTopButtons() {
    		JPanel row1 = CreateJ.panelLine();
      	row1.add(new JLabel("Show selected: "));
      	row1.add(Box.createHorizontalStrut(5));   	
     
        btnShowLibraries = CreateJ.buttonFun("Libs");
        btnShowLibraries.setEnabled(false);
        btnShowLibraries.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try {
					int IDidx = theTableData.getColumnHeaderIndex(Globals.TRANSSQLID);
			 		int Nameidx = theTableData.getColumnHeaderIndex(Globals.TRANSNAME);
			 		String queryOn = "transLib.TRANSid"; 
			 		
					LibListTable newPanel = new LibListTable(theViewerFrame, theTableData, 
							theJTable.getSelectedRows(),
							IDidx, Nameidx, queryOn, Globals.MODE_TRANS,
							theColumn.getChkLibs());
					theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getName(), 
							newPanel.getSummary());
				} catch(Exception e) {
					ErrorReport.prtError(e, "Error showing Libraries");
				} catch(Error e) {
					ErrorReport.reportFatalError(e);
				}
			}
    		});
    		row1.add(btnShowLibraries);
    		row1.add(Box.createHorizontalStrut(5));
    	
    		btnShowSNPs = CreateJ.buttonFun("Variants");
        btnShowSNPs.setEnabled(false);
        btnShowSNPs.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try {
					int IDidx = theTableData.getColumnHeaderIndex(Globals.TRANSSQLID);
			 		int Nameidx = theTableData.getColumnHeaderIndex(Globals.TRANSNAME);
			 		int startIdx = theTableData.getColumnHeaderIndex(Globals.START); 
			 		
			 		String sumAdd=makeSummaryAddition();
			 		theViewerFrame.getSNPColumnSync().setAbbr(theColumn.getChkAbbr());
			 		theViewerFrame.getSNPColumnSync().setLibs(theColumn.getChkLibs());
					SNPTable newPanel = new SNPTable(theViewerFrame, theTableData, 
							theJTable.getSelectedRows(),
							IDidx, Nameidx,  startIdx, Globals.MODE_TRANS, sumAdd);
					theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getName(), 
							newPanel.getSummary());
				} catch(Exception e) {
					ErrorReport.prtError(e, "Error showing Trans SNPs");
				} catch(Error e) {
					ErrorReport.reportFatalError(e);
				}
			}
    		});
    		row1.add(btnShowSNPs);
    		row1.add(Box.createHorizontalStrut(5));
    		
    		btnShowExons = CreateJ.buttonFun("Exons");
        btnShowExons.setEnabled(false);
        btnShowExons.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try {
					int IDidx = theTableData.getColumnHeaderIndex(Globals.TRANSSQLID);
			 		int Nameidx = theTableData.getColumnHeaderIndex(Globals.TRANSNAME);
			 		int startIdx = theTableData.getColumnHeaderIndex(Globals.START); 
			 		
			 		String sumAdd=makeSummaryAddition();
					ExonTable newPanel = new ExonTable(theViewerFrame, theTableData, theJTable.getSelectedRows(),
							IDidx, Nameidx, startIdx, sumAdd);
					theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getName(), 
							newPanel.getSummary());
				} catch(Exception e) {
					ErrorReport.prtError(e, "Error showing Exons");
				} catch(Error e) {
					ErrorReport.reportFatalError(e);
				}
			}
    		});
    		row1.add(btnShowExons);
    		row1.add(Box.createHorizontalStrut(5));
    		
    		btnShowDraw = CreateJ.buttonFun("Draw");
            btnShowDraw.setEnabled(false);
            btnShowDraw.addActionListener(new ActionListener() {
    				public void actionPerformed(ActionEvent arg0) {
    					try {
    						if (theJTable.getSelectedRows().length==1)
    						{
    							int row = theJTable.getSelectedRows()[0];
    							int IDidx = theTableData.getColumnHeaderIndex(Globals.GENESQLID);
    					 	
    					 		long lgIdx = (Long)theTableData.getValueAt(row, IDidx);
    					 		int gIdx = (int)lgIdx;
    					 	
    					 		GeneDraw drawPanel = new GeneDraw(theViewerFrame,gIdx);
    							theViewerFrame.addResultPanel(getInstance(), drawPanel, drawPanel.getName(), 
    									drawPanel.getSummary());
    									
    						}
    					} catch(Exception e) {
    						ErrorReport.prtError(e, "Error drawing gene and transcripts");
    					} 
    				}
        		});
        	row1.add(btnShowDraw);
        	row1.add(Box.createHorizontalStrut(5));
        		
    		btnShowAlign = CreateJ.buttonFun("Align");
        btnShowAlign.setEnabled(false);
        btnShowAlign.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				exportSequence(null); // null indicates write to stdout
			}
    		});
        if (theViewerFrame.getMetaData().hasAAseqs()) {
    			row1.add(btnShowAlign);
    			row1.add(Box.createHorizontalStrut(20));
        }
        createExportButton();
        row1.add(btnExport);
        row1.add(Box.createHorizontalStrut(5)); 
        
        createCopyTransButton();
		btnCopyClipboard.setToolTipText(toolTip);
    		row1.add(btnCopyClipboard);
    		row1.add(Box.createHorizontalStrut(5)); 
        
        JButton btnHelp = new JButton("Help");
        btnHelp.setBackground(Globals.COLOR_HELP);
        btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				ViewTextPane.displayHTML(theViewerFrame, "Trans Table Help", htmlFile);
			}
		});
        row1.add(btnHelp);
        row1.add(Box.createHorizontalStrut(30)); // keep from truncating Help with click through options
        
        row1.setMaximumSize(row1.getPreferredSize());
        return row1;
    }
    private String makeSummaryAddition() {
    		String sumAdd="";
 		if (theJTable.getSelectedColumnCount()==1) {
 			int [] rows = theJTable.getSelectedRows();
			int start = theTableData.getColumnHeaderIndex(Globals.START);
 			int end = theTableData.getColumnHeaderIndex(Globals.END);
 			int cd1st = theTableData.getColumnHeaderIndex(Globals.TRANSSTARTC);
 			int cdLast = theTableData.getColumnHeaderIndex(Globals.TRANSENDC);
 			int strand = theTableData.getColumnHeaderIndex(Globals.TRANSTRAND);
 			DecimalFormat df = new DecimalFormat ();
 			df.applyPattern( "###,###,###" );
 			sumAdd = " (" + theTableData.getValueAt(rows[0], strand) +
 					" Start=" + 	df.format(theTableData.getValueAt(rows[0], start)) +
 					 " End=" + 		df.format(theTableData.getValueAt(rows[0], end)) +
 					 "   ATG=" + df.format(theTableData.getValueAt(rows[0], cd1st)) +
 					 " Stop=" + df.format(theTableData.getValueAt(rows[0], cdLast)) + ")";
		}
 		return sumAdd;
    }
    private void createCopyTransButton() {	
     	btnCopyClipboard  = new MultiButton(Globals.COLOR_BG);
     	btnCopyClipboard.setEnabled(false);
        
        final String [] TRANS = {"Copy " + Globals.TRANSNAME + "...", 
        		"Copy " + Globals.TRANSIDEN + "...",
        		"Copy Ref Pro...", "Copy Alt Pro" + "..."};
        for (int i=0; i<TRANS.length; i++) btnCopyClipboard.addLabel(TRANS[i]);	  
    	
        btnCopyClipboard.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				String column="";
				int parent = 0;
				
				if (btnCopyClipboard.getText().equals(TRANS[0])) column=Globals.TRANSNAME;
				else if (btnCopyClipboard.getText().equals(TRANS[1])) column=Globals.TRANSIDEN;
				else if (btnCopyClipboard.getText().equals(TRANS[2])) parent = 1;
				else parent = 2;
				
				int row = theJTable.getSelectedRow();
				Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
				if (parent == 0) {
					cb.setContents(new StringSelection((String)theTableData.
							getValueAt(row, theTableData.getColumnHeaderIndex(column))), null);
				}
				else {
					try {
						int col=theTableData.getColumnHeaderIndex(Globals.TRANSSQLID);
						Long TRANSid = (Long) theTableData.getValueAt(row, col);
						DBConn mDB = ViewerFrame.getDBConnection();
						ResultSet rs = mDB.executeQuery("Select seq from sequences WHERE TRANSid=" 
								+ TRANSid + " and parent=" + parent);
						String seq="";
						if (rs.next()) seq = rs.getString(1);
						else if (parent==2) {
							rs = mDB.executeQuery("Select seq from sequences WHERE TRANSid=" 
									+ TRANSid + " and parent=1");
							if (rs.next()) seq = rs.getString(1);
						}
						if (seq.equals("")) LogTime.infoBox("No sequence in database");
						else {
							cb.setContents(new StringSelection(seq), null);
						}
					}
					catch (Exception e) {ErrorReport.prtError(e, "Copy seq");}
				}
			}
		});
    	
        btnCopyClipboard.addMouseListener(new MouseAdapter() {
	    		public void mousePressed(MouseEvent e) {
	            }
	    	});
    }
  //Needed for setTable, cannot use 'this' in a thread
  	private TransTable getInstance() { return this; }
	/**********************************************************
	 * QUERY
	 */
	private String getSQLquery() {
		ColumnData theFields = theColumn.getColumnSQLMap();
				
        try {
	        	String strQuery = "SELECT " + theFields.getDBColumnQueryList() + " FROM trans  ";
	       
	        	if (strQueryWhereSQL.length() > 0) 
        			strQuery += " WHERE " + strQueryWhereSQL;     
	      
	        	if(loadStatus.getText().equals("Cancelled")) return null;       	
	        	loadStatus.setText("Loading from database");
	 
	        	return strQuery;
        } catch(Exception e) {
        		ErrorReport.prtError(e, "Error processing query");
        		return null;
        } catch(Error e) {
        		ErrorReport.reportFatalError(e);
        		return null;
        }
	}
	
    /****************************************************
	 * Build the master table
	 * this can probably be made into a class under viewer.table
	 * where rset, label, theFields, buttonPanel, etc are passed in. 
	 */
    private void buildTable(ResultSet rset) {
        ColumnData theFields = theColumn.getColumnSQLMap();
              
        tableButtonPanel = createTableButtonPanel();
		tableStatusPanel = createTableStatusPanel();	
		tableSummaryPanel = createTableSummaryPanel();
		
		// created when trans object is created
		theColumnScrollPane.setVisible(false);
		
		//Create toggle for displaying column select
		showColumnPanel = CreateJ.button("Select");
		showColumnPanel.setBackground(Globals.COLOR_COLUMNS);
		showColumnPanel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(theColumnScrollPane.isVisible()) {
					showColumnPanel.setText("Select");
					theColumnScrollPane.setVisible(false);
				}
				else {
					showColumnPanel.setText("Hide");
					theColumnScrollPane.setVisible(true);
				}
				createTablePanel();
			}
		});
		
		txtStatus = new JTextField(100);
		txtStatus.setBorder(BorderFactory.createEmptyBorder());
		txtStatus.setEditable(false);
		txtStatus.setBackground(Globals.COLOR_BG);

        dblClick = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
			}
		};	
		// called when row selected
		sngClick = new ActionListener() {
			public void actionPerformed(ActionEvent e) {

			}
		};		
		// called when row selected
		selListener = new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent arg0) {
				boolean rowsSelected = false;
				if(theJTable.getSelectedRowCount() > 0 && theJTable.getSelectedRowCount() < 1000) 
					rowsSelected = true;
				if(theJTable.getSelectedRowCount() >= 1000)
					txtStatus.setText("Cannot select more than 1000 rows at a time");
				else
					txtStatus.setText("");
				
				if(theJTable.getSelectedRowCount() == 1 && btnCopyClipboard != null)
					btnCopyClipboard.setEnabled(true);
				else btnCopyClipboard.setEnabled(false);
			
				btnShowLibraries.setEnabled(rowsSelected);
				btnShowSNPs.setEnabled(rowsSelected);
				btnShowExons.setEnabled(rowsSelected);
				btnShowAlign.setEnabled(rowsSelected);
				btnShowDraw.setEnabled(rowsSelected);
			}
		};

        theTableData = new TableData(this);
        theTableData.setColumnHeaders(theFields.getDisplayColumns(), theFields.getDisplayTypes());
        theTableData.addRowsWithProgress(rset, theFields.getDisplayColumnsSymbols(), loadStatus);
        theTableData.finalize();
        
        //Update the menu and query results
        String status = "";
        if(theTableData.getNumRows() == 0) status = "No trans";
        else if(theTableData.getNumRows() == 1) status = "1 trans";
        else status = theTableData.getNumRows() + " trans";
        
        rowCount.setText(strTabName + status);
        theViewerFrame.changePanelName(this, strTabName + status);
    }	
	
	/****************************************************
	 * Initialization for all constructors
	 */
	private void executeSQL_InitTable() {
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setAlignmentY(LEFT_ALIGNMENT);

        showProgress();
        //Has to be run on a thread 
		buildThread = new Thread(new Runnable() {
			public void run() {
				try {
					// Perform the query
					String sql = getSQLquery();
					DBConn mDB = ViewerFrame.getDBConnection();
					ResultSet rset = ViewerFrame.executeQuery(mDB, sql, loadStatus);
					if(rset != null) {
						buildTable(rset);				
						//Thread safe way of closing the resultSet
						ViewerFrame.closeResultSet(rset);
						mDB.close();

						createTable(true);
					}
					createTablePanel();
					//Makes the table appear (A little hacky, but fast)
					if(isVisible()) {
						setVisible(false);
						setVisible(true);
					}
				} catch (Exception e) {
					ErrorReport.prtError(e, "Error generating list");
				} catch (Error e) {
					ErrorReport.reportFatalError(e);
				}
			}
		});
		buildThread.setPriority(Thread.MIN_PRIORITY);
		buildThread.start();
	}

	//When a column is selected/removed this is called to set the new model
	private void createTable(boolean firstTime) {

		if(theJTable != null) {
			theJTable.removeListeners(); //If this is not done, the old table stays in memory
		}	
		String [] columns;
		
		if (firstTime) columns = theColumn.getOrderedColumns(); // XXX
		else columns = TableData.orderColumns(theJTable, theColumn.getSelectedColumns());
		
		theJTable = new SortTable(TableData.createModel(columns, theTableData, this));
		// CAS 1/31/14 catch column moved for syncing
		// not perfect because just catches at start of move, so may not sync it right,
		// but usually gets it
		TableColumnModelListener tableColumnModelListener = new TableColumnModelListener() {
			int lastFrom=0, lastTo=0;
			public void columnMoved(TableColumnModelEvent e) {
				 if (e.getFromIndex() != lastFrom || e.getToIndex() != lastTo) {
			         lastFrom = e.getFromIndex();
			         lastTo = e.getToIndex();
			         theColumn.columnMoved(theJTable);
				 }
		    }
			public void columnAdded(TableColumnModelEvent e) {}
			public void columnMarginChanged(ChangeEvent e) {}
			public void columnRemoved(TableColumnModelEvent e) {}
			public void columnSelectionChanged(ListSelectionEvent e) {}
		};
		theJTable.getColumnModel().addColumnModelListener(tableColumnModelListener);
		
		//Set the attributes/interface
        theJTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        theJTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        theJTable.autofitColumns();
        	
        theJTable.getSelectionModel().addListSelectionListener(selListener);
		theJTable.addSingleClickListener(sngClick);
        theJTable.addDoubleClickListener(dblClick);

        theJTable.setTableHeader(new SortHeader(theJTable.getColumnModel()));
        
        //If a header contains a '\n' multiple lines will appear using this renderer
        MultiLineHeaderRenderer renderer = new MultiLineHeaderRenderer();
        Enumeration<TableColumn> en = theJTable.getColumnModel().getColumns();
        while (en.hasMoreElements()) {
          ((TableColumn)en.nextElement()).setHeaderRenderer(renderer);
        } 
	}
	
    //When the view table gets sorted, sort the master table to match (Called by TableData)
    public void sortMasterColumn(String columnName) {
    		int index = theTableData.getColumnHeaderIndex(columnName);
    		theTableData.sortByColumn(index, !theTableData.isAscending(index));
    }
    
    //Builds progress panel while data is being loaded
    private void showProgress() {
	    	removeAll();
	    	repaint();
	    	setBackground(Color.WHITE);
	    	loadStatus = new JTextField(40);
	    	loadStatus.setBackground(Color.WHITE);
	    	loadStatus.setMaximumSize(loadStatus.getPreferredSize());
	    	loadStatus.setEditable(false);
	    	loadStatus.setBorder(BorderFactory.createEmptyBorder());
	    	JButton btnStop = new JButton("Stop");
	    	btnStop.setBackground(Color.WHITE);
	    	btnStop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if(buildThread != null)
					loadStatus.setText("Cancelled");
			}
		});
        add(loadStatus);
        add(btnStop);
        validateTable();
    }
    
    private void createTablePanel() {
	    	removeAll();
	    	repaint();
	    	loadStatus = null;
	    	theTableScrollPane = new JScrollPane();
	    	theTableScrollPane.setViewportView(theJTable);
	    	theJTable.getTableHeader().setBackground(Color.WHITE);
	    	theTableScrollPane.setColumnHeaderView(theJTable.getTableHeader());
	    	theTableScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
	    	
	    	theTableScrollPane.getViewport().setBackground(Color.WHITE);
	    	theTableScrollPane.getHorizontalScrollBar().setBackground(Color.WHITE);
	    	theTableScrollPane.getVerticalScrollBar().setBackground(Color.WHITE);
	    	theTableScrollPane.getHorizontalScrollBar().setForeground(Color.WHITE);
	    	theTableScrollPane.getVerticalScrollBar().setForeground(Color.WHITE);
	    	
	    	//Is null if a sample table
	    	if(tableButtonPanel != null) {
	    		add(tableButtonPanel);
	    		add(Box.createVerticalStrut(10));
	    	}
	    	add(Box.createVerticalStrut(10));
	    	add(tableSummaryPanel);
	    	add(Box.createVerticalStrut(10));
	    	add(tableStatusPanel);
	    	add(theTableScrollPane);
	    		    	
	    	// Columns: Hide/Select
	    	add(Box.createVerticalStrut(5));
	    	JPanel bottomButtons = CreateJ.panelLine();
	    	bottomButtons.add(new JLabel("Columns:"));
	    bottomButtons.add(Box.createHorizontalStrut(5));
	    	bottomButtons.add(showColumnPanel);
	    	bottomButtons.add(Box.createHorizontalStrut(100));
	   
	    	bottomButtons.add(txtStatus);
	    	bottomButtons.setMaximumSize(bottomButtons.getPreferredSize());   	
	    	add(bottomButtons);
	    add(Box.createVerticalStrut(5));
	    
	  	add(theColumnScrollPane);
	  	    	
	    	if(theJTable != null) tableType.setText("Trans View");
	
	    	invalidate();
	    	validateTable();
    }
    /***********************************************************
     * Export
     */
    private void createExportButton() {
        btnExport = new JButton("Export...");
        btnExport.setAlignmentX(Component.LEFT_ALIGNMENT);    	
        btnExport.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try{
					final ExportType et = new ExportType();
					et.setVisible(true);
					
					int saveMode = et.getSelection();
					if (saveMode==et.TABLEROWS)
						FileMethods.writeDelimFile(theViewerFrame, theJTable, "\t");
					else if (saveMode==et.SEQ)
						exportSequence("Sequences");
					else if (saveMode==et.ALIGN)
						exportSequence("Align");
					else System.out.println("Cancel Export");
				} catch(Exception e) {
					ErrorReport.prtError(e, "Error saving file");
				} catch(Error e) {
					ErrorReport.reportFatalError(e);
				}
			}
		});        
	    	btnExport.addMouseListener(new MouseAdapter() {
	    		public void mousePressed(MouseEvent e) {
	            }
	    	});
    }
    /*****************************************************
     * This is used to Align two sequences and show in pop-up
     * And to export many parental sequences or parental alignments
     */
    private void exportSequence(String label) {
		try {
			FileWriter fw = null;
			boolean append=false;
			if (label != null) {
				fw = FileMethods.appendFile(theViewerFrame);
				append = FileMethods.append;
			}
			DBConn mDB = ViewerFrame.getDBConnection();
			ResultSet rs;
	    		String content="", name="";
	    		if (label!=null) content="###" + strQuerySummary+"\n";
	    		int [] rows = theJTable.getSelectedRows();
	    		int numRows=rows.length;
	    		if (rows.length==0) {
	    			numRows = theJTable.getRowCount();
	    			rows = new int [numRows];
	    			for (int i=0; i<numRows; i++) rows[i]=i;
	    		}
	    		if (label != null) System.out.println("Export " + label + " "+ numRows);
	    		int colID=theTableData.getColumnHeaderIndex(Globals.TRANSSQLID);
	    		int colName = theTableData.getColumnHeaderIndex(Globals.TRANSNAME);
	    		for (int i=0; i<numRows; i++) {
				int index = (rows.length==0) ? i : rows[i];
				Long TRANSid = (Long) theTableData.getValueAt(index, colID);
				name = (String) theTableData.getValueAt(index, colName);
				String cntMiss="", cntDam="", cntIndel="", nRef="", nAlt="", nDiff="";
				Vector <String> seq = new Vector <String> ();
			
				rs = mDB.executeQuery("Select seq from sequences WHERE TRANSid=" + TRANSid + " and parent>0");
				while (rs.next()) seq.add(rs.getString(1));
				if (seq.size() == 1) seq.add(seq.get(0)); // they are the same, so only one seq in database 
				
				rs = mDB.executeQuery("Select cntMissense, cntDamage, cntIndel, refProLen, altProLen, nProDiff " +
						"from trans WHERE TRANSid=" + TRANSid);
				if (rs.next()) {
					cntMiss= rs.getString(1);
					cntDam= rs.getString(2);
					cntIndel= rs.getString(3);
					nRef= rs.getString(4);
					nAlt= rs.getString(5);
					nDiff= rs.getString(6);
				}
			
				if (seq.size()==0) {
					if (fw!=null) ViewTextPane.displayInfo("Align " + name, "No sequences for " + name, false); 
					System.err.println("No sequences for this transcript - " + name);
				}
				else {
					content += String.format(
							"> %-10s Missense=%-3s Damaged=%-3s Indel=%-3s RefLen=%-5s AltLen=%-5s Diff=%s\n", 
							name, cntMiss, cntDam, cntIndel, nRef, nAlt, nDiff);
					if (seq.size()==1) seq.add(seq.get(0));
					
					if (label != null && label.startsWith("Seq")) { // sequences
						content += seq.get(0) + "\n> Alt\n" + seq.get(1) +"\n";
					}
					else {											// alignment
						Align alignObj = new Align();
						content += alignObj.getAlign(seq.get(0), seq.get(1)) + "\n";
					}
					if (fw!=null) {
						if (append) fw.append(content);
						else fw.write(content);
						content = "";
						if (i%10 == 0) System.out.print("  exported " + i + "\r");
					}
				}		
	    		}
	    		if (fw!=null) {
	    			fw.close();
	    			System.out.println("Complete  export " + numRows);
	    		}
	    		else {
	    			if (!content.equals("")) 
	    				ViewTextPane.displayInfo("Align", content, false);
	    			else 
	    				ViewTextPane.displayInfo("Align", "No sequences for selected set", false);
			}
		}
		catch (Exception e) {ErrorReport.prtError(e, "In download");}
	} 
    /******************************************
     * export popup
     */
    private class ExportType extends JDialog {
    		
		private static final long serialVersionUID = 3516263191127357070L;
		public ExportType() {
			setModal(true);
			setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
			setTitle("Export.... ");
			
			ButtonGroup grp = new ButtonGroup();
			JRadioButton btnTableTab = new JRadioButton("Table of columns (.tab)");
			btnTableTab.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					nMode=TABLEROWS;
				}
			});
			grp.add(btnTableTab);
			
			JRadioButton btnSeq = new JRadioButton("Parental proteins (.fasta)");
			btnSeq.addActionListener(new ActionListener() {
		        public void actionPerformed(ActionEvent arg0) {
		            nMode=SEQ;
		        }
		    });
			grp.add(btnSeq);
			
			JRadioButton btnAlign = new JRadioButton("Alignment of parental proteins");
			btnAlign.addActionListener(new ActionListener() {
		        public void actionPerformed(ActionEvent arg0) {
		            nMode=ALIGN;
		        }
		    });
			grp.add(btnAlign);
			
			JButton btnOK = new JButton("OK");
			btnOK.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					setVisible(false);
				}
			});
			JButton btnCancel = new JButton("Cancel");
			btnCancel.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					nMode = -1;
					setVisible(false);
				}
			});
			
			btnOK.setPreferredSize(btnCancel.getPreferredSize());
			btnOK.setMaximumSize(btnCancel.getPreferredSize());
			btnOK.setMinimumSize(btnCancel.getPreferredSize());
	        
			JPanel selectPanel = new JPanel();
			selectPanel.setLayout(new BoxLayout(selectPanel, BoxLayout.PAGE_AXIS));
			selectPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			
	        selectPanel.add(btnTableTab);
	        selectPanel.add(Box.createVerticalStrut(5));
	        
			selectPanel.add(new JLabel("For selected rows or all rows:"));
			selectPanel.add(Box.createVerticalStrut(5));
	        selectPanel.add(btnSeq);
	    		selectPanel.add(Box.createVerticalStrut(5));
	    		selectPanel.add(btnAlign);
	    		selectPanel.add(Box.createVerticalStrut(5));
	 
			JPanel buttonPanel = new JPanel();
			buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
			buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			buttonPanel.add(btnOK);
			buttonPanel.add(Box.createHorizontalStrut(20));
			buttonPanel.add(btnCancel);
			buttonPanel.setMaximumSize(buttonPanel.getPreferredSize());
	
	   		JPanel mainPanel = new JPanel();
			mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
			mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
	
			mainPanel.add(selectPanel);
			mainPanel.add(Box.createVerticalStrut(15));
			mainPanel.add(buttonPanel);
			
			mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
			add(mainPanel);
			
			pack();
			this.setResizable(false);
			centerScreen(this);
		}
	 	public int getSelection() { return nMode; }
	 	private int nMode=-1;
	 	
	 	int SEQ = 1;
	 	int ALIGN = 2;
	 	int TABLEROWS = 3;
    }
    static public void centerScreen( Window win ) 
    {
          Dimension dim = win.getToolkit().getScreenSize();
          Rectangle abounds = win.getBounds();
          win.setLocation((dim.width - abounds.width) / 2,
              (dim.height - abounds.height) / 2);
    }
  
    /*******************************************************
     * Parts of table
     */
    private JPanel createTableSummaryPanel() {
	    	JPanel thePanel = new JPanel();
	    	thePanel.setLayout(new BoxLayout(thePanel, BoxLayout.LINE_AXIS));
	    	thePanel.setBackground(Globals.COLOR_BG);
	    	thePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
	
	    	lblSummary = new JLabel(strQuerySummary);
	    	lblSummary.setMaximumSize(lblSummary.getPreferredSize());
	    	lblSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
	    	lblSummary.setFont(getFont());
	    	
	    	JLabel header =new JLabel("Summary: ");
	    	
	    	thePanel.add(header);
	    	thePanel.add(lblSummary);
	    	
	    	return thePanel;
    }
    
    private JPanel createTableStatusPanel() {
	    	JPanel thePanel = new JPanel();
	    	thePanel.setLayout(new BoxLayout(thePanel, BoxLayout.LINE_AXIS));
	    	thePanel.setBackground(Globals.COLOR_BG);
	    	thePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
	
	    	tableType = new JTextField(20);
	    	tableType.setBackground(Globals.COLOR_BG);
	    	tableType.setBorder(BorderFactory.createEmptyBorder());
	    	tableType.setEditable(false);
	    	Font f = tableType.getFont();
	    	tableType.setFont(new Font(f.getFontName(),Font.BOLD, f.getSize()));
	    	tableType.setMaximumSize(tableType.getPreferredSize());
	    	tableType.setAlignmentX(LEFT_ALIGNMENT);
	
	    	rowCount = new JTextField(30);
	    	rowCount.setBackground(Globals.COLOR_BG);
	    	rowCount.setBorder(BorderFactory.createEmptyBorder());
	    	rowCount.setEditable(false);
	    	rowCount.setMaximumSize(rowCount.getPreferredSize());
	    	rowCount.setAlignmentX(LEFT_ALIGNMENT);
	    	thePanel.add(tableType);
	    	thePanel.add(rowCount);
	    	thePanel.setMaximumSize(thePanel.getPreferredSize());	    	
	    	return thePanel;
    }
    
	public String getTabName() {
  		if(theJTable == null)
  			return strTabName + " In progress"; 
  		return strTabName; 
  	}
	
    //Called from a thread
    private void validateTable() {
    		validate();
    }
	//Sort listener for columns 
    private class SortHeader extends JTableHeader {
		private static final long serialVersionUID = -2417422687456468175L;
	    	public SortHeader(TableColumnModel model) {
	    		super(model);
	    		addMouseListener(new MouseAdapter() {
	            	public void mouseClicked(MouseEvent evt) 
	            	{ 
	            		SortTable table = (SortTable)((JTableHeader)evt.getSource()).getTable(); 
	            		TableColumnModel colModel = table.getColumnModel(); 
	            		int vColIndex = colModel.getColumnIndexAtX(evt.getX()); 
	            		int mColIndex = table.convertColumnIndexToModel(vColIndex);               			            		
	            		table.sortAtColumn(mColIndex);
	            }   
	    		});
	    	}    	
    }
    
    public class MultiLineHeaderRenderer extends JList implements TableCellRenderer {
		private static final long serialVersionUID = 3118619652018757230L;

		public MultiLineHeaderRenderer() {
	    	    setOpaque(true);
	    	    setBorder(BorderFactory.createLineBorder(Color.BLACK));
	    	    setBackground(Color.WHITE);
	    	    ListCellRenderer renderer = getCellRenderer();
	    	    ((JLabel)renderer).setHorizontalAlignment(JLabel.CENTER);
	    	    setCellRenderer(renderer);
    	  	}
    	 
    	  public Component getTableCellRendererComponent(JTable table, Object value,
    	                   boolean isSelected, boolean hasFocus, int row, int column) {
    	    setFont(table.getFont());
    	    String str = (value == null) ? "" : value.toString();
    	    BufferedReader br = new BufferedReader(new StringReader(str));
    	    String line;
    	    Vector<String> v = new Vector<String>();
    	    try {
	    	      while ((line = br.readLine()) != null) {
	    	        v.addElement(line);
	    	      }
    	    } catch (Exception e) {
    	    		ErrorReport.prtError(e, "Error rendering table cells");
    	    } catch(Error e) {
    	    		ErrorReport.reportFatalError(e);
    	    }
    	    setListData(v);
    	    return this;
    	  }
    	}
   
    private String strQuerySummary = null;
    private String strQueryWhereSQL = null;
	private String strTabName = "";
	
    //Show buttons
    private JButton btnShowLibraries = null;
    private JButton btnShowSNPs = null;
    private JButton btnShowExons = null;
    private JButton btnShowAlign = null;
    private JButton btnShowDraw = null;
    private JButton showColumnPanel = null;

    //Function buttons
    private JButton btnExport = null;
    private MultiButton btnCopyClipboard = null;
 
    private JTextField rowCount = null;
    private JTextField tableType = null;
    private JTextField loadStatus = null;
    private JTextField txtStatus = null;
    private JLabel lblSummary = null;
     
    private ActionListener dblClick = null;
    private ActionListener sngClick = null;
    private ListSelectionListener selListener = null;

    private Thread buildThread = null;
    private ViewerFrame theViewerFrame = null;
     
	private JPanel tableButtonPanel = null;
	private JPanel tableStatusPanel = null;
	private JPanel tableSummaryPanel = null;
    
	private JScrollPane theColumnScrollPane = null;	
	private TransColumn theColumn = null;
	private SortTable theJTable = null;
	private TableData theTableData = null;
	private JScrollPane theTableScrollPane = null;
	
	private String toolTip="Right mouse - step through options";
}
