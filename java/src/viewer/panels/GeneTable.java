package viewer.panels;
/****************************************
 * Called from Gene Query to produce Gene Table
 */
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
import java.util.Vector;
import java.util.Enumeration;

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

import database.DBConn;
import util.ErrorReport;
import util.Globals;
import util.LogTime;
import viewer.ViewerFrame;
import viewer.table.ColumnData;
import viewer.table.ColumnSync;
import viewer.table.SortTable;
import viewer.table.TableData;
import viewer.controls.CreateJ;
import viewer.controls.MultiButton;
import viewer.controls.ViewTextPane;
import viewer.controls.FileMethods;
import viewer.panels.GeneColumn;

public class GeneTable extends JPanel {
	private static final long serialVersionUID = -12486209145350722L;
	private static final String htmlFile = "html/GeneTable.html";
	
	/**********************************************************
	 * Called from GeneQuery
	 */
	public GeneTable(GeneColumn gc, ViewerFrame pF, String tab, String tblQuery, 
			String tblSum, String limit) {
		theViewerFrame = pF;
	
		strTabName = tab;
		strQueryWhereSQL = tblQuery;
		strQuerySummary = tblSum;
		strQueryLimit = limit;
		theColumn = gc;

		executeSQL_InitTable();
	}
	// called when a column is selected, or Clear or Sync
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
					int IDidx = theTableData.getColumnHeaderIndex(Globals.GENESQLID);
			 		int Nameidx = theTableData.getColumnHeaderIndex(Globals.GENENAME);
			 		String queryOn = "geneLib.GENEid"; 
			 		
					LibListTable newPanel = new LibListTable(theViewerFrame, theTableData, theJTable.getSelectedRows(),
							IDidx, Nameidx, queryOn, Globals.MODE_GENE, null);
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
    		
    		
    		
