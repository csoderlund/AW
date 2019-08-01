package viewer;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JApplet;

import util.Globals;
import viewer.ViewerFrame;

public class ViewerApplet extends JApplet {
	
	private static final long serialVersionUID = -2952501895997109083L;

	public void start() {
		System.out.println("View " + Globals.TITLE); // CAS 12/31/14
		if ( theFrame != null )
			theFrame.toFront();
		else
		{
			theFrame = new ViewerFrame (getParameter("DB_URL") ,getParameter("DB_USER") ,
							getParameter("ASSEMBLY_DB") );
			theFrame.setVisible(true);

			theFrame.addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					
				}

				public void windowClosed(WindowEvent e) {
					stop();
				}
			});	
		}
	}
	
	public void stop() 
	{
		theFrame = null;
		destroy();
		System.exit(0);
	}
	private ViewerFrame theFrame = null;
}

