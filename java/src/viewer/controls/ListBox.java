package viewer.controls;
/*******************************************
 * Used in Transcript Query to add rules for query
 */
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import util.Globals;
import viewer.ViewerFrame;

public class ListBox  extends JPanel {
	private static final long serialVersionUID = -6602976255905887521L;
	private static final int WIDTH = 500;
	private static final int HEIGHT = 100;
	
	public ListBox(ViewerFrame vFrame) {
		theViewerFrame = vFrame;
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBackground(Globals.COLOR_BG);
		
		JPanel row = CreateJ.panelLine();
		 
		boxList = new JList(emptyList);
		boxList.setEnabled(false);
		
		JScrollPane sPane = new JScrollPane(boxList);
		sPane.setPreferredSize(new Dimension(WIDTH, HEIGHT));
 		sPane.getVerticalScrollBar().setUnitIncrement(15);
 		sPane.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(sPane);
		add(row);
		
		JPanel buttonRow = createButtonPanel();
		add(Box.createVerticalStrut(10));
		add(buttonRow);
	}
	
	private JPanel createButtonPanel() {
		JPanel row = CreateJ.panelLine();
		
		btnRemove = CreateJ.button("Remove");
		btnRemove.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				removeClause();
			}
		});
		row.add(btnRemove);
		row.add(Box.createHorizontalStrut(5));
		
		btnRemoveAll = CreateJ.button("Remove All");
		btnRemoveAll.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				removeAll();
			}
		});
		row.add(btnRemoveAll);
		row.add(Box.createHorizontalStrut(5));
		
		btnSaveRules = CreateJ.button("Save Rules");
		btnSaveRules.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				saveRules();
			}
		});
		row.add(btnSaveRules);
		row.add(Box.createHorizontalStrut(5));
		
		btnLoadRules = CreateJ.button("Load Rules");
		btnLoadRules.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				loadRules();
			}
		});
		row.add(btnLoadRules);		
		return row;
	}	
	
	public void addClause(String op, String clause, String sql) {
		rowOpList.add(op);
		if (rowSumList.size()==0) rowSumList.add(" (" + clause + ")");
		else rowSumList.add(op + " (" + clause + ")");
		rowSQLList.add(sql);
		updateList();
	}
	
	public void appendClause(String op, String sum, String sql) {
		int index = boxList.getSelectedIndex();
		if (index==-1) return;
		String rule = rowSumList.get(index) + " " + op + " (" + sum + ")";
		String rulesql;
		if (op.equals("&")) rulesql = rowSQLList.get(index) + " and " + sql;
		else rulesql = rowSQLList.get(index) + " or " + sql;
		rowSumList.set(index, rule);
		rowSQLList.set(index, rulesql);
		updateList();
	}
	public Vector <String> getSummaries() { return rowSumList;}
	public Vector <String> getSQLWhere() { return rowSQLList;}
	public Vector <String> getOp() { return rowOpList;}
	
	public void removeAll() {
		rowSumList.clear();
		rowSQLList.clear();
		rowOpList.clear();
		boxList.setListData(emptyList);
		boxList.setEnabled(false);
	}
	private void removeClause() {
		Object [] vals = boxList.getSelectedValues();
		for(int i=0; i<vals.length; i++) {
			for (int j=0; j<rowSumList.size(); j++) {
				if (vals[i].equals(rowSumList.get(j))) {
					rowSumList.remove(j);
					rowSQLList.remove(j);
					rowOpList.remove(j);
				}
			}	
		}
		updateList();
	}
	private void saveRules() {
		
		String rules="";
		for (int i=0; i<rowSumList.size(); i++) {
			rules += rowOpList.get(i) + ":::" + rowSumList.get(i) + ":::" + rowSQLList.get(i) + "\n";
		}
		FileMethods.writeFile(theViewerFrame, rules);
	}
	
	private void loadRules() {
		Vector <String> rules = FileMethods.readFile(theViewerFrame);
		if (rules==null) return;
		
		rowSumList.clear();
		rowSQLList.clear();
		for (String r : rules) {
			String [] parts = r.split(":::");
			if (parts.length==3) {
				rowOpList.add(parts[0]);
				rowSumList.add(parts[1]);
				rowSQLList.add(parts[2]);
			}
		}
		updateList();
	}
	
	private void updateList() {
		int size = rowSumList.size();
		if (size==0) {
			boxList.setListData(emptyList);
			boxList.setEnabled(false);
			return;
		}
		Object [] list = new String [rowSumList.size()];
		for (int i=0; i<size; i++) list[i] = rowSumList.get(i);
		boxList.setListData(list);
		boxList.setEnabled(true);
		return;
	}
	private ViewerFrame theViewerFrame = null;
	
	private Vector <String> rowSumList = new Vector <String> ();
	private Vector <String> rowSQLList = new Vector <String> ();
	private Vector <String> rowOpList = new Vector <String> ();
	private Object [] emptyList = {"empty list"};
	
	private JList boxList = null;
	private JButton btnRemove = null;
	private JButton btnRemoveAll = null;
	private JButton btnSaveRules = null;
	private JButton btnLoadRules = null;
}
