package viewer.table;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.Vector;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import util.ErrorReport;
import util.Globals;

public class SortTable extends JTable implements ListSelectionListener {
	private static final long serialVersionUID = 5088980428070407729L;
	public static final String BLANK = "--"; // otherwise strand +/- does not work if "-"

	private Component getFormattedComponent(Component comp, int Index_row, int Index_col) {
   		if(!(comp instanceof JLabel)) return comp;
   		
       	JLabel compLbl = (JLabel)comp;
   
       	Class<?> cl = getColumnClass(Index_col);
       	String colName = getColumnName(Index_col);
  
       	//alternating row color
       	if(isRowSelected(Index_row)) {
       		compLbl.setBackground(bgColorHighlight);
       		compLbl.setForeground(bgColor);
       	}
       	else if (Index_row % 2 == 0) {
       		compLbl.setBackground(bgColorAlt);
       		compLbl.setForeground(txtColor);
       } 
       else {
	        	compLbl.setBackground(bgColor);
	        	compLbl.setForeground(txtColor);
       }  
       // format
       if(colName.equals("Row #")) {
       		compLbl.setText("" + (Index_row + 1));
       }
       else if (colName.equals(Globals.COL_AAPOS)) { 
       		String val = compLbl.getText();
       		int dval = Integer.parseInt(val);
            if (dval == Globals.NO_PVALUE) compLbl.setText(BLANK);
          	else compLbl.setText(addCommas(val));
       }
       else if(cl == Integer.class || cl == Long.class || cl == BigInteger.class) {
       		compLbl.setText(addCommas(compLbl.getText()));
       	}
       	else if(cl == Double.class || cl == Float.class) {

       		if(colName.contains("%")) {
           		Double val = (((Double)getValueAt(Index_row, Index_col) + .005) * 100);
           		compLbl.setText(val.intValue() + "%");
       		}
       		else {
       			Double val;
           		if (cl == Float.class)
           			val = Double.valueOf(((Float)getValueAt(Index_row, Index_col)));
           		else
           			val = ((Double)getValueAt(Index_row, Index_col));
           		
       			try {
       				if (val == Globals.NO_PVALUE && 
       				   (colName.startsWith(Globals.PRE_Spval) || colName.startsWith(Globals.PRE_Rpval) ||
       					colName.startsWith(Globals.PRE_Sscore) || colName.startsWith(Globals.PRE_Tscore) ||
       					colName.endsWith(Globals.SUF_PVAL) || colName.endsWith(Globals.SUF_REP))) {
       							compLbl.setText(BLANK);
       				}
       				else if (colName.startsWith(Globals.PRE_REF) || colName.startsWith(Globals.PRE_ALT)) {
       					if (val==-1) compLbl.setText(BLANK);
       					else {
       						DecimalFormat df = new DecimalFormat("#0.00;#0.00");
       						compLbl.setText(df.format(val));
       					}
       				}
       				else compLbl.setText(formatDouble(val));
       			}
       			catch (Exception e) {
       				ErrorReport.prtError(e, Index_row + " " + Index_col);
       				compLbl.setText("error");
       			}
       		}
       	}
       	
       	if(compLbl.getText().length() == 0) compLbl.setText("");
       	compLbl.setHorizontalAlignment(SwingConstants.LEFT);
        return compLbl;    		
	}
	private static String addCommas(String val) {
  		return val.replaceAll("(\\d)(?=(\\d{3})+$)", "$1,");
	}
	private static String formatDouble(double val) {
	    	if(val == 0.0) return "0.0";
	    	
	    	DecimalFormat df = null;
	    	double abs = Math.abs(val);
		    
	    if (abs >= 0.001 && abs < 1) { // CAS 7/17/14 
	    	 	BigDecimal bd1 = new BigDecimal(val, new MathContext(2));
		    	return bd1.toString();
	    	}
	    	else if (abs >= 1 && abs < 100000) 
	    		df = new DecimalFormat("#0.0#");
	    	else
	    		df = new DecimalFormat("0E0;0E0");
	    
	    	return df.format(val);
	}
    public SortTable(TableData tData) {
        theClickListeners = new Vector<ActionListener> ();
        theDoubleClickListeners = new Vector<ActionListener> ();
        
    		theModel = new SortTableModel(tData);
    	
        setAutoCreateColumnsFromModel( true );
       	setColumnSelectionAllowed( false );
       	setCellSelectionEnabled( false );
       	setRowSelectionAllowed( true );
       	setShowHorizontalLines( false );
       	setShowVerticalLines( true );	
       	setIntercellSpacing ( new Dimension ( 1, 0 ) );
       	setOpaque(true);

       	setModel(theModel);
       	
        addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent me) {
				if(me.getClickCount() > 1) {
				    ActionEvent e = new ActionEvent ( this, ActionEvent.ACTION_PERFORMED, "DoubleClickSingleRow" );
				    
					Iterator<ActionListener> iter = theDoubleClickListeners.iterator();
					while(iter.hasNext()) {
						iter.next().actionPerformed(e);
					}
				}
				else {
				    ActionEvent e = new ActionEvent ( this, ActionEvent.ACTION_PERFORMED, "SingleClickRow" );
					Iterator<ActionListener> iter = theClickListeners.iterator();
					while(iter.hasNext()) {
						ActionListener l = iter.next();
						l.actionPerformed(e);
					}					
				}
			}
			
        });
    }  
    
    public void removeListeners() {
    		theClickListeners.clear();
    		theDoubleClickListeners.clear();
    }
    
    public void addSingleClickListener(ActionListener l) {
    		theClickListeners.add(l);
    }
    
    public void addDoubleClickListener(ActionListener l) {
    		theDoubleClickListeners.add(l);
    }
    
    public void sortAtColumn(int column) { // this may not get called
	    	if(!theModel.getColumnName(column).equals("Row #"))
	    		theModel.sortAtColumn(column);
    }
    // Don't know where this is called from, but it is used
    // the code works if it is removed, but does not getFormatedComponent
    public Component prepareRenderer(TableCellRenderer renderer,int Index_row, int Index_col) {
	    	Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
	    	return getFormattedComponent(comp, Index_row, Index_col);
    }
    private Color bgColor = Color.WHITE;
    private Color bgColorAlt = new Color(240,240,255);
    private Color bgColorHighlight = Color.GRAY;
    private Color txtColor = Color.BLACK; 
    
    private SortTableModel theModel = null;
    private Vector<ActionListener> theClickListeners = null;
    private Vector<ActionListener> theDoubleClickListeners = null;

	private static final int MAX_AUTOFIT_COLUMN_WIDTH = 150; 
	
	// the width of columns varies a little based on the lenght of the table
    public void autofitColumns() {
        TableColumn column;
        Component comp;
        int headerWidth;
        int cellWidth;
        TableCellRenderer headerRenderer = getTableHeader().getDefaultRenderer();
        
        for (int i = 0;  i < getModel().getColumnCount();  i++) { // for each column
            column = getColumnModel().getColumn(i);
            
            comp = headerRenderer.getTableCellRendererComponent(
                                 this, column.getHeaderValue(),
                                 false, false, 0, i);
            
            headerWidth = comp.getPreferredSize().width + 10;
            
            cellWidth = 0;
            for (int j = 0;  j < getModel().getRowCount() && j<100;  j++) { // for each row
            		Class <?> cl = null;
            		Object val = null;
            		
            		try {
            			cl = theModel.getColumnClass(i);    		
            			val = theModel.getValueAt(j, i);       		
            			comp = getDefaultRenderer(cl).getTableCellRendererComponent(this, val, false, false, j, i);
            			comp = getFormattedComponent(comp, j, i);
            			cellWidth = Math.max(cellWidth, comp.getPreferredSize().width); 
            		}
            		catch (Exception e) {
            			System.out.println("error on " + cl + " " + val);
            			ErrorReport.prtError(e, "autofitcolumns");
            		}
            }
            int max = Math.max(headerWidth, cellWidth)+1;
            int min = Math.min(max, MAX_AUTOFIT_COLUMN_WIDTH);
            column.setPreferredWidth(min);
        }
    }
    
    public class SortTableModel extends AbstractTableModel {

	    	private static final long serialVersionUID = -2360668369025795459L;
	
	    	public SortTableModel(TableData values) {
	    		theData = values;
	    	}
	
	    	public boolean isCellEditable(int row, int column) { return false; }
	    	public Class<?> getColumnClass(int columnIndex) { return theData.getColumnType(columnIndex); }
	    	public String getColumnName(int columnIndex) { return theData.getColumnName(columnIndex); }
	    	public int getColumnCount() { return theData.getNumColumns(); }
	    	public int getRowCount() { return theData.getNumRows(); }
	    	public Object getValueAt(int rowIndex, int columnIndex) {
	    		return theData.getValueAt(rowIndex, columnIndex); 
	    	}
	    	public void setValueAt(Object obj, int rowIndex, int columnIndex) { 
	    		theData.setValueAt(obj, rowIndex, columnIndex); 
	    	}
	    	public void sortAtColumn(int columnIndex) {// This may not get called
	    		theData.sortByColumn(columnIndex);
	    		this.fireTableDataChanged();
	    		theData.sortMasterList(theData.getColumnName(columnIndex));
	    	}
	
	    	private TableData theData = null;
    }
}