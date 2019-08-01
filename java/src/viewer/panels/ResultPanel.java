package viewer.panels;

/*****************************************************
 * Shows all the queries on the left panel
 */
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import util.Globals;
import util.ErrorReport;
import viewer.ViewerFrame;
import viewer.controls.CreateJ;
import viewer.controls.ViewTextPane;
import viewer.table.TableData;
import viewer.panels.TransTable;

public class ResultPanel extends JPanel {
	private static final long serialVersionUID = -4532933089334778200L;
	private static final String htmlFile = "html/Results.html";
	private static final int isDiff1=1;
	private static final int isDiff2=2;
	private static final String MSG = "Select two Trans or two SNP rows";
	
	public final static String panelType(JPanel p) {
		if (p instanceof TransTable) return "Trans";
		if (p instanceof GeneTable) return "Gene";
		if (p instanceof SNPTable) return "SNP";
		if (p instanceof SNPRepTable) return "SNP Rep";
		if (p instanceof LibraryTable) return "Library";
		if (p instanceof LibListTable) return "Lib List";
		if (p instanceof ExonTable) return "Exon";
		return "unknown";
	}
	private static final String [] RESULT_COLUMNS = { "Type", "Name", "Summary" };
	
	public ResultPanel(ViewerFrame parentFrame) {
		theViewerFrame = parentFrame;
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBackground(Globals.COLOR_BG);
		colNames = RESULT_COLUMNS;
		
		theTable = new JTable();
		theTable.getTableHeader().setBackground(Globals.COLOR_BG);
		theTable.setColumnSelectionAllowed( false );
		theTable.setCellSelectionEnabled( false );
		theTable.setRowSelectionAllowed( true );
		theTable.setShowHorizontalLines( false );
		theTable.setShowVerticalLines( true );	
		theTable.setIntercellSpacing ( new Dimension ( 1, 0 ) );		
		rowData = new Vector<String []>();
		panels = new Vector<JPanel> ();
		theTable.setModel(new ResultsTableModel());
		theTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				updateButtons();
				if (e.getClickCount() == 2) {
					int row = theTable.getSelectedRow();
					theViewerFrame.setSelection(panels.get(row));
				}
			}
		});
		
		theScrollPane = new JScrollPane(theTable);
		theScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
		theScrollPane.getViewport().setBackground(Globals.COLOR_BG);
				
		add(addButtonPanel());
		add(Box.createVerticalStrut(30));
		add(addListResultsPanel());
		add(theScrollPane);
	}
	
	public int getNumColumns() { return colNames.length; }
	
	public void addResult(JPanel theNewPanel, String tabName, String summary) {
		String [] temp = new String[3];
		temp[0] = panelType(theNewPanel);
		temp[1] = tabName;
		temp[2] = summary;
		rowData.add(temp);
		panels.add(theNewPanel);
		theTable.revalidate();
		updateButtons();
	}
	/****************************************************
	 * Intersect
	 */
	private void selInterPanels(int [] selRows) {
		if (selRows.length!=2) {
			JOptionPane.showMessageDialog(theViewerFrame, 
				MSG, "Intersect", JOptionPane.PLAIN_MESSAGE);
			return;
		}
		JPanel p1 = panels.get(selRows[0]);
		JPanel p2 = panels.get(selRows[1]);
		int type=0;
		if (p1 instanceof TransTable && p2 instanceof TransTable) type=1;
		else if (p1 instanceof SNPTable && p2 instanceof SNPTable) type=2;
		else {
			JOptionPane.showMessageDialog(theViewerFrame, 
					MSG, "Intersect", JOptionPane.PLAIN_MESSAGE);
			return;
		}
		String tab1 = getTab(rowData.get(selRows[0])[1]);
		String tab2 = getTab(rowData.get(selRows[1])[1]);
		Vector <Long> list1 = new Vector <Long> ();
		String whereSQL = "";
		int cnt=0;
		TableData data1, data2;
		int IDidx;
		
		if (type==1) {
			data1 = ((TransTable) p1).getTableData();
			data2 = ((TransTable) p2).getTableData();
			IDidx = data1.getColumnHeaderIndex(Globals.TRANSSQLID);
		}
		else {
			data1 = ((SNPTable) p1).getTableData();
			data2 = ((SNPTable) p2).getTableData();
			IDidx = data1.getColumnHeaderIndex(Globals.SNPSQLID);
		}
		
		int n1 = data1.getNumRows();
		int n2 = data2.getNumRows();
		for (int i=0; i<n1; i++) {
			Long id = (Long) data1.getValueAt(i, IDidx);
			list1.add(id);
		}
		for (int i=0; i<n2; i++) {
			Long id = (Long) data2.getValueAt(i, IDidx);
			if (list1.contains(id)) {
				if (cnt==0) whereSQL = "" + id;
				else whereSQL += "," + id;
				cnt++;
			}
		}
		if (cnt==0) {
			JOptionPane.showMessageDialog(theViewerFrame, 
					"No rows in intersect", "Intersect", JOptionPane.PLAIN_MESSAGE);
			return;
		}
		String summary = "Intersect of: " + tab1 + " ; "  + tab2;
		String x = getQ(tab1) + "." +  getQ(tab2);
		if (x.equals("")) x = "intersect";
		
		if (type==1) {
			whereSQL = "(trans.TRANSid IN (" + whereSQL + "))";
			String tabName = "Trans " + x + ": ";
			TransTable newPanel = 
				new TransTable(Globals.MODE_TRANS, theViewerFrame, 
				tabName, whereSQL, summary);
			theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getTabName(), 
				summary);
		}
		else {
			whereSQL = "(SNP.SNPid IN (" + whereSQL + "))";
			String tabName = "SNP " + x + ": ";
			SNPTable newPanel = 
				new SNPTable(theViewerFrame, tabName, whereSQL, summary);
			theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getTabName(), 
				summary);
		}
	}
	// UNION
	private void selUnionPanels(int [] selRows) {
		if (selRows.length!=2) {
			JOptionPane.showMessageDialog(theViewerFrame, 
				MSG, "Union", JOptionPane.PLAIN_MESSAGE);
			return;
		}
		JPanel p1 = panels.get(selRows[0]);
		JPanel p2 = panels.get(selRows[1]);
		int type=0;
		if (p1 instanceof TransTable && p2 instanceof TransTable) type=1;
		else if (p1 instanceof SNPTable && p2 instanceof SNPTable) type=2;
		else {
			JOptionPane.showMessageDialog(theViewerFrame, 
					MSG, "Union", JOptionPane.PLAIN_MESSAGE);
			return;
		}
		TableData data1, data2;
		int IDidx;
		
		if (type==1) {
			data1 = ((TransTable) p1).getTableData();
			data2 = ((TransTable) p2).getTableData();
			IDidx = data1.getColumnHeaderIndex(Globals.TRANSSQLID);
		}
		else {
			data1 = ((SNPTable) p1).getTableData();
			data2 = ((SNPTable) p2).getTableData();
			IDidx = data1.getColumnHeaderIndex(Globals.SNPSQLID);
		}
		int n1 = data1.getNumRows();
		int n2 = data2.getNumRows();
		
		String tab1 = getTab(rowData.get(selRows[0])[1]);
		String tab2 = getTab(rowData.get(selRows[1])[1]);
		Vector <Long> list1 = new Vector <Long> ();
		int cnt=0;
		
		for (int i=0; i<n1; i++) {
			Long id = (Long) data1.getValueAt(i, IDidx);
			list1.add(id);
		}
		String whereSQL = "";
		for (int i=0; i<n2; i++) {
			Long id = (Long) data2.getValueAt(i, IDidx);
			if (!list1.contains(id)) {
				if (cnt==0) whereSQL = "" + id;
				else whereSQL += "," + id;
				cnt++;
			}
		}
		for (Long id : list1) {
			if (cnt==0) whereSQL = "" + id;
			else whereSQL += "," + id;
			cnt++;
		}
		if (cnt==0) {
			JOptionPane.showMessageDialog(theViewerFrame, 
					"No rows in union", "Union", JOptionPane.PLAIN_MESSAGE);
			return;
		}
		String summary = "Union of: " + tab1 + " ; "  + tab2;
		String x = getQ(tab1) + "+" + getQ(tab2);
		if (x.equals("")) x = "union";
		
		if (type==1) {
			whereSQL = "(trans.TRANSid IN (" + whereSQL + "))";
			String tabName = "Trans " + x + ": ";
			TransTable newPanel = 
				new TransTable(Globals.MODE_TRANS, theViewerFrame, 
				tabName, whereSQL, summary);
			theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getTabName(), 
				summary);
		}
		else {
			whereSQL = "(SNP.SNPid IN (" + whereSQL + "))";
			String tabName = "SNP " + x + ": ";
			SNPTable newPanel = 
				new SNPTable(theViewerFrame, tabName, whereSQL, summary);
			theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getTabName(), 
				summary);
		}
	}
	// DIFF
	private void selDiffPanels(int [] selRows, int typeCmd) {
		String summary="";
		if (typeCmd==isDiff1) summary="1st Only";
		else summary = "2nd Only";
		
		if (selRows.length!=2) {
			JOptionPane.showMessageDialog(theViewerFrame, 
					MSG, summary, JOptionPane.PLAIN_MESSAGE);
			return;
		}
		JPanel p1 = panels.get(selRows[0]);
		JPanel p2 = panels.get(selRows[1]);
		int type=0;
		if (p1 instanceof TransTable && p2 instanceof TransTable) type=1;
		else if (p1 instanceof SNPTable && p2 instanceof SNPTable) type=2;
		else {
			JOptionPane.showMessageDialog(theViewerFrame, 
					MSG, summary, JOptionPane.PLAIN_MESSAGE);
			return;
		}
		String tab1 = getTab(rowData.get(selRows[0])[1]);
		String tab2 = getTab(rowData.get(selRows[1])[1]);
		
		TableData data1, data2;
		int IDidx;
		
		if (type==1) {
			data1 = ((TransTable) p1).getTableData();
			data2 = ((TransTable) p2).getTableData();
			IDidx = data1.getColumnHeaderIndex(Globals.TRANSSQLID);
		}
		else {
			data1 = ((SNPTable) p1).getTableData();
			data2 = ((SNPTable) p2).getTableData();
			IDidx = data1.getColumnHeaderIndex(Globals.SNPSQLID);
		}
		int n1 = data1.getNumRows();
		int n2 = data2.getNumRows();
		
		Vector <Long> list1 = new Vector <Long> ();
		for (int i=0; i<n1; i++) {
			Long id = (Long) data1.getValueAt(i, IDidx);
			list1.add(id);
		}
		Vector <Long> list2 = new Vector <Long> ();
		for (int i=0; i<n2; i++) {
			Long id = (Long) data2.getValueAt(i, IDidx);
			list2.add(id);
		}
		
		String whereSQL = "", tab="";
		int cnt=0, t1=0, t2=0;
		if (typeCmd==isDiff1) {
			for (Long TRANSid : list1) {
				if (!list2.contains(TRANSid)) {
					if (cnt==0) whereSQL = "" + TRANSid;
					else whereSQL += "," + TRANSid;
					cnt++; t1++;
				}
			}
			tab = getQ(tab1) + "!" + getQ(tab2);
		}
		if (typeCmd==isDiff2) {
			for (Long TRANSid : list2) {
				if (!list1.contains(TRANSid)) {
					if (cnt==0) whereSQL = "" + TRANSid;
					else whereSQL += "," + TRANSid;
					cnt++; t2++;
				}
			}
			tab = "!" + getQ(tab1) + getQ(tab2);
		}	
		
		if (whereSQL.equals("")) {
			if (typeCmd==isDiff1) JOptionPane.showMessageDialog(theViewerFrame, 
					"No unique to 1st", "1st Only", JOptionPane.PLAIN_MESSAGE);
			else JOptionPane.showMessageDialog(theViewerFrame, 
					"No unique to 2nd", "2nd Only", JOptionPane.PLAIN_MESSAGE);
			return;
		}
		summary +=  tab1+ "(" + t1 + ")" + " ; "  + tab2 + "(" + t2 + ")";
		
		if (type==1) {
			whereSQL = "(trans.TRANSid IN (" + whereSQL + "))";
			String tabName = "Trans " + tab + ": ";
			TransTable newPanel = 
				new TransTable(Globals.MODE_TRANS, theViewerFrame, 
				tabName, whereSQL, summary);
			theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getTabName(), 
				summary);
		}
		else {
			whereSQL = "(SNP.SNPid IN (" + whereSQL + "))";
			String tabName = "SNP " + tab + ": ";
			SNPTable newPanel = 
				new SNPTable(theViewerFrame, tabName, whereSQL, summary);
			theViewerFrame.addResultPanel(getInstance(), newPanel, newPanel.getTabName(), 
				summary);
		}
	}
	private String getQ(String tab) {
		Pattern tabPat = Pattern.compile("(Q(\\d+)):");
		Matcher x = tabPat.matcher(tab);
		if (x.find()) return x.group(1);
		return "";
	}
	private String getTab(String tab) {
		tab = tab.replace("Trans ", "");
		tab = tab.replace("trans", "");
		return tab;
	}
	private ResultPanel getInstance() { return this; }
	
	private void selRemovePanels(int [] selRows) {
		for(int x=selRows.length-1; x>=0; x--) {
			theViewerFrame.removePanelFromMenuOnly(panels.get(selRows[x]));
			panels.remove(selRows[x]);
			rowData.remove(selRows[x]);
		}
		theTable.clearSelection();
		theTable.revalidate();
		updateButtons();
	}
	
	public void removePanel(JPanel targetPanel) {
		for(int x=0; x<panels.size(); x++) {
			if(panels.get(x).equals(targetPanel)) {
				panels.remove(x);
				rowData.remove(x);
				theTable.revalidate();
				updateButtons();
				return;
			}
		}
	}
	
	public void renamePanel(JPanel targetPanel, String newName) {
		for(int x=0; x<theTable.getRowCount(); x++) {
			if(panels.get(x).equals(targetPanel)) {
	        		rowData.elementAt(x)[1] = newName;
				theTable.revalidate();
				updateButtons();
				return;
			}
		}
	}
	
	private void updateButtons() {		
		if(theTable.getRowCount() > 0) {
			btnRemoveSelPanels.setEnabled(true);
			btnUnionSelPanels.setEnabled(true);
			btnInterSelPanels.setEnabled(true);
			btn1stDiffSelPanels.setEnabled(true);
			btn2ndDiffSelPanels.setEnabled(true);
		}
		else {
			btnRemoveSelPanels.setEnabled(false);
			btnUnionSelPanels.setEnabled(false);
			btnInterSelPanels.setEnabled(false);
			btn1stDiffSelPanels.setEnabled(false);
			btn2ndDiffSelPanels.setEnabled(false);
		}
	}
	
	private JPanel addButtonPanel() {
		JPanel thePanel = new JPanel();
		thePanel.setLayout(new BoxLayout(thePanel, BoxLayout.LINE_AXIS));
		thePanel.setBackground(Globals.COLOR_BG);
		
		thePanel.add(new JLabel("Selected: "));
		thePanel.add(Box.createHorizontalStrut(5));
		btnUnionSelPanels = new JButton("Union");
		btnUnionSelPanels.setEnabled(false);
		btnUnionSelPanels.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				selUnionPanels(theTable.getSelectedRows());
			}
		});
		thePanel.add(btnUnionSelPanels);
		thePanel.add(Box.createHorizontalStrut(5));
		
		btnInterSelPanels = new JButton("Intersect");
		btnInterSelPanels.setEnabled(false);
		btnInterSelPanels.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				selInterPanels(theTable.getSelectedRows());
			}
		});
		thePanel.add(btnInterSelPanels);
		thePanel.add(Box.createHorizontalStrut(5));
		
		btn1stDiffSelPanels = new JButton("1st only");
		btn1stDiffSelPanels.setEnabled(false);
		btn1stDiffSelPanels.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				selDiffPanels(theTable.getSelectedRows(), isDiff1);
			}
		});
		thePanel.add(btn1stDiffSelPanels);
		thePanel.add(Box.createHorizontalStrut(5));
		
		btn2ndDiffSelPanels = new JButton("2nd only");
		btn2ndDiffSelPanels.setEnabled(false);
		btn2ndDiffSelPanels.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				selDiffPanels(theTable.getSelectedRows(), isDiff2);
			}
		});
		thePanel.add(btn2ndDiffSelPanels);
		thePanel.add(Box.createHorizontalStrut(5));
		
		btnRemoveSelPanels = new JButton("Remove");
		btnRemoveSelPanels.setEnabled(false);
		btnRemoveSelPanels.setBackground(Globals.COLOR_FUNCTION);
		btnRemoveSelPanels.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				selRemovePanels(theTable.getSelectedRows());
			}
		});
		thePanel.add(btnRemoveSelPanels);
		thePanel.add(Box.createHorizontalStrut(10));
		
		btnRemoveAllPanels = new JButton("Remove All");
		btnRemoveAllPanels.setBackground(Globals.COLOR_FUNCTION);
		btnRemoveAllPanels.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				theTable.selectAll();
				selRemovePanels(theTable.getSelectedRows());
			}
		});
		thePanel.add(btnRemoveAllPanels);
		
		JButton btnHelp = new JButton("Help");
        btnHelp.setBackground(Globals.COLOR_HELP);
        btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				ViewTextPane.displayHTML(theViewerFrame, "Results Help", htmlFile);
			}
		});
        thePanel.add(Box.createHorizontalStrut(10));
		thePanel.add(btnHelp);
		thePanel.setMaximumSize(thePanel.getPreferredSize());
		thePanel.setAlignmentX(LEFT_ALIGNMENT);
		
		return thePanel;
	}
	
	private JPanel addListResultsPanel() {
		JPanel thePanel = CreateJ.panelPage();
		
		JLabel headerLine = new JLabel("List Results");
		headerLine.setAlignmentX(LEFT_ALIGNMENT);		
		
		thePanel.add(headerLine);
		thePanel.setMaximumSize(thePanel.getPreferredSize());
		thePanel.setAlignmentX(LEFT_ALIGNMENT);
		thePanel.add(Box.createVerticalStrut(10));
		
		return thePanel;
	}
	
	private class ResultsTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 774460555629612058L;

		public int getColumnCount() {
            return colNames.length;
		}
        public int getRowCount() {
            return rowData.size();
        }
        public Object getValueAt(int row, int col) {
            String [] r = rowData.elementAt(row);
            return r[col];
        }      
        public String getColumnName(int col) {
            return colNames[col];
        }        
	}

	private ViewerFrame theViewerFrame = null;
	
	private JButton btnRemoveAllPanels = null;
	private JButton btnRemoveSelPanels = null;
	private JButton btnUnionSelPanels = null;
	private JButton btnInterSelPanels = null;
	private JButton btn1stDiffSelPanels = null;
	private JButton btn2ndDiffSelPanels = null;
	
    private JTable theTable = null;
	private JScrollPane theScrollPane = null;

	private String[] colNames = null;
	private Vector<String []> rowData = null;
	private Vector<JPanel> panels = null;
}

