package viewer;

import util.Globals;

public class ViewerMain {

	public static void main(String[] args) {
		System.out.println("View " + Globals.TITLE);
		String pre = Globals.DBprefix;
		
		if (args.length == 0 || args[0].equals("-h") || args[0].equals("-help")) {
			System.out.println("\nUsage: viewAW [database suffix]");
			System.out.println("   The database is prefixed with " + pre );
			System.out.println("Or: viewAW -d");
			System.out.println("   Shows all existing databases with prefix " + pre);
			System.exit(1);
		}
		
		if (args[0].equals("-d")) {
			new ViewerFrame();
		}
		else {
			String db = args[0];
			if (db.startsWith(pre)) 
				db = db.substring(pre.length());
			ViewerFrame mainWindow = new ViewerFrame(db);
			mainWindow.setVisible(true);
		}
	}
}
