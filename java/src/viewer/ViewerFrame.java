package viewer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.ResultSet;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;

import database.DBConn;
import database.MetaData;
import database.HostCfg;

import util.ErrorReport;
import util.Globals;
import util.LogTime;
import viewer.controls.CreateJ;
import viewer.controls.TextPanel;
import viewer.panels.GeneQuery;
import viewer.table.ColumnSync;
import viewer.panels.TransQuery;
import viewer.panels.SNPQuery;
import viewer.panels.LibraryQuery;
import viewer.panels.MainMenuPanel;
import viewer.panels.ResultPanel;

public class ViewerFrame extends JFrame {
	private static final long serialVersionUID = -8856159343902806878L;

	static private String DBname="", DBhost = "", DBuser = "", DBpass = "";

	//Menu selections
	private static final String [] MAIN_MENU = { 
		">Overview", ">Libraries", ">Genes", ">Trans", ">Variants", ">Results", ">Help" };
	String overviewHead = ""; 
	
	//Frame properties
	private static final int MIN_FRAME_WIDTH = 1000;
	private static final int MIN_FRAME_HEIGHT = 700; // if change this, change Globals.CLEAR_LOC;
	
	// applet
	public ViewerFrame(String dbUrl, String dbUser, String dbName) {
		DBhost = dbUrl;
		DBuser = dbUser;
		DBname = dbName;
		if (!DBConn.checkMysqlDB(DBhost, DBname, DBuser, null)) {
			System.out.println("Database does not exist: " + DBname);
			System.exit(-1);
		}
		createViewerFrame();
	}
	// desktop
	public ViewerFrame(String dbName) {
		HostCfg hcfg = new HostCfg();
		boolean exists = hcfg.existsDB(dbName);
		if (!exists) {
			System.out.println("Database does not exist:" + dbName);
			showDatabases();
			System.exit(-1);
		}
		
		DBhost = hcfg.getDBhost();
		DBuser = hcfg.getDBuser();
		DBname = hcfg.getDBname();
		DBpass = hcfg.getDBpass();
		createViewerFrame();
	}
	// desktop - just display names of AW databases
	public ViewerFrame() {
		showDatabases();
	}
	private void showDatabases() {
		try {
			System.out.println("Show " + Globals.DBprefix + " databases");
			HostCfg hcfg = new HostCfg();
			hcfg.readHosts();
			
			DBConn mDB = hcfg.getDBConnection();
			ResultSet rs = mDB.executeQuery("show databases");
			int cnt=0;
			while (rs.next()) {
				String db = rs.getString(1);
				if (db.startsWith(Globals.DBprefix)) {
					System.out.println("   " + db);
					cnt++;
				}
			}
			System.out.println("Databases: " + cnt);
		}
		catch (Exception e){
			System.out.println("Cannot show databases");
			e.printStackTrace();
		}
	}
	
	// this get called over and over everytime the database is queried
	static public DBConn getDBConnection() throws Exception
	{
		String dbstr = "jdbc:mysql://" + DBhost + "/" 	+ DBname;
		return new DBConn(dbstr, DBuser, DBpass);
	}
	
	static public ResultSet executeQuery(DBConn conn, String query, JTextField updateField) throws Exception {
		//Wait for resultSet to become available
		double x = 0;
		while(curResult != null) {
			Thread.sleep(100);
			x += .1;
			if(updateField != null) 
				updateField.setText("Waiting for DB to be available (" + x + ") seconds lapsed");
		}
		curResult = conn.executeQuery(query);
		return curResult;
	}	
	static public void closeResultSet(ResultSet rset) throws Exception {
		rset.close();
		curResult = null;
	}
	
	/*******************************************************
	 * Middle man between Table and Columns
	 */
	public ColumnSync getGeneColumnsSync() { return theGeneColumnSync;}
	public ColumnSync getTransColumnSync() { return theTransColumnSync;}
	public ColumnSync getSNPColumnSync() { return theSNPColumnSync;}
	public ColumnSync getExonColumnSync() { return theExonColumnSync;}
	public ColumnSync getLibraryColumnSync() { return theLibraryColumnSync;}
	public ColumnSync getListLibColumnSync() { return theListLibColumnSync;}
	public ColumnSync getSNPRepColumnSync() { return theSNPRepColumnSync;}
	
