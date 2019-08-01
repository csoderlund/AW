package viewer.controls;

import java.awt.Component;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.CaretListener;

import viewer.ViewerFrame;
import util.Globals;

public class RangeList extends JPanel {
	private static final long serialVersionUID = 1850168523023801208L;
	
	public static final int RANGE_LTE = 0;
	public static final int RANGE_GTE = 1;
	public static final int RANGE_NA = 2;
	
	public RangeList(String leadLabel, String [] labels, String [] values, int [] selections, int gap, CaretListener cl) {
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBackground(Globals.COLOR_BG);
		
		cmbBtnRange = new ButtonCombo[labels.length];
		lblLabel = new JLabel[labels.length];
		txtValue = new JTextField[labels.length];
		
		if(leadLabel != null)
			add(new JLabel(leadLabel));
		for(int x=0; x<labels.length; x++) {
			cmbBtnRange[x] = new ButtonCombo(ButtonCombo.COMP_OPTIONS, 0);
			if(selections[x] == RANGE_LTE)
				cmbBtnRange[x].setSelectedItem("<=");
			else if(selections[x] == RANGE_GTE)
				cmbBtnRange[x].setSelectedItem(">=");
			else if(selections[x] == RANGE_NA)
				cmbBtnRange[x].setSelectedItem("<=>");
			
			lblLabel[x] = new JLabel(labels[x]);
			
			txtValue[x] = new JTextField(5);
			txtValue[x].setBackground(Globals.COLOR_BG);
			txtValue[x].setText("" + values[x]);
			txtValue[x].setMaximumSize(txtValue[x].getPreferredSize());
			txtValue[x].setMinimumSize(txtValue[x].getPreferredSize());
			txtValue[x].addCaretListener(cl);
			
			add(cmbBtnRange[x]);
			add(Box.createHorizontalStrut(5));
			add(txtValue[x]);
			add(Box.createHorizontalStrut(5));
			add(lblLabel[x]);
			add(Box.createHorizontalStrut(gap));
		}
	}
	
	public void enableAll(boolean enable) {
		for(int x=0; x<cmbBtnRange.length; x++) {
			cmbBtnRange[x].setEnabled(enable);
			lblLabel[x].setEnabled(enable);
			txtValue[x].setEnabled(enable);
		}
	}
	
	public boolean isEnabledAt(int pos) { return cmbBtnRange[pos].isEnabled(); }
	public String getRangeAt(int pos) { return cmbBtnRange[pos].getSelectedItem(); }
	public String getValueAt(int pos) { return txtValue[pos].getText().trim(); }
	public void clearValueAt(int pos) { txtValue[pos].setText(""); }
	public int getFieldCount() { return txtValue.length; }
	
	private ButtonCombo [] cmbBtnRange = null;
	private JLabel [] lblLabel = null;
	private JTextField [] txtValue = null;
}
