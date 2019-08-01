package viewer.controls;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

import viewer.ViewerFrame;
import util.Globals;

public class ButtonCombo extends JPanel {
	private static final long serialVersionUID = -4332991291207280824L;
	
	public static final String [] COMP_OPTIONS = { "<=", ">=" };
	
	public ButtonCombo(String [] options, int selection) {
		theOptions = new String[options.length];
		for(int x=0; x<options.length; x++)
			theOptions[x] = options[x];
		
		int maxLabelPos = 0;
		for(int x=1; x<theOptions.length; x++) {
			if(theOptions[x].length() > theOptions[maxLabelPos].length())
				maxLabelPos = x;
		}

		btnOption = new JButton(theOptions[maxLabelPos]);
		btnOption.setMaximumSize(btnOption.getPreferredSize());
		btnOption.setMinimumSize(btnOption.getPreferredSize());
		btnOption.setBorder(BorderFactory.createEmptyBorder());
		btnOption.setBackground(Globals.COLOR_BG);
		btnOption.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		btnOption.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				incSelection();
			}
		});
		
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
		setBackground(Globals.COLOR_BG);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		
		add(btnOption);
		
		setMaximumSize(getPreferredSize());
		setMinimumSize(getPreferredSize());
		btnOption.setText(theOptions[selection]);
	}
	
	public void setSelection(int selection) {
		selection = selection % theOptions.length;
		btnOption.setText(theOptions[selection]);
	}
	
	public void setSelectedItem(String label) { btnOption.setText(label); } 
	
	public int getSelectedPos() {
		for(int x=0; x<theOptions.length; x++) {
			if(theOptions[x].equals(btnOption.getText()))
				return x;
		}
		return -1;
	}
	
	public String getSelectedItem() { return btnOption.getText(); }
	public void setEnabled(boolean enabled) { btnOption.setEnabled(enabled); }
	public boolean isEnabled() { return btnOption.isEnabled(); }
	
	private void incSelection() { setSelection(getSelectedPos() + 1); }
	
	private String [] theOptions = null;
	private JButton btnOption = null;
}