	public void setSelection(JPanel panel) {
		menuPanel.setSelected(panel);
	}
	
	public MetaData getMetaData() { return theMetaData;}
		
	private String getOverviewText() {
		try {
			String overview=overviewHead;
			DBConn conn = getDBConnection();
			ResultSet rs = executeQuery(conn, "SELECT overview, buildDate, chgDate, remark FROM metaData", null);
			if (!rs.next()) {
				LogTime.PrtError("viewAW cannot access overview");
				return "cannot access overview";
			}
			overview += "Build Date: " +  rs.getDate(2) + 
					"   Change Date: " + rs.getString(3) + "    " + rs.getString(4) 
					+ "\n" + rs.getString(1);
			
			closeResultSet(rs);
			conn.close();
			
			return overview;
		} catch (Exception e) {
			ErrorReport.prtError(e, "Error loading overview");
		} catch (Error e) {
			ErrorReport.reportFatalError(e);
		}
		return null;
	}
	/*******************************************************
	 * CreateViewerFrame
	 */
	private void createViewerFrame() {
		try {
			DBConn conn = getDBConnection();
			theMetaData = new MetaData(conn);
		}
		catch (Exception e) {ErrorReport.die(e, "Could not get metadata");}
		
		setTitle(DBname);
		setMinimumSize(new Dimension(MIN_FRAME_WIDTH, MIN_FRAME_HEIGHT));
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);
		
		theTransColumnSync = new ColumnSync(this, "Trans");
		theGeneColumnSync = new ColumnSync(this, "Gene");
		theSNPColumnSync = new ColumnSync(this, "SNP");
		theExonColumnSync = new ColumnSync(this, "Exon");
		theLibraryColumnSync = new ColumnSync(this, "Library");
		theListLibColumnSync = new ColumnSync(this, "LibList");
		theSNPRepColumnSync = new ColumnSync(this, "SNPRep");
		
		//build 
		createMainPanel();
		
		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerLocation(MIN_FRAME_WIDTH/5);

        splitPane.setBorder(null);
        splitPane.setRightComponent(mainPanel);
        splitPane.setLeftComponent(sPane);
    
