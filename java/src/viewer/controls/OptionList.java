package viewer.controls;

/************************************************
 * Radio button exclusive options, e.g. o Yes  o No  x don't care
 */
import java.awt.Component;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import util.Globals;

public class OptionList extends JPanel {
	private static final long serialVersionUID = -2536481010314753219L;

	public OptionList(String [] labels, int selection) {
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBackground(Globals.COLOR_BG);
		
		ButtonGroup group = new ButtonGroup();
		theOptions = new JRadioButton[labels.length];
		for(int x=0; x<labels.length; x++) {
			theOptions[x] = new JRadioButton(labels[x]);
			theOptions[x].setBackground(Globals.COLOR_BG);
			if(x == selection)
				theOptions[x].setSelected(true);
			else
				theOptions[x].setSelected(false);
			group.add(theOptions[x]);
			add(theOptions[x]);
		}
	}
	
	public int getSelectedOption() {
		for(int x=0; x<theOptions.length; x++)
			if(theOptions[x].isSelected())
				return x;
		return -1;
	}
	
	public void setSelectedOption(int pos) {
		for(int x=0; x<theOptions.length; x++) {
			if(pos == x)
				theOptions[x].setSelected(true);
			else
				theOptions[x].setSelected(false);
		}
	}
	
	public void setEnabled(boolean enabled) {
		for(int x=0; x<theOptions.length; x++)
			theOptions[x].setEnabled(enabled);
	}
	
	public boolean isEnabled() { return theOptions[0].isEnabled(); }
	
	private JRadioButton [] theOptions = null;
}
