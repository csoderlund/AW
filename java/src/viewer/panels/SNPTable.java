package viewer.panels;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.StringReader;
import java.sql.ResultSet;
import java.util.Enumeration;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

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

public class SNPTable extends JPanel {
	private static final long serialVersionUID = 771373784182823381L;
	private static final String htmlFile = "html/SNPTable.html";
	/**********************************************************
	 * Called from SNPQueryPanel to produce initial table, and sync columns
	 */
	public SNPTable(ViewerFrame pF, String tab, String query, String sum) {
		modeTable = Globals.MODE_SNP;
		theColumn = new SNPColumn(pF, modeTable, -1);
		theColumnScrollPane = theColumn.createColumnPanel(this);
		
		theViewerFrame = pF;
		
		strTabName = tab;
		strQueryWhereSQL = query;
		strQuerySummary = sum;

		executeSQL_InitTable();
	}	
	public TableData getTableData() {return theTableData;}
	public JTable getJTable() {return theJTable;}
	/**********************************************************
	 * Called from GeneTable and TransTable to show respective SNPS	 
	 * Note: queries build the 'where', but when coming from another table,
	 * such as from gene or trans, the where is from the selected rows,
	 * computed here.
	 */
	public SNPTable(ViewerFrame pF, TableData srcTable, int [] rows,
			int idx, int qID, int startIdx, int from, String sum) {
	
		modeTable = from;
		long start=-1;
		if (rows.length==1 && startIdx!=-1) 
			start = ((Long)srcTable.getValueAt(rows[0], startIdx));
		theColumn = new SNPColumn(pF, modeTable, start);
		theColumnScrollPane = theColumn.createColumnPanel(this);
		theViewerFrame = pF;
		
		getSQLwhere(srcTable, rows, idx, qID, sum);
		executeSQL_InitTable();
	}
	/***************************************************************/
	// called when a column is selected, or Clear or Sync
	public void columnChange() {
		createTable(false);
		createTablePanel();	
	}
	 /****************************************************
     * XXX  specific button panels
     */
    private JPanel createTableButtonPanel() {
	    	JPanel row1 = createTopButtons();
	    	
	    	JPanel buttonPanel = CreateJ.panelPage();
        buttonPanel.add(row1);
        
        return buttonPanel;
    }
    private JPanel createTopButtons() {
    		JPanel row1 = CreateJ.panelLine();
      	
    		row1.add(new JLabel ("Show Selected:"));
    		row1.add(Box.createHorizontalStrut(5)); 
    		
    		createShowTransButton();
    		row1.add(btnShowTrans);
    	    row1.add(Box.createHorizontalStrut(5)); 
    	    
    	    createShowVarTransButton();
    		row1.add(btnShowVarTrans);
    	    row1.add(Box.createHorizontalStrut(5)); 
    	    
    	    createShowSNPRepsButton();
    	    row1.add(btnShowSNPReps);
    	    row1.add(Box.createHorizontalStrut(5)); 
    	    
        	btnCopyClipboard = CreateJ.button("Copy " + Globals.SNPNAME); // TODO make pull-down with copy gene
        	btnCopyClipboard.setEnabled(false);
        	btnCopyClipboard.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				int row = theJTable.getSelectedRow();
				Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
				cb.setContents(new StringSelection((String)theTableData.
					getValueAt(row, theTableData.getColumnHeaderIndex(Globals.SNPNAME))), null);
			}
		});
        	row1.add(btnCopyClipboard);
        row1.add(Box.createHorizontalStrut(5)); 
        
        createDownloadButton();
        row1.add(btnDownload);
        row1.add(Box.createHorizontalStrut(5)); 
        
        JButton btnHelp = new JButton("Help");
        btnHelp.setBackground(Globals.COLOR_HELP);
        btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				ViewTextPane.displayHTML(theViewerFrame, "Variant Table Help", htmlFile);
			}
		});
        row1.add(btnHelp);
        
        row1.setMaximumSize(row1.getPreferredSize());
        return row1;
    }
    /***********************************************************
     * Top Row Download
     */
    private void createShowVarTransButton() {	
        btnShowVarTrans = new JButton("Var by trans");
    	
        btnShowVarTrans.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try{
					int IDidx = theTableData.getColumnHeaderIndex(Globals.SNPSQLID);
			 		int Nameidx = theTableData.getColumnHeaderIndex(Globals.SNPNAME);
			 		
					int cnt = getSQLWhereTransSelected(); // make sure it has trans
					if (cnt > 0) {		
						SNPTable newPanel = new SNPTable(theViewerFrame, theTableData, 
								theJTable.getSelectedRows(),
								IDidx, Nameidx,  -1, Globals.MODE_SNP_TRANS, null);
						theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getName(), 
								newPanel.getSummary());
					}
					else {
						LogTime.infoBox("No transcripts for SNP(s)");
					}
				} catch(Exception e) {
					ErrorReport.prtError(e, "Error showing damaged trans");
				} catch(Error e) {
					ErrorReport.reportFatalError(e);
				}
			}
		});        
    }
    private void createShowTransButton() {
    	 	btnShowTrans = new JButton("Trans");
        btnShowTrans.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try{
					int cnt = getSQLWhereTransSelected();
					if (cnt > 0) {		
						theViewerFrame.getTransColumnSync().setAbbr(theColumn.getChkAbbr());
				 		theViewerFrame.getTransColumnSync().setLibs(theColumn.getChkLibs());
						TransTable newPanel = 
								new TransTable(Globals.MODE_SNP, theViewerFrame, 
								strTabName, strQueryWhereSQL, strQuerySummary);
						theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getTabName(), 
								strQuerySummary);
					}
					else {
						LogTime.infoBox("No transcripts for SNP(s)");
					}
				} catch(Exception e) {
					ErrorReport.prtError(e, "Error showing damaged trans");
				} catch(Error e) {
					ErrorReport.reportFatalError(e);
				}
			}
		});        
    }
    private void createShowSNPRepsButton() {	
        btnShowSNPReps = new JButton("Replicas");
    	
        btnShowSNPReps.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try{	
					int IDidx = theTableData.getColumnHeaderIndex(Globals.SNPSQLID);
			 		int Nameidx = theTableData.getColumnHeaderIndex(Globals.SNPNAME);
			 	
			 		theViewerFrame.getSNPColumnSync().setAbbr(theColumn.getChkAbbr());
			 		theViewerFrame.getSNPColumnSync().setLibs(theColumn.getChkLibs());
					SNPRepTable newPanel = new SNPRepTable(theViewerFrame, theTableData, 
							theJTable.getSelectedRows(), IDidx, Nameidx, theColumn.getChkLibs());
					theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getTabName(), 
							strQuerySummary);
				} catch(Exception e) {
					ErrorReport.prtError(e, "Error showing replicas");
				} catch(Error e) {
					ErrorReport.reportFatalError(e);
				}
			}
		});        
    	
        btnShowSNPReps.addMouseListener(new MouseAdapter() {
	    		public void mousePressed(MouseEvent e) {
	            }
	    	});
    }
    /***********************************************************
     * Top Row Download
     */
    private void createDownloadButton() {
        btnDownload = new MultiButton(Globals.COLOR_FUNCTION);
        btnDownload.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnDownload.addLabel("Export Table");	   
    	
        btnDownload.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try{
					if(btnDownload.getText().equals("Export...") || 
							btnDownload.getText().equals("Export Table"))
						FileMethods.writeDelimFile(theViewerFrame, theJTable, "\t");
					
				} catch(Exception e) {
					ErrorReport.prtError(e, "Error saving file");
				} catch(Error e) {
					ErrorReport.reportFatalError(e);
				}
			}
		});          	
	    	btnDownload.addMouseListener(new MouseAdapter() {
	    		public void mousePressed(MouseEvent e) {
	            }
	    	});
    }
  //Needed for setTable, cannot use 'this' in a thread
  	private SNPTable getInstance() { return this; }
	/**********************************************************
	 * XXX QUERY
	 * SELECT * FROM `SNP` JOIN SNPgene WHERE SNPgene.GENEid='2' and SNP.SNPid=SNPgene.SNPid
	 * GeneTable does the strSubQuery where SNPgene.GENEid='x'
	 */
	private String getSQLquery() {
		ColumnData theColumns = theColumn.getColumnSQLMap();
				
        try {
	        	String strQuery = "SELECT " + theColumns.getDBColumnQueryList() + " FROM SNP ";
	        
	        	if (modeTable==Globals.MODE_GENE)
	        		strQuery += "  JOIN SNPgene ON SNP.SNPid = SNPgene.SNPid ";
	        	else if (modeTable==Globals.MODE_TRANS || modeTable==Globals.MODE_SNP_TRANS)
	        		strQuery += " JOIN SNPtrans ON SNP.SNPid = SNPtrans.SNPid ";
	        	else if (modeTable==Globals.MODE_SNP_REPS)
	        		strQuery += " JOIN SNPlib ON SNP.SNPid = SNPlib.SNPid ";
	        	else if (modeTable==Globals.MODE_EXON) 
	        		strQuery += " JOIN SNPexon ON SNP.SNPid = SNPexon.SNPid ";
        		if (strQueryWhereSQL.length() > 0) strQuery += " WHERE " + strQueryWhereSQL;
        
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
	
	private int getSQLWhereTransSelected() {
		try {
			int [] sels = theJTable.getSelectedRows();
			int IDidx = theTableData.getColumnHeaderIndex(Globals.SNPSQLID);
			int cnt=0;
			
			strQueryWhereSQL = "trans.TRANSid IN (";
			DBConn mDB = ViewerFrame.getDBConnection();
			
			for(int i=0; i<sels.length; i++) {
				long SNPid = ((Long)theTableData.getValueAt(sels[i], IDidx));
				ResultSet rs = mDB.executeQuery("SELECT SNPtrans.TRANSid FROM SNPtrans " +
						"WHERE SNPtrans.SNPid=" + SNPid);
				while (rs.next()) {
					int TRANSid = rs.getInt(1);
					if (cnt!=0) strQueryWhereSQL += ",";
					strQueryWhereSQL += TRANSid;
					cnt++;
				}
			}
			strQueryWhereSQL += ")";
			if (sels.length==1) {
				int idx = theTableData.getColumnHeaderIndex(Globals.SNPNAME);
				String rsid = (String) theTableData.getValueAt(sels[0], idx); 
				strTabName = "Trans " + Globals.TAB_SEL + rsid + ": ";
				strQuerySummary = "From SNP: Trans containing " + rsid;
			}
			else {
				strTabName =  "Trans " + Globals.TAB_SEL + sels.length + " SNPs: ";
				strQuerySummary = "From SNP: Trans containing selected " + sels.length + " SNPs";
			}
	      	return cnt;
        } catch(Exception e) {
        		ErrorReport.prtError(e, "Error processing query");
        		return 0;
        } 
	}
	
	/****************************************
	 * Coming from another table, the 'where' clause is built here.
	 */
	private void getSQLwhere(TableData srcTable, int [] rows, int IDidx, int Nameidx, String sum) {
		String queryOn="";
		String name1="";
		if (modeTable==Globals.MODE_GENE) {
			queryOn = "SNPgene.GENEid";
			strTabName = "GeneVar " + Globals.TAB_SEL;
			strQuerySummary = "From " + Globals.MODE_TABLE[modeTable] + ": ";
		}
		else if (modeTable==Globals.MODE_TRANS) {
			queryOn="SNPtrans.TRANSid";
			strTabName = "TransVar " + Globals.TAB_SEL;
			strQuerySummary = "From " + Globals.MODE_TABLE[modeTable] + ": ";
		}
		else if (modeTable==Globals.MODE_SNP_TRANS) {
			queryOn="SNPtrans.SNPid";
			strTabName = "VarsTrans " + Globals.TAB_SEL;
			strQuerySummary = "From " + Globals.MODE_TABLE[modeTable] + ": ";
		}
		else if (modeTable==Globals.MODE_EXON) {
			queryOn="SNPexon.EXONid";
			strTabName = "VarsExon " + Globals.TAB_SEL;
			strQuerySummary = "From " + Globals.MODE_TABLE[modeTable] + ": ";
			int n = (Integer) srcTable.getValueAt(rows[0], Nameidx);
			name1 = "Exon" + n;
		}
		else ErrorReport.die("No mode for SNPTable.getSQLWHere " + modeTable);
		
		if (name1.equals("")) name1 = (String) srcTable.getValueAt(rows[0], Nameidx);
		
 		if(rows.length == 1) {
 			strTabName += name1 + ": ";
 			strQuerySummary += name1;
 			
 			strQueryWhereSQL = queryOn + " = " + (Long)srcTable.getValueAt(rows[0], IDidx);
 			strQueryWhereSQL += " ORDER By SNP.pos";
 		}
 		else {
 			strTabName += rows.length + " " + Globals.MODE_TABLE[modeTable] + ": ";
 			strQuerySummary += name1;
 			
 			strQueryWhereSQL = "(" + queryOn + " IN (" + ((Long)srcTable.getValueAt(rows[0], IDidx));
 			for(int x=1; x<rows.length; x++) {
 				if (x<7) 
 					strQuerySummary += ", " +(String)srcTable.getValueAt(rows[x], Nameidx);
 				strQueryWhereSQL += ", " + ((Long)srcTable.getValueAt(rows[x], IDidx));
 			}	
 			if (rows.length>=7) strQuerySummary += "...";
 			strQueryWhereSQL += "))"; 
 		}
 		if (sum!=null) strQuerySummary += sum;
	}
    /****************************************************
	 * XXX build the master table
	 * this can probably be made into a class under viewer.table
	 * where rset, label, theFields, buttonPanel, etc are passed in. 
	 */
    private void buildTable(ResultSet rset) {
        ColumnData theColumns = theColumn.getColumnSQLMap();
              
        tableButtonPanel = createTableButtonPanel();
		tableStatusPanel = createTableStatusPanel();	
		tableSummaryPanel = createTableSummaryPanel();
		
		// XXXX columnPanel created on trans object creation
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
		sngClick = new ActionListener() {
			public void actionPerformed(ActionEvent e) {

			}
		};		
		selListener = new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent arg0) {
				if(theJTable.getSelectedRowCount() >= 1000)
					txtStatus.setText("Cannot select more than 1000 rows at a time");
				else
					txtStatus.setText("");
				
				if(theJTable.getSelectedRowCount() == 1 && btnCopyClipboard != null)
					btnCopyClipboard.setEnabled(true);
				else if(btnCopyClipboard != null)
					btnCopyClipboard.setEnabled(false);
			}
		};

        theTableData = new TableData(this);
        theTableData.setColumnHeaders(theColumns.getDisplayColumns(), theColumns.getDisplayTypes());
        theTableData.addRowsWithProgress(rset, theColumns.getDisplayColumnsSymbols(), loadStatus);
        theTableData.finalize();

        String status = "";
        if(theTableData.getNumRows() == 0) status = "No Vars";
        else if(theTableData.getNumRows() == 1) status = "1 Var";
        else status = theTableData.getNumRows() + " Vars";
        
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
					//XXX Perform the query
					String sql = getSQLquery();
					DBConn conn = ViewerFrame.getDBConnection();
					ResultSet rset = ViewerFrame.executeQuery(conn, sql, loadStatus);
					if(rset != null) {
						buildTable(rset);				
						ViewerFrame.closeResultSet(rset);
						conn.close();

						createTable(true);
					}
					createTablePanel();
					
					if(isVisible()) { // make table visible
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
		if (firstTime) columns = theColumn.getOrderedColumns();
		else columns = TableData.orderColumns(theJTable, theColumn.getSelectedColumns());
		
		theJTable = new SortTable(TableData.createModel(columns, theTableData, this));	
		
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
				//This not only informs the user that it is canceled, 
				//but this is also the signal for the thread to stop. 
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
	    	
	    	if(tableButtonPanel != null) {
	    		add(tableButtonPanel);
	    		add(Box.createVerticalStrut(10));
	    	}
	    	add(Box.createVerticalStrut(10));
	    	add(tableSummaryPanel);
	    	add(Box.createVerticalStrut(10));
	    	add(tableStatusPanel);
	    	add(theTableScrollPane);
	    	add(Box.createVerticalStrut(5));
	    	  	
	    	// Column Panel
	    	JPanel row = CreateJ.panelLabelLine("Columns:");
	    	row.add(showColumnPanel);
	    	row.add(Box.createHorizontalStrut(100));
	    
	    	row.add(txtStatus);
	    	row.setMaximumSize(row.getPreferredSize());   	
	    	add(row);
	    	add(Box.createVerticalStrut(5));
	    	
	    add(theColumnScrollPane);
	    	add(Box.createVerticalStrut(5));
	    	
	    	if(theJTable != null)
	    		tableType.setText("Variant View");
	
	    	invalidate();
	    	validateTable();
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
        
  	public String getSummary() { return strQuerySummary; }
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
  
    private int modeTable = 0;
    private String strQuerySummary = null;
	private String strQueryWhereSQL = "";
	private String strTabName = "";
    
    private MultiButton btnDownload = null;
    private JButton btnShowVarTrans = null;
    private JButton btnShowTrans = null;
    private JButton btnShowSNPReps = null;
    private JButton btnCopyClipboard = null;
        
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
	private JButton showColumnPanel = null;
	private SNPColumn theColumn = null; 
	private SortTable theJTable = null;
    private TableData theTableData = null;
    private JScrollPane theTableScrollPane = null;
}
