package viewer.controls;

/*********************************************
 * Displays the Help html for all panels and tables
 */
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.net.URL;

import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;

public class ViewEditorPane extends javax.swing.JEditorPane 
{
	private static final long serialVersionUID = 6456712672097393553L;

	public ViewEditorPane(URL linkURL) throws Exception 
	{
		super(linkURL);
		setEditable(false);
        Font font = UIManager.getFont("Label.font");
        String bodyRule = "body { font-family: " + font.getFamily() + "; " +
                "font-size: " + font.getSize() + "pt; }";
        ((HTMLDocument)getDocument()).getStyleSheet().addRule(bodyRule);

		addHyperlinkListener(new HyperlinkListener() 
		{
		    public void hyperlinkUpdate(HyperlinkEvent e) 
		    {
		        if(e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) 
		        {
		        		try
		        		{
		        			String ustr = e.getDescription();
		        			if (ustr.startsWith("http://"))
		        			{
					        	if(Desktop.isDesktopSupported()) 
					        	{
					        		Desktop.getDesktop().browse(e.getURL().toURI());
					        	}		        				
		        			}
		        			else
		        			{
		        				URL url = ViewEditorPane.class.getResource(ustr);
		        				setPage(url);
		        			}
	
		        		}
		        		catch(Exception eee)
		        		{
		        			System.err.println("Can't open link");
		        			System.err.println(eee.getMessage());
		        		}
		        }
		    }
		});
	}

	protected void paintComponent(Graphics g) 
	{
		Graphics2D g2 = (Graphics2D)g;

		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
		RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		super.paintComponent(g2);
	}
}