    		// SNPTable does query based on selected rows
    		btnShowSNPs = CreateJ.buttonFun("Variants");
    		btnShowSNPs.setEnabled(false);
    		btnShowSNPs.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try {
					int IDidx = theTableData.getColumnHeaderIndex(Globals.GENESQLID);
			 		int Nameidx = theTableData.getColumnHeaderIndex(Globals.GENENAME);
			 		int startIdx = theTableData.getColumnHeaderIndex(Globals.START); 
			 		
					SNPTable newPanel = new SNPTable(theViewerFrame, theTableData, theJTable.getSelectedRows(),
							IDidx, Nameidx, startIdx,  Globals.MODE_GENE, "");
					theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getName(), 
							newPanel.getSummary());
				} catch(Exception e) {
					ErrorReport.prtError(e, "Error showing SNPs");
				} catch(Error e) {
					ErrorReport.reportFatalError(e);
				}
			}
    		});
    		row1.add(btnShowSNPs);
    		row1.add(Box.createHorizontalStrut(5));
    		
    		btnShowTrans = CreateJ.buttonFun("Trans");
    		btnShowTrans.setEnabled(false);
    		btnShowTrans.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try {
					
					int cnt = getSQLWhereTransSelected();
					if (cnt > 0) {		
						TransTable newPanel = 
								new TransTable(Globals.MODE_SNP, theViewerFrame, 
								strTabName, strQueryWhereSQL, strQuerySummary);
						theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getTabName(), 
								strQuerySummary);
					}
					else {
						LogTime.infoBox("No transcripts for gene(s)");
					}
				} catch(Exception e) {
					ErrorReport.prtError(e, "Error showing Trans");
				} catch(Error e) {
					ErrorReport.reportFatalError(e);
				}
			}
    		});
    		row1.add(btnShowTrans);
    		row1.add(Box.createHorizontalStrut(5));
    		
    		btnShowAlign = CreateJ.buttonFun("Draw");
    		btnShowAlign.setEnabled(false);
    		btnShowAlign.addActionListener(new ActionListener() {
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
    		row1.add(btnShowAlign);
    		row1.add(Box.createHorizontalStrut(5));
    		
    		
        	btnCopyClipboard = CreateJ.button("Copy Gene"); // TODO make pull-down with copy gene
        	btnCopyClipboard.setEnabled(false);
        	btnCopyClipboard.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					int row = theJTable.getSelectedRow();
					Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
					cb.setContents(new StringSelection((String)theTableData.
							getValueAt(row, theTableData.getColumnHeaderIndex(Globals.GENENAME))), null);
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
				ViewTextPane.displayHTML(theViewerFrame, "Gene Table Help", htmlFile);
			}
		});
        row1.add(btnHelp);
        
        row1.setMaximumSize(row1.getPreferredSize());
        return row1;
    }
  //Needed for setTable, cannot use 'this' in a thread
  	private GeneTable getInstance() { return this; }
	/**********************************************************
	 * XXX QUERY
	 */
	private String getSQLquery() {
		ColumnData theFields = theColumn.getColumnSQLMap();
        try {
	        	String strQuery = "SELECT " + theFields.getDBColumnQueryList() + " FROM gene ";
	        	     
        		if (strQueryWhereSQL.length() > 0) 
        			strQuery += "WHERE " + strQueryWhereSQL; 
        		
        		if (strQueryLimit.length() > 0)
	        		strQuery += " " + strQueryLimit;   
	      
	        	//This is threaded.. check if the user canceled at some point, if so no data
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
			int IDidx = theTableData.getColumnHeaderIndex(Globals.GENESQLID);
			int cnt=0;
			
			strQueryWhereSQL = "trans.TRANSid IN (";
			DBConn mDB = ViewerFrame.getDBConnection();
			
			for(int i=0; i<sels.length; i++) {
				long GENEid = ((Long)theTableData.getValueAt(sels[i], IDidx));
				ResultSet rs = mDB.executeQuery("SELECT trans.TRANSid " +
						"FROM trans WHERE trans.GENEid=" + GENEid);
				while (rs.next()) {
					int TRANSid = rs.getInt(1);
					if (cnt!=0) strQueryWhereSQL += ",";
					strQueryWhereSQL += TRANSid;
					cnt++;
				}
			}
			strQueryWhereSQL += ")";
			if (sels.length==1) {
				int idx = theTableData.getColumnHeaderIndex(Globals.GENENAME);
				String name = (String) theTableData.getValueAt(sels[0], idx); 
				strTabName = "Trans " + Globals.TAB_SEL + name + ": ";
				strQuerySummary = "From Gene: " + name;
			}
			else {
				strTabName =  "Trans " + Globals.TAB_SEL + sels.length + " Genes: ";
				strQuerySummary = "From Gene: Trans containing selected " + sels.length + " genes";
			}
	      	return cnt;
        } catch(Exception e) {
        		ErrorReport.prtError(e, "Error processing query");
        		return 0;
        } 
	}
    /****************************************************
	 * XXX build the master table
	 * this can probably be made into a class under viewer.table
	 * where rset, label, theFields, buttonPanel, etc are passed in. 
	 */
    private void buildTable(ResultSet rset) {
        ColumnData theFields = theColumn.getColumnSQLMap();
              
        tableButtonPanel = createTableButtonPanel();
		tableStatusPanel = createTableStatusPanel();	
		tableSummaryPanel = createTableSummaryPanel();
		
		// XXXX create the column select check boxes
		theColumnScrollPane = theColumn.createColumnPanel(this);
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
				boolean rowsSelected = false;
				if(theJTable.getSelectedRowCount() > 0 && theJTable.getSelectedRowCount() < 1000) 
					rowsSelected = true;
				if(theJTable.getSelectedRowCount() >= 1000)
					txtStatus.setText("Cannot select more than 1000 rows at a time");
				else
					txtStatus.setText("");
				
				if(theJTable.getSelectedRowCount() == 1 && btnCopyClipboard != null)
					btnCopyClipboard.setEnabled(true);
				else if(btnCopyClipboard != null)
					btnCopyClipboard.setEnabled(false);
			
				btnShowLibraries.setEnabled(rowsSelected);
				btnShowSNPs.setEnabled(rowsSelected);
				btnShowAlign.setEnabled(theJTable.getSelectedRowCount() == 1);
				btnShowTrans.setEnabled(rowsSelected);
			}
		};

        theTableData = new TableData(this);
        theTableData.setColumnHeaders(theFields.getDisplayColumns(), theFields.getDisplayTypes());
        theTableData.addRowsWithProgress(rset, theFields.getDisplayColumnsSymbols(), loadStatus);
        theTableData.finalize();
 
        //Update the menu and query results
        String status = "";
        if(theTableData.getNumRows() == 0) status = "No genes";
        else if(theTableData.getNumRows() == 1) status = "1 gene";
        else status = theTableData.getNumRows() + " genes";
        
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
						//Thread safe way of closing the resultSet
						ViewerFrame.closeResultSet(rset);
						conn.close();

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
	    		
	    	add(Box.createVerticalStrut(5));
	    	JPanel bottomButtons = CreateJ.panelLine();
	    	JLabel col = new JLabel("Columns:");
	    	bottomButtons.add(col);
	    bottomButtons.add(Box.createHorizontalStrut(5));
	    	bottomButtons.add(showColumnPanel);
	    	bottomButtons.add(Box.createHorizontalStrut(100));
	    	bottomButtons.add(txtStatus);
	    	bottomButtons.setMaximumSize(bottomButtons.getPreferredSize());   	
	    	add(bottomButtons);
	    add(Box.createVerticalStrut(10));
	    	add(theColumnScrollPane);
	    	
	    	if(theJTable != null)
		    	tableType.setText("Gene View");   
	
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
    /***********************************************************
     * Download
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
   
    private String strQuerySummary = null;
    private String strQueryWhereSQL = null;
    private String strQueryLimit = null;
	private String strTabName = "";
	
    //Show buttons
    private JButton btnShowLibraries = null;
    private JButton btnShowSNPs = null;
    private JButton btnShowAlign = null;
    private JButton btnShowTrans = null;
    private JButton showColumnPanel = null;

    //Function buttons
    private MultiButton btnDownload = null;
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
	private GeneColumn theColumn = null; 
	private SortTable theJTable = null;
	private TableData theTableData = null;
	private JScrollPane theTableScrollPane = null;
}
