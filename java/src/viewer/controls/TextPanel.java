package viewer.controls;

/********************************************
 * This is currently only called from ViewerFrame to display >Overview and >Help
 */
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class TextPanel extends JPanel {
	private static final long serialVersionUID = 6074102283606563987L;
	
	public TextPanel(String text, boolean useHTML) {
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		
		mainPanel = new JEditorPane();
		mainPanel.setEditable(false);
		Font font;

		if(useHTML) { // bold doesn't work, but courier does
			font = new Font("courier", Font.BOLD, 16);
			mainPanel.setContentType("text/html");
		}
		else {
			font = new Font("monospaced", Font.BOLD, 12);
			mainPanel.setContentType("text/plain");
		}
		mainPanel.setFont(font);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		setContent(text);
		JScrollPane scrollPane = new JScrollPane(mainPanel);
		scrollPane.setFont(font);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		setLayout(new BoxLayout ( this, BoxLayout.X_AXIS ));
		add(scrollPane);		
	}
	
	public void setContent(String text) {
		mainPanel.setText(text);
	}

	private JEditorPane mainPanel = null;
}