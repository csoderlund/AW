package viewer.controls;

/*************************************************
 * Pulldown of one or more options
 * Currently, used just for the single Download button
 */
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

import util.Globals;

public class MultiButton extends JPanel {
	private static final long serialVersionUID = -4416494948701821198L;

	public MultiButton() {
		theLabels = new Vector<String> ();
		theButton = new JButton();
		theButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		theButton.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent ev) {
				if(ev.getButton() == 3) {
					int pos = theLabels.indexOf(theButton.getText());
					pos = (pos + 1) % theLabels.size();
					theButton.setText(theLabels.get(pos));
				}
			}
		});
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		
		add(theButton);
	}
	
	public MultiButton(Color c) {
		theLabels = new Vector<String> ();
		
		theButton = new JButton();
		theButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		theButton.setBackground(c);
		
		theButton.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent ev) {
				if(ev.getButton() == 3) {
					int pos = theLabels.indexOf(theButton.getText());
					pos = (pos + 1) % theLabels.size();
					theButton.setText(theLabels.get(pos));
				}
			}
		});
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		
		add(theButton);
	}
	public void addLabels(String [] labels) {
		for (int i=0; i< labels.length; i++) {
			theLabels.add(labels[i]);
			if(theLabels.size() == 1)
				theButton.setText(labels[i]);
		}
	}
	
	public void addLabel(String label) {
		theLabels.add(label);
		if(theLabels.size() == 1)
			theButton.setText(label);
	}
	
	public void setEnabled(boolean enabled) {
		theButton.setEnabled(enabled);
	}
	
	public boolean isEnabled() { return theButton.isEnabled(); }
	
	public String getText() { return theButton.getText(); }
	public void addActionListener(ActionListener al) {
		theButton.addActionListener(al);
	}
	
	private Vector<String> theLabels = null;
	private JButton theButton;
}
