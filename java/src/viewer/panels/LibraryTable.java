package viewer.panels;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
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
import javax.swing.JCheckBox;
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
import viewer.ViewerFrame;
import viewer.controls.CreateJ;
import viewer.controls.MultiButton;
import viewer.controls.ViewTextPane;
import viewer.controls.FileMethods;
import viewer.table.ColumnData;
import viewer.table.ColumnSync;
import viewer.table.SortTable;
import viewer.table.TableData;
import database.DBConn;

public class LibraryTable extends JPanel {
	private static final long serialVersionUID = 8538658338254253600L;
	private static final String htmlFile = "html/LibraryTable.html";

	/**********************************************************
	 * Called from LibraryQueryPanel to produce initial table
	 */
	public LibraryTable(ViewerFrame pF, String tab, String query, String sum) {
		theViewerFrame = pF;
		strTabName = tab;
		strQueryWhereSQL = query;
		strQuerySummary = sum;
		theColumn = new LibraryColumn(pF);

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
     * XXX button panel
     */
    private JPanel createTableButtonPanel() {  
	    JPanel row1 = createTopButtons ();
	    	
	    	JPanel buttonPanel = CreateJ.panelPage();
        buttonPanel.add(row1);
        return buttonPanel;
    }
    private JPanel createTopButtons() {
     	JPanel row1 = CreateJ.panelLine();
    		row1.add(new JLabel("Show selected: "));
      	row1.add(Box.createHorizontalStrut(5));   	
        
        btnShowGenes = CreateJ.buttonFun("Genes");
        btnShowGenes.setEnabled(false);
        btnShowGenes.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try {
					int IDidx = theTableData.getColumnHeaderIndex(Globals.LIBSQLID);
			 		int Nameidx = theTableData.getColumnHeaderIndex(Globals.LIBNAME);
			 		String queryOn = "geneLib.LIBid"; 
			 		
					LibListTable newPanel = new LibListTable(theViewerFrame, theTableData, theJTable.getSelectedRows(),
							IDidx, Nameidx, queryOn, Globals.MODE_GENE_LIBS, null);
					theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getName(), 
							newPanel.getSummary());
				} catch(Exception e) {
					ErrorReport.prtError(e, "Error showing Genes for Library View");
				} catch(Error e) {
					ErrorReport.reportFatalError(e);
				}
			}
    		});
    		row1.add(btnShowGenes);
    		row1.add(Box.createHorizontalStrut(5));
    		
    		btnShowTrans = CreateJ.buttonFun("Trans");
        btnShowTrans.setEnabled(false);
        btnShowTrans.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try {
					int IDidx = theTableData.getColumnHeaderIndex(Globals.LIBSQLID);
			 		int Nameidx = theTableData.getColumnHeaderIndex(Globals.LIBNAME);
			 		String queryOn = "transLib.LIBid"; 
			 		
					LibListTable newPanel = new LibListTable(theViewerFrame, theTableData, theJTable.getSelectedRows(),
							IDidx, Nameidx, queryOn, Globals.MODE_TRANS_LIBS, null);
					theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getName(), 
							newPanel.getSummary());
				} catch(Exception e) {
					ErrorReport.prtError(e, "Error transcripts for Library View");
				} catch(Error e) {
					ErrorReport.reportFatalError(e);
				}
			}
    		});
    		row1.add(btnShowTrans);
    		row1.add(Box.createHorizontalStrut(5));
    	    		
    		JButton btnHelp = new JButton("Help");
            btnHelp.setBackground(Globals.COLOR_HELP);
            btnHelp.addActionListener(new ActionListener() {
    			public void actionPerformed(ActionEvent arg0) {
    				ViewTextPane.displayHTML(theViewerFrame, "Library Table Help", htmlFile);
    			}
    		});
        row1.add(btnHelp);
    		row1.setMaximumSize(row1.getPreferredSize());       	
    		return row1;
    }
    /**********************************************************
	 * XXX QUERY
	 */
	private String getSQLquery() {
		ColumnData theFields = theColumn.getColumnSQLMap();
				
        try {
	        	String strQuery = "SELECT " + theFields.getDBColumnQueryList() + 
	        			" FROM " + Globals.LIBRARY_TABLE;
	        	     	
		    if(strQueryWhereSQL.length() > 0) 
		        	strQuery += " WHERE " + strQueryWhereSQL;
   
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
	 
    /****************************************************
	 * XXXX build the master table
	 */
    private void buildTable(ResultSet rset) {
        ColumnData theColumnData = theColumn.getColumnSQLMap();
              
        tableButtonPanel = createTableButtonPanel();
		tableStatusPanel = createTableStatusPanel();	
		tableSummaryPanel = createTableSummaryPanel();	
		
		theColumnPanel = theColumn.createColumnPanel(this);
		theColumnPanel.setVisible(false);
		
		//Create toggle for displaying column select
		showColumnPanel = CreateJ.button("Select");
		showColumnPanel.setBackground(Globals.COLOR_COLUMNS);
		showColumnPanel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(theColumnPanel.isVisible()) {
					showColumnPanel.setText("Select");
					theColumnPanel.setVisible(false);
				}
				else {
					showColumnPanel.setText("Hide");
					theColumnPanel.setVisible(true);
				}
				createTablePanel();
			}
		});		
		
		txtStatus = new JTextField(100);
		txtStatus.setBorder(BorderFactory.createEmptyBorder());
		txtStatus.setEditable(false);
		txtStatus.setBackground(Globals.COLOR_BG);

		//Set action for table listeners
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
				
				btnShowGenes.setEnabled(rowsSelected);
				btnShowTrans.setEnabled(rowsSelected);
			}
		};

        theTableData = new TableData(this);
        theTableData.setColumnHeaders(theColumnData.getDisplayColumns(), theColumnData.getDisplayTypes());
        theTableData.addRowsWithProgress(rset, theColumnData.getDisplayColumnsSymbols(), loadStatus);
        theTableData.finalize();
       
        String status = "";
        if(theTableData.getNumRows() == 0) status = "No Library";
        else if(theTableData.getNumRows() == 1) status = "1 Library";
        else status = theTableData.getNumRows() + " Libraries";
        
        rowCount.setText(strTabName + status);
        theViewerFrame.changePanelName(this, strTabName + status);
    }
 
	//Needed for setTable, cannot use 'this' in a thread
	private LibraryTable getInstance() { return this; }
	
	/****************************************************
	 * Initialization for all constructors
	 */
	private void executeSQL_InitTable() {
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setAlignmentY(Component.LEFT_ALIGNMENT);

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
					
					//Update the interface with completed panel
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
	private void createTable(boolean loadMaster) {
		if(theJTable != null) //If this is not done, the old table stays in memory		
			theJTable.removeListeners();
			
		String [] columns;
		if (loadMaster) columns = theColumn.getOrderedColumns();
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
    
    //Take built view table and build the panel
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
	    	
	    	add(theColumnPanel);
	    	add(Box.createVerticalStrut(10));
	    	
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
	  	add(theColumnPanel);
    	
	    	if(theJTable != null)
	    		tableType.setText("Library View");
	
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
    	
    		btnDownload.addLabel("Export table");
    	
        btnDownload.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try{
					if(btnDownload.getText().equals("Export table"))
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
    
    private String strQueryWhereSQL = "";
    private String strQuerySummary = null;
	private String strTabName = "";
	    
    private JTextField rowCount = null;
    private JTextField tableType = null;
    private JTextField loadStatus = null;
    private JTextField txtStatus = null;
    private JLabel lblSummary = null;
    private JButton btnShowGenes = null;
    private JButton btnShowTrans = null;
    private JButton showColumnPanel = null;
    
    //Function buttons
    private MultiButton btnDownload = null;
    private JButton btnCopyClipboard = null;
    
    private ActionListener dblClick = null;
    private ActionListener sngClick = null;
    private ListSelectionListener selListener = null;
   
    private Thread buildThread = null;
    private ViewerFrame theViewerFrame = null;
    
    private JPanel tableButtonPanel = null;
	private JPanel tableStatusPanel = null;
	private JPanel tableSummaryPanel = null;
	private JPanel theColumnPanel = null;
	
	private LibraryColumn theColumn = null; 
	private SortTable theJTable = null;
    private TableData theTableData = null;
    private JScrollPane theTableScrollPane = null;
}