        setLayout(new BorderLayout());
        add(splitPane, BorderLayout.CENTER);		
	}
	
	private void createMainPanel() {
		mainPanel = CreateJ.panelPage();
		menuSelectListener = new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
			}
		};
		
		menuCloseListener = new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if(arg0.getSource() instanceof JButton) {
					JPanel curPanel = menuPanel.getSelectedPanel();
					JPanel remPanel = menuPanel.getMenuItem((JButton)arg0.getSource());
					
					removePanel(remPanel);
					
					if(curPanel == remPanel)
						menuPanel.setSelected(resultPanel);					
				}
			}
		};
		
		menuPanel = new MainMenuPanel(menuSelectListener, menuCloseListener);

		overviewPanel = new TextPanel(getOverviewText(), false);
		libraryQueryPanel = new LibraryQuery(this);
		geneQueryPanel = new GeneQuery(this);
		transQueryPanel = new TransQuery(this);
		SNPQueryPanel = new SNPQuery(this);
		resultPanel = new ResultPanel(this);
		helpPanel = new TextPanel(getHelpText(), true);
		
		menuPanel.addMenuItem(null, overviewPanel, MAIN_MENU[0]);
		menuPanel.addMenuItem(null, libraryQueryPanel, MAIN_MENU[1]);
		menuPanel.addMenuItem(null, geneQueryPanel, MAIN_MENU[2]);
		menuPanel.addMenuItem(null, transQueryPanel, MAIN_MENU[3]);
		menuPanel.addMenuItem(null, SNPQueryPanel, MAIN_MENU[4]);
		menuPanel.addMenuItem(null, resultPanel, MAIN_MENU[5]);
		menuPanel.addMenuItem(null, helpPanel, MAIN_MENU[6]);
		
		mainPanel.add(overviewPanel);
		mainPanel.add(libraryQueryPanel);
		mainPanel.add(geneQueryPanel);
		mainPanel.add(transQueryPanel);
		mainPanel.add(SNPQueryPanel);
		mainPanel.add(resultPanel);
		mainPanel.add(helpPanel);

		menuPanel.setSelected(overviewPanel);
		
		sPane = new JScrollPane(menuPanel);
	}
	
	public void addResultPanel(JPanel parentPanel, JPanel newPanel, String name, String summary) {
		mainPanel.add(newPanel);
		menuPanel.addMenuItem(parentPanel, newPanel, name);
		resultPanel.addResult(newPanel, name, summary);
	}
	
	public void addResultPanel(JPanel newPanel, String name, String summary) {
		mainPanel.add(newPanel);
		menuPanel.addMenuItem(resultPanel, newPanel, name);
		resultPanel.addResult(newPanel, name, summary);
	}
	
	public void removePanel(JPanel panel) {
		mainPanel.remove(panel);
		menuPanel.removeMenuItem(panel);
		resultPanel.removePanel(panel);
	}
	
	public void removePanel(JButton closeButton) {
		removePanel(menuPanel.getMenuItem(closeButton));
	}
	
	public void removePanelFromMenuOnly(JPanel panel) {
		menuPanel.removeMenuItem(panel);
	}
	
	public void changePanelName(JPanel sourcePanel, String newName) {
		menuPanel.renameMenuItem(sourcePanel, newName);
		resultPanel.renamePanel(sourcePanel, newName);
	}
	private String getHelpText() {
		String msg = "<html>";
		msg += "<head><style type=\"text/css\">body { font-family: monospace, courier; } </style></head><body>";
		msg += "<p>The following tabs on the left can be selected and will produce a page the replaces this Help. ";
		msg += "Each resulting page (except Overview) has a Help button.";
		msg += "<p>";
		msg += "<table border=0 cellspacing=4>";
		msg += "<tr><td><b>Tab on left</b><td><b>Description</b>";
		msg += "<tr><td valign=\"top\">Libraries<td>There will be one or more libraries.";
		
		msg += "<tr><td valign=\"top\">Genes<td>The genes are defined in the genome annotation file. The coordinates of the ";
		msg += "gene are the rightmost and leftmost coordinates of its transcripts.  ";
		msg += "Its variants are all those found within coordinates.";
		msg += "<tr><td valign=\"top\">Trans<td>There is at least one transcript for each gene.";
		msg += "<tr><td valign=\"top\">Variants<td>SNPs and indels.";
		msg += "<tr><td valign=\"top\">Results<td>All search results are listed under this tab. They may be removed by selecting " +
				"the x next to them, or select the '>Results' to further manipulate the results.";
		msg += "</table>";
		msg += "<p>Terminology:";
		msg += "<table border=0 cellspacing=4>";
		msg += "<tr><td>Ref, Alt<td> Refers to the reference and alternative genomes.";
		msg += "</table>";
		msg += "</body></html>";
		return msg;
	}
	
	//Main panels for the window
	private JSplitPane splitPane = null;
	private MainMenuPanel menuPanel = null;
	private JPanel mainPanel = null;
    private JScrollPane sPane = null;
	
	//Individual content panels
	private TextPanel overviewPanel = null;
	private LibraryQuery libraryQueryPanel = null;
	private GeneQuery geneQueryPanel = null;
	private TransQuery transQueryPanel = null;
	private SNPQuery SNPQueryPanel = null;
	private ResultPanel resultPanel = null;
	private TextPanel helpPanel = null;
	
	//Events for selecting/closing menus
	private ActionListener menuSelectListener = null;
	private ActionListener menuCloseListener = null;
		
	//Settings
	private ColumnSync theTransColumnSync = null;
	private ColumnSync theGeneColumnSync = null;
	private ColumnSync theSNPColumnSync = null;
	private ColumnSync theExonColumnSync = null;
	private ColumnSync theLibraryColumnSync = null;
	private ColumnSync theListLibColumnSync = null;
	private ColumnSync theSNPRepColumnSync = null;
	
	private MetaData theMetaData = null;
	
	//Result set queue
	private static ResultSet curResult = null;
}
