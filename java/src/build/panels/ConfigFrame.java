package build.panels;

/***********************************
 * Panel for creating AW.cfg
 * TODO:
 * the variant files may need a prefix for new variants (e.g. SNP1, Indel2)
 * Right now, the SNP and Indel files are handle a little differently -- is that necessary?
 * 	and if so, need to get that info from here
 * Right now, the build only takes one variant count directory.
 * Nothing is really tested
 */
import java.sql.ResultSet;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;

import util.ErrorReport;
import util.Format;
import util.Globals;
import util.LogTime;
import viewer.controls.CreateJ;
import viewer.controls.ViewTextPane;
import build.Bmain;
import build.Cfg;
import database.HostCfg;
import database.DBConn;

public class ConfigFrame extends JFrame
{
	private static final String pgmName = "runAW";
	private static final String errorMsg = "Building runAW interface";
	private static final long serialVersionUID = 1L;
	private static final String htmlFile = "html/runAW.html";
	private static final String htmlFile2 = "html/Files.html";
	private static final String htmlFile3 = "html/Pipeline.html";
	private static final int tableHeight=60;
	private static final int tableWidth=500;
	private static final int browseTextWidth=45;
	
	private String[] strainCols = {"Name","Abbr*","isHybrid (yes/no)"};
	private String[] condCols = {"Name","Abbr*"};
	private String currentProject = "";
	private String lastDir = "";
	private boolean bNewProject = false; // a hack to avoid printing "can't find config" msg for a new project
	private HostCfg hostCfg=null;
	private JComboBox cmbProjects;
	
	private boolean bHaveCondition2 = false;
	// 
	// The following controls are declared final so they can be seen inside event handlers
	final EditTableModel strainTblModel = new EditTableModel(strainCols,0);
	final EditTableModel condTblModel = new EditTableModel(condCols,0);
	
	// These are accessed in the validate method but not directly in a handler
	private JTextField varText = new JTextField(browseTextWidth);
	private JTextField varAnnoText = new JTextField(browseTextWidth);
	private JTextField genomeSeqText = new JTextField(browseTextWidth);
	private JTextField genomeAnnoText = new JTextField(browseTextWidth);
	private JTextField genomeNCBIText = new JTextField(browseTextWidth);
	private JTextField transCntText = new JTextField(browseTextWidth);
	private JTextField varCntText = new JTextField(browseTextWidth);
	private JTextField refText = new JTextField(5);
	private JTextField altText = new JTextField(5);
	private JTextField cond1Txt = new JTextField(10);
	private JTextField cond2Txt = new JTextField(10);
	
	private JButton btnBuildDatabase = new JButton("Build");
	private JButton btnRemProject = new JButton("Remove");
	private JButton btnSaveProject = new JButton("Save");
	private JButton btnCopyProject = new JButton("Copy");
	private JButton btnOverProject = new JButton("Overview");
	
	// The condition edit tables each have a method overridden to detect editing and
		// require revalidation entries
	final JTable strainTable = new JTable(strainTblModel){
		private static final long serialVersionUID = 6352184381249267788L;
		public Component prepareEditor(TableCellEditor editor,int row, int column) {
				Component c = super.prepareEditor(editor, row, column);
				return c;
		}
	};
	final JTable condTable = new JTable(condTblModel){
		private static final long serialVersionUID = -8522049412058942804L;
		public Component prepareEditor(TableCellEditor editor,int row, int column) {
				Component c = super.prepareEditor(editor, row, column);
				return c;
		}
	};
		
	public ConfigFrame()
	{
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent we) 
			{
				System.exit(0);
			}
		});
		
		File pf = new File(Globals.projDir);
		if (pf.exists() && pf.isFile())
		{
			ErrorReport.infoBox("A file named 'projects' exists; please delete before running AW");
			System.exit(0);
		}
		if (!pf.exists())
			pf.mkdir();
		
		hostCfg = new HostCfg();
		
		create0Panel();
		enableControls(false);
		setTitle(pgmName);
		setBounds(600,400,650,850); // x, y, width, height
	}
	/*************************************************
	 * XXX Create Panel
	 */
	private void create0Panel()
	{
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
		mainPanel.setBackground(Color.white);
		mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		mainPanel.setAlignmentY(Component.TOP_ALIGNMENT);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		mainPanel.setVisible(true);
		
		create1TopRow("PROJECT", mainPanel);
		horizSeparator(mainPanel);
		create2ParameterRow("PARAMETERS", mainPanel);
		
		mainPanel.add(CreateJ.panelLabelLine("--- Conditions ---", Color.blue));
		create3Conditions(mainPanel);
		mainPanel.add(Box.createVerticalStrut(10));
	
		mainPanel.add(CreateJ.panelLabelLine("--- Variant Files ---", Color.blue));
		create4VarFiles(mainPanel);
		mainPanel.add(Box.createVerticalStrut(10));
		
		mainPanel.add(CreateJ.panelLabelLine("--- Reference Genome Files ---", Color.blue));
		create5GenomeFiles(mainPanel);
		mainPanel.add(Box.createVerticalStrut(10));
		
		mainPanel.add(CreateJ.panelLabelLine("--- Transcript Count Files ---", Color.blue));
		create6GeneCntFiles(mainPanel);
		
		horizSeparator(mainPanel);
		
		create7build("AW database", mainPanel);
		
		addCfgToUI();
		
		JScrollPane sPane = new JScrollPane(mainPanel);
		getContentPane().add(sPane);
	}
	
////////////////////////////////////////////////
//// Top bar of buttons
/////////////////////////////////////////////////
	private void create1TopRow(String label, JPanel mainPanel) {
		mainPanel.add(Box.createVerticalStrut(5));
		JPanel pRow = CreateJ.panelLabelLine(label);
		
		// Select a Project
		cmbProjects = new JComboBox();
		cmbProjects.setBackground(Color.white);
		updateProjList(cmbProjects,"");
		cmbProjects.addActionListener(
	        new ActionListener()
	        {
	            public void actionPerformed(ActionEvent e)
	            {
	                if (cmbProjects.getSelectedIndex() > 0)
	                {
	                		currentProject = ((String)cmbProjects.getSelectedItem()).replace("*", "");
	                		if (addCfgToUI()) enableControls(true);
	                		else 	btnRemProject.setEnabled(true);
	                }
	                else
	                {
						enableControls(false);
	                }
	            }
	        }            
        );
		pRow.add(cmbProjects);	
		
		// new project
		JButton btnAddProject = new JButton("Create");
		btnAddProject.setBackground(Globals.COLOR_PROMPT);
		btnAddProject.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnAddProject.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				String result = null;
				while(true) {
					result = (String)JOptionPane.showInputDialog(getInstance(), "Enter name for new project", "", JOptionPane.PLAIN_MESSAGE);
					if(result == null) break;
									
					if (result.matches("[A-Za-z_0-9]+")) 
					{
						File f = new File(Globals.projDir,result);
						if (f.exists())
						{
							ErrorReport.infoBox("The project name '" + result + "' is already in use");
							return;
						}
						f.mkdir();
						bNewProject = true;
						updateProjList(cmbProjects,result); // cause cmbProject.actionListioner to be invoked
                			bNewProject = false;
						currentProject = result;
						enableControls(true);
						break;
					}
					else 
					{
						JOptionPane.showMessageDialog(getInstance(), "Invalid Project Name. \n\nThe name may only contain "
								+ "letters, '_' or digits", "Error - Invalid Name", JOptionPane.PLAIN_MESSAGE);
					}
				} 
			}
		});
		pRow.add(Box.createHorizontalStrut(10));
		pRow.add(btnAddProject);

	// Remove a project
		btnRemProject.setBackground(Globals.COLOR_PROMPT);
		btnRemProject.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnRemProject.setEnabled(false);
		
		btnRemProject.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) 
			{
				if (currentProject.equals("")) return; // it shouldn't happen but...
				String dbName = Globals.DBprefix + currentProject;
				boolean bInDB = hostCfg.existsDB(dbName);
				
				if (bInDB)
				{
					int choice = JOptionPane.showConfirmDialog(null, "Remove database?", "Remove the database?",
							JOptionPane.YES_NO_OPTION);
					if (choice == JOptionPane.YES_OPTION)
					{
						try
						{
							if (hostCfg.deleteDB(dbName))
								System.out.println("Complete delete " + dbName);
						}
						catch(Exception e)
						{
							ErrorReport.infoBox("There was a problem deleting the database.");
							e.printStackTrace();
						}
					}
				}
				int choice = JOptionPane.showConfirmDialog(null, "Remove project directory from disk?", "Remove from disk?",
						JOptionPane.YES_NO_OPTION);
				if (choice == JOptionPane.YES_OPTION)
				{
					File projDir = new File(Globals.projDir + "/" + currentProject);
					if (projDir.isDirectory())
					{
						deleteDir(projDir);
					}
					updateProjList(cmbProjects,"");
				}
			}
		});
		pRow.add(Box.createHorizontalStrut(10));
		pRow.add(btnRemProject);
		
		// overview 
		btnOverProject.setEnabled(false);
		btnOverProject.setBackground(Globals.COLOR_HELP);
		btnOverProject.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String tmp = ((String)cmbProjects.getSelectedItem()); 
				if (!tmp.endsWith("*")) LogTime.infoBox(currentProject + " database does not exist");
				else {
					try {
						DBConn mdb = hostCfg.openDB(currentProject);
						ResultSet rs = mdb.executeQuery("Select overview, remark from metaData");
						if (rs.next()) {
							String ov = rs.getString(2) + "\n"  + rs.getString(1);
							ViewTextPane.displayInfoMonoSpace(getInstance(), "Overview for " + 
									currentProject, ov, false, true);
						}
						else LogTime.infoBox(currentProject + " problem getting overview");
					}
					catch (Exception ee) {ErrorReport.prtError(ee, "Cannot open database " + currentProject);}
				}
			}
		});
		btnOverProject.setAlignmentX(RIGHT_ALIGNMENT);
		pRow.add(Box.createHorizontalStrut(5));
		pRow.add(btnOverProject);
				
		// Help
		final String [] t = {"Help", "runAW", "Files", "Pipeline"};
		JComboBox btnHelps = new JComboBox(t);
		btnHelps.setSelectedIndex(0);
		btnHelps.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				JComboBox jcmbType = (JComboBox) e.getSource();
				String cmbType = (String) jcmbType.getSelectedItem();
				if (cmbType.equals(t[1])) 
					ViewTextPane.displayHTML(getThis(), "Help runAW", htmlFile);
				else if (cmbType.equals(t[2]))
					ViewTextPane.displayHTML(getThis(), "Help Files", htmlFile2);
				else if (cmbType.equals(t[3]))
					ViewTextPane.displayHTML(getThis(), "Help Pipeline", htmlFile3);
			}
		});
		
		pRow.add(Box.createHorizontalStrut(90));
		pRow.add(btnHelps);
		pRow.add(Box.createHorizontalGlue());
		
		pRow.setMaximumSize(pRow.getPreferredSize());
		mainPanel.add(pRow);		
	}
	/****************************************
	 * Parameter buttons
	 */
	private void create2ParameterRow(String label, JPanel mainPanel)
	{
		JPanel pRow = CreateJ.panelLabelLine(label);
	
		btnSaveProject.setEnabled(false);
		btnSaveProject.setBackground(Globals.COLOR_PROMPT);
		btnSaveProject.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnSaveProject.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				saveCfg(); 
				bNewProject=false;
			}
		});
		pRow.add(Box.createHorizontalStrut(10));
		pRow.add(btnSaveProject);
		
		JButton btnClearProject = new JButton("Clear");
		btnClearProject.setEnabled(true);
		btnClearProject.setBackground(Globals.COLOR_PROMPT);
		btnClearProject.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnClearProject.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				clearPanels(); 
			}
		});
		pRow.add(Box.createHorizontalStrut(10));
		pRow.add(btnClearProject);
		
		btnCopyProject.setEnabled(false);
		btnCopyProject.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnCopyProject.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser j = new JFileChooser();
				j.setFileSelectionMode(JFileChooser.FILES_ONLY);
				FileNameExtensionFilter filter = new FileNameExtensionFilter("AW.cfg", "cfg");
				j.setFileFilter(filter);
			    j.setCurrentDirectory(new java.io.File("." + "/" + Globals.projDir));
			    j.setDialogTitle("Select existing AW.cfg");

				Integer opt = j.showSaveDialog(null);
				if (opt == JFileChooser.APPROVE_OPTION)
				{
					try
					{
						String file = j.getSelectedFile().getCanonicalPath();
						System.out.println("Copy " + file);
						String savCurrent = currentProject;
						currentProject = file;
				  		bNewProject = false;
						addCfgToUI();
						currentProject = savCurrent;
						enableControls(true);
					}
					catch(Exception ee){ErrorReport.prtError(ee,  "Error copying AW.cfg");}
				}
			}
		});
		pRow.add(Box.createHorizontalStrut(10));
		pRow.add(btnCopyProject);  
		pRow.setMaximumSize(pRow.getPreferredSize());
		pRow.add(Box.createHorizontalGlue());
			
		pRow.setMaximumSize(pRow.getPreferredSize());
		pRow.add(Box.createHorizontalGlue());
		
		mainPanel.add(pRow);		
	}
////////////////////////////////////////////
//				 CONDITIONS
//////////////////////////////////////////
	private void create3Conditions(JPanel mainPanel) {
		mainPanel.add(Box.createVerticalStrut(5));
		
		JPanel pL = CreateJ.panelLabelLine("Condition #1:");
		pL.add(cond1Txt);
		cond1Txt.setText("Strain");
		pL.add(Box.createHorizontalStrut(5));
		pL.add(new JLabel("*see Help"));
		pL.setMaximumSize(pL.getPreferredSize());
		mainPanel.add(pL);
		
		JButton addC1 = new JButton("Add Row"); 
		addC1.setBackground(Globals.COLOR_MENU);
		
		final JButton remC1 = new JButton("Remove");
		remC1.setEnabled(false);
		remC1.setBackground(Globals.COLOR_MENU);
		
		JPanel pButton = CreateJ.panelPage();
		pButton.add(addC1);
		pButton.add(Box.createVerticalStrut(5));
		pButton.add(remC1);
		
		strainTable.setAutoCreateColumnsFromModel( true );

		JScrollPane tblPane = CreateJ.scrollPane(tableWidth, tableHeight);
		tblPane.setViewportView(strainTable);
		strainTable.getTableHeader().setBackground(Color.WHITE);
		tblPane.setColumnHeaderView(strainTable.getTableHeader());
		
		addC1.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				strainTblModel.addRow();
			}
		});
		remC1.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				strainTblModel.removeRow(strainTable.getSelectedRow());
			}
		});
		strainTable.getSelectionModel().addListSelectionListener(new ListSelectionListener(){
	        public void valueChanged(ListSelectionEvent event) {
		        	int row = strainTable.getSelectedRow();
		        	if (row == -1) remC1.setEnabled(false);
		        	else remC1.setEnabled(true);
	        }
	    });
		strainTable.putClientProperty("terminateEditOnFocusLost", true);
		strainTable.setBackground(Color.white);
		
		JPanel pTbl = CreateJ.panelPage();
		pTbl.add(tblPane);
		pTbl.add(Box.createVerticalGlue());
		pTbl.setMaximumSize(pTbl.getPreferredSize());
		
		JPanel pFull = CreateJ.panelLine();
		pFull.add(pTbl);
		pFull.add(Box.createHorizontalStrut(5));
		pFull.add(pButton);
		pFull.setMaximumSize(pFull.getPreferredSize());
		
		mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(pFull);
		
		mainPanel.add(Box.createVerticalStrut(10));
		
		// CONDITION 2
		JPanel pL2 = CreateJ.panelLabelLine("(Optional) Condition #2:   ");
		pL2.add(cond2Txt);
		pL2.add(Box.createHorizontalStrut(5));
		pL2.add(new JLabel("*see Help"));
		pL2.setMaximumSize(pL2.getPreferredSize());
		mainPanel.add(pL2);
		
		final JButton addC2 = new JButton("Add Row"); 
		addC2.setBackground(Globals.COLOR_MENU);
		
		final JButton remC2 = new JButton("Remove");
		remC2.setBackground(Globals.COLOR_MENU);
		remC2.setEnabled(false);
		
		JPanel pButton2 = CreateJ.panelPage();
		pButton2.add(addC2);
		pButton2.add(Box.createVerticalStrut(5));
		pButton2.add(remC2);
				
        condTable.setAutoCreateColumnsFromModel( true );

		JScrollPane tblPane2 = CreateJ.scrollPane(tableWidth, tableHeight);
		tblPane2.setViewportView(condTable);
		condTable.getTableHeader().setBackground(Color.WHITE);
		tblPane2.setColumnHeaderView(condTable.getTableHeader());
    	
		addC2.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				condTblModel.addRow();
			}
		});
		remC2.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				condTblModel.removeRow(condTable.getSelectedRow());
			}
		});
		condTable.getSelectionModel().addListSelectionListener(new ListSelectionListener(){
	        public void valueChanged(ListSelectionEvent event) {
	        		int row = condTable.getSelectedRow();
	        		if (row == -1) remC2.setEnabled(false);
	        		else remC2.setEnabled(true);
	        }
	    });
		condTable.putClientProperty("terminateEditOnFocusLost", true);
		condTable.setBackground(Color.white);
		
		JPanel pTbl2 = CreateJ.panelPage();
		pTbl2.add(tblPane2);
		pTbl2.add(Box.createVerticalGlue());
		
		JPanel pFull2 = CreateJ.panelLine();
		pFull2.add(pTbl2);
		pFull2.add(Box.createHorizontalStrut(10));
		pFull2.add(pButton2);
		pFull2.setMaximumSize(pFull2.getPreferredSize());
	
		mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(pFull2);	
	}
	
////////////////////////////////////////////
//				 VARIANT FILE SECTION
///////////////////////////////////////////
	private void create4VarFiles(JPanel mainPanel) {
		JPanel pL = CreateJ.panelLabelLine("Variant calls - file or directory of files (.vcf)");
		mainPanel.add(pL);

		JButton annBrowse = new JButton("Browse");
		annBrowse.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				JFileChooser j = new JFileChooser();
				j.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
				if (lastDir.equals("")) j.setCurrentDirectory(new java.io.File("."));
			    else j.setCurrentDirectory(new java.io.File(lastDir));
			    j.setDialogTitle("Select file or directory of variant files (.vcf)");

				Integer opt = j.showSaveDialog(null);
				if (opt == JFileChooser.APPROVE_OPTION)
				{
					try
					{
						lastDir = j.getSelectedFile().getCanonicalPath();
						varText.setText(lastDir);
					}
					catch(Exception ee){ErrorReport.prtError(ee, errorMsg);}
				}
			}
		});
		JPanel pRow = CreateJ.panelLine();
		pRow.add(varText);
		pRow.add(Box.createHorizontalStrut(5));
		pRow.add(annBrowse);
		pRow.add(Box.createHorizontalGlue());
		pRow.setMaximumSize(pRow.getPreferredSize());
		mainPanel.add(pRow);
	
		// count files
		mainPanel.add(Box.createVerticalStrut(10));
		JPanel pL2 = CreateJ.panelLabelLine("Variant coverage - directory of files (.bed)");
		mainPanel.add(pL2);

		JButton annBrowse2 = new JButton("Browse");
		annBrowse2.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				JFileChooser j = new JFileChooser();
				j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				if (lastDir.equals("")) j.setCurrentDirectory(new java.io.File("."));
			    else j.setCurrentDirectory(new java.io.File(lastDir));
			    j.setDialogTitle("Select directory of files (.bed)");

				Integer opt = j.showSaveDialog(null);
				if (opt == JFileChooser.APPROVE_OPTION)
				{
					try
					{
						lastDir = j.getSelectedFile().getCanonicalPath();
						varCntText.setText(lastDir);
					}
					catch(Exception ee){ErrorReport.prtError(ee, errorMsg);}
				}
			}
		});
		JPanel pRow2 = CreateJ.panelLine();
		pRow2.add(varCntText);
		pRow2.add(Box.createHorizontalStrut(5));
		pRow2.add(annBrowse2);
		pRow2.add(Box.createHorizontalGlue());
		pRow2.setMaximumSize(pRow2.getPreferredSize());
		mainPanel.add(pRow2);
	
		// variant annotation
		mainPanel.add(Box.createVerticalStrut(10));
		JPanel pL3 = CreateJ.panelLabelLine(" (Optional) Variant effects -- file or directory of files (.vcf, EVP or snpEFF)");
		mainPanel.add(pL3);

		JButton annBrowse3 = new JButton("Browse");
		annBrowse3.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				JFileChooser j = new JFileChooser();
				j.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
				if (lastDir.equals("")) j.setCurrentDirectory(new java.io.File("."));
			    else j.setCurrentDirectory(new java.io.File(lastDir));
			    j.setDialogTitle("Select file or directory of variant anno files (.vcf)");

				Integer opt = j.showSaveDialog(null);
				if (opt == JFileChooser.APPROVE_OPTION)
				{
					try
					{
						lastDir = j.getSelectedFile().getCanonicalPath();
						varAnnoText.setText(lastDir);
					}
					catch(Exception ee){ErrorReport.prtError(ee, errorMsg);}
				}
			}
		});
		JPanel pRow3 = CreateJ.panelLine();
		pRow3.add(varAnnoText);
		pRow3.add(Box.createHorizontalStrut(5));
		pRow3.add(annBrowse3);
		pRow3.add(Box.createHorizontalGlue());
		pRow3.setMaximumSize(pRow3.getPreferredSize());
		mainPanel.add(pRow3);
	}
////////////////////////////////////////////
//				 GENOME Files
///////////////////////////////////////////
	private void create5GenomeFiles(JPanel mainPanel) 
	{
		JPanel pL = CreateJ.panelLabelLine("Genome annotation - file (.gtf, .gff)");
		mainPanel.add(pL);
		
		JButton annBrowse = new JButton("Browse");
		annBrowse.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				JFileChooser j = new JFileChooser();
				j.setFileSelectionMode(JFileChooser.FILES_ONLY);
				if (lastDir.equals("")) j.setCurrentDirectory(new java.io.File("."));
			    else j.setCurrentDirectory(new java.io.File(lastDir));
			    j.setDialogTitle("Select genome annotation file");
			    FileNameExtensionFilter filter = new FileNameExtensionFilter("ANNO FILES", "gtf","gff");
			    j.setFileFilter(filter);

				Integer opt = j.showSaveDialog(null);
				if (opt == JFileChooser.APPROVE_OPTION)
				{
					try
					{
						lastDir = j.getSelectedFile().getCanonicalPath();
						genomeAnnoText.setText(lastDir);
					}
					catch(Exception ee){ErrorReport.prtError(ee, errorMsg);}
				}
			}
		});
		JPanel pRow = CreateJ.panelLine();
		pRow.add(genomeAnnoText);
		pRow.add(Box.createHorizontalStrut(5));
		pRow.add(annBrowse);
		pRow.add(Box.createHorizontalGlue());
		pRow.setMaximumSize(pRow.getPreferredSize());
		mainPanel.add(pRow);
		
		mainPanel.add(Box.createVerticalStrut(10));
		
		// Genome sequence files
		JPanel pL2 = CreateJ.panelLabelLine(
				"Genome sequence - directory of chromosome files (.fa, .fasta) ");
		mainPanel.add(pL2);
			
		JButton genBrowse = new JButton("Browse");
		genBrowse.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				JFileChooser j = new JFileChooser();
				j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			    if (lastDir.equals("")) j.setCurrentDirectory(new java.io.File("."));
			    else j.setCurrentDirectory(new java.io.File(lastDir));
			    j.setDialogTitle("Select genome directory");

				Integer opt = j.showSaveDialog(null);
				if (opt == JFileChooser.APPROVE_OPTION)
				{
					try
					{
						lastDir = j.getSelectedFile().getCanonicalPath();
						genomeSeqText.setText(lastDir);
					}
					catch(Exception ee)
					{
						ErrorReport.prtError(ee, errorMsg);
					}
				}
			}
		});
		JPanel pRow2 = CreateJ.panelLine();
		pRow2.add(genomeSeqText);
		pRow2.add(Box.createHorizontalStrut(5));
		pRow2.add(genBrowse);
		pRow2.add(Box.createHorizontalGlue());
		pRow2.setMaximumSize(pRow2.getPreferredSize());
		
		mainPanel.add(pRow2);
		mainPanel.add(Box.createVerticalStrut(10));
		
		// NCBI functional annotation files
		JPanel pL3 = CreateJ.panelLabelLine(
				"(Optional) NCBI functional annotation - file");
		mainPanel.add(pL3);
			
		JButton ncbiBrowse = new JButton("Browse");
		ncbiBrowse.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				JFileChooser j = new JFileChooser();
				j.setFileSelectionMode(JFileChooser.FILES_ONLY);
			    if (lastDir.equals("")) j.setCurrentDirectory(new java.io.File("."));
			    else j.setCurrentDirectory(new java.io.File(lastDir));
			    j.setDialogTitle("Select NCBI file");

				Integer opt = j.showSaveDialog(null);
				if (opt == JFileChooser.APPROVE_OPTION)
				{
					try
					{
						lastDir = j.getSelectedFile().getCanonicalPath();
						genomeNCBIText.setText(lastDir);
					}
					catch(Exception ee){ErrorReport.prtError(ee, errorMsg);}
				}
			}
		});
		JPanel pRow3 = CreateJ.panelLine();
		pRow3.add(genomeNCBIText);
		pRow3.add(Box.createHorizontalStrut(5));
		pRow3.add(ncbiBrowse);
		pRow3.add(Box.createHorizontalGlue());
		pRow3.setMaximumSize(pRow3.getPreferredSize());
		
		mainPanel.add(pRow3);
	}
	//////////////////////////////////////////////
	/// for express files
	private void create6GeneCntFiles(JPanel mainPanel) {
		
		JPanel pL = CreateJ.panelLabelLine(
				"(Optional) Transcript counts directory");
		mainPanel.add(pL);
			
		JButton browse = new JButton("Browse");
		browse.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				JFileChooser j = new JFileChooser();
				j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			    if (lastDir.equals("")) j.setCurrentDirectory(new java.io.File("."));
			    else j.setCurrentDirectory(new java.io.File(lastDir));
			    j.setDialogTitle("Select transcript count directory");

				Integer opt = j.showSaveDialog(null);
				if (opt == JFileChooser.APPROVE_OPTION)
				{
					try
					{
						lastDir = j.getSelectedFile().getCanonicalPath();
						transCntText.setText(lastDir);
					}
					catch(Exception ee){ErrorReport.prtError(ee, errorMsg);}
				}
			}
		});
		JPanel row = CreateJ.panelLine();
		row.add(transCntText);
		row.add(Box.createHorizontalStrut(5));
		row.add(browse);
		row.add(Box.createHorizontalGlue());
		row.setMaximumSize(row.getPreferredSize());
		
		mainPanel.add(row);
	}
////////////////////////////////////////////
// BUILD 
///////////////////////////////////////////
	private void create7build(String label, JPanel mainPanel) 
	{
		JPanel pRow = CreateJ.panelLabelLine(label);
		pRow.add(btnBuildDatabase);
	
		btnBuildDatabase.setEnabled(false);
		btnBuildDatabase.setBackground(Globals.COLOR_LAUNCH);
		btnBuildDatabase.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnBuildDatabase.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (currentProject==null) {
					ErrorReport.infoBox("Select or create a project");
					return;
				}
				saveCfg();
				Bmain.build(currentProject); 
			}
		});
		pRow.add(Box.createHorizontalStrut(15));
		pRow.add(new JLabel("(see terminal, you may be prompted for more information)") );
		
		mainPanel.add(pRow);
		mainPanel.add(Box.createVerticalGlue());
	}

	/********** end create panel ***************/
	private JFrame getThis() { return this;}
	
	/*********************************************
	 * EditTableModel
	 */
	private class EditTableModel extends AbstractTableModel
	{
		private static final long serialVersionUID = -8216104747672688135L;
		public EditTableModel(String[] names, int nRows)
		{
			colNames = names;
			this.nRows = nRows;
			rowData = new String[nRows][names.length];
			for (int i = 0; i < names.length; i++)
			{
				for (int j = 0; j < nRows; j++) rowData[j][i] = "";
			}
		}
		public void clearData()
		{
			nRows=0;
			rowData = new String[nRows][colNames.length];
			fireTableDataChanged();
		}
        public int getColumnCount() { 
            return colNames.length; 
        }
        public int getRowCount() { 
            return nRows;
        }
        public Object getValueAt(int row, int col) { 
            return rowData[row][col]; 
        }
        public boolean isCellEditable(int row, int col){ 
        		return true; 
        	}
        public void setValueAt(Object value, int row, int col) {
        		rowData[row][col] = (String) value;
        		fireTableCellUpdated(row, col);
        }
    		public String getColumnName(int columnIndex) {
    			return colNames[columnIndex];
    		}
	    	public void addRow()
	    	{
	    		nRows++;
	    		String[][] newRowData = new String[nRows][colNames.length];
			for (int i = 0; i < colNames.length; i++)
			{
				for (int j = 0; j < nRows; j++)
				{
					if (j < nRows-1) newRowData[j][i] = rowData[j][i];
					else newRowData[j][i] = "";
				}
			}
			rowData = newRowData;
			fireTableDataChanged();
	    	}
	    	// add data to first empty row, or a new row
	    	public void addRow(String[] entries)
	    	{
	    		int nextRow = -1;
	    		for (int r = 0; r < nRows; r++)
	    		{
	    			if (rowData[r][0].equals(""))
	    			{
	    				nextRow = r;
	    				break; 
	    			}
	    		}
	    		if (nextRow == -1)
	    		{
	    			addRow();
	    			nextRow = nRows-1;
	    		}
	    		if (rowData[nextRow].length != entries.length)
	    		{
	    			String estr = Format.strArrayJoin(entries, " ");
	    			ErrorReport.infoBox("Wrong number of entries in condition line:\n" + estr);
	    			return;
	    		}
	    		else
	    		{
	    			for (int c = 0; c < entries.length; c++)
	    				rowData[nextRow][c] = entries[c];
	    		}
	    		fireTableDataChanged();
	    	}
	    	public void removeRow(int row)
	    	{
	    		nRows--;
	    		String[][] newRowData = new String[nRows][colNames.length];
			for (int i = 0; i < colNames.length; i++)
			{
				for (int j = 0, jNew=0; j <= nRows; j++)
				{
					if (j == row) continue;
					newRowData[jNew][i] = rowData[j][i];
					jNew++;
				}
			}
			rowData = newRowData;
			fireTableDataChanged();
		}
		String[] colNames;
		String[][] rowData;
		int nRows;
	};

	// Scan the projects directories and see which ones are
	// in the DB already
	private void updateProjList(JComboBox cmb, String selected)
	{
		Vector<String> projects = new Vector<String>();
		projects.add("Select a project");
		int sel_idx=0;
		
		File pf = new File(Globals.projDir);
		int idx = 1;
		for (File f : pf.listFiles())
		{
			if (f.isDirectory())
			{
				String pname = f.getName();
				String dbname = Globals.DBprefix + pname;
				if (hostCfg.existsDB(dbname))
				{
					pname += "*";
				}
				projects.add(pname);
				if (pname.equals(selected)) 
				{
					sel_idx=idx;
				}
				idx++;
			}
		}
		cmb.setModel(new JComboBox(projects).getModel());
		cmb.setSelectedIndex(sel_idx);
	}
	/***********************************************
	 * call cfg.loadCfg to read it, then get into from cfg
	 */
	private boolean addCfgToUI()
	{
		if (currentProject.equals("")) return false;
		if (bNewProject) return false;
		
		try {
			Cfg awcfg = new Cfg();
			if (!awcfg.readCfg(currentProject, false /*don't print */)) {
				ErrorReport.infoBox("Errors in " + currentProject + " will load anyway");
			}
			clearPanels();
			
			cond1Txt.setText(awcfg.getCond1());
			cond2Txt.setText(awcfg.getCond2());
			
			Vector <String> condVal1 = awcfg.getCondVal1();
			for (String line : condVal1) {
				String [] tok = line.split(":");
				strainTblModel.addRow(tok);
			}
			Vector <String> condVal2 = awcfg.getCondVal2();
			for (String line : condVal2) {
				String [] tok = line.split(":");
				condTblModel.addRow(tok);
			}
			
			Vector <String> fileVec = awcfg.getFileVec();
			for (String line : fileVec) {
				String [] tok = line.split(" ");
				if (tok[0].equalsIgnoreCase(Cfg.keyVARCOV)) 		varCntText.setText(tok[1]);
				else if (tok[0].equalsIgnoreCase(Cfg.keyVARIANT))varText.setText(tok[1]);
				else if (tok[0].equalsIgnoreCase(Cfg.keyVARANNO))varAnnoText.setText(tok[1]);
				else if (tok[0].equalsIgnoreCase(Cfg.keyGENOME)) genomeSeqText.setText(tok[1]);
				else if (tok[0].equalsIgnoreCase(Cfg.keyGTF))	genomeAnnoText.setText(tok[1]);
				else if (tok[0].equalsIgnoreCase(Cfg.keyNCBI))	genomeNCBIText.setText(tok[1]);
				else if (tok[0].equalsIgnoreCase(Cfg.keyTRANSCNT))	transCntText.setText(tok[1]);
				else System.out.println("runAW does not recognize: " + line);
			}
			return true;
		}
		catch(Exception e)
		{
			ErrorReport.infoBox("There was a problem reading " + Globals.cfgFile + " for project " + currentProject);
			e.printStackTrace();
			return false;
		}
	}
	private void clearPanels()
	{
		strainTblModel.clearData(); 
		condTblModel.clearData();
		cond1Txt.setText("");
		cond2Txt.setText("");
		
		varText.setText("");
		varAnnoText.setText("");
		varCntText.setText("");
		
		genomeSeqText.setText("");
		genomeAnnoText.setText("");
		refText.setText("");
		altText.setText("");
	}
	private JFrame getInstance() { return this; }

	/**************************************************
	 * Check to make sure everything is filled in.
	 * The files are valided in Cfg.java so that it also works from loadAW
	 */
	private boolean validateData()
	{
		//
		// Conditions: check that the conditions are filled in correctly
		//
		bHaveCondition2 = false;
		if (condTable.isEditing()) condTable.getCellEditor().stopCellEditing();
		if (strainTable.isEditing()) strainTable.getCellEditor().stopCellEditing();
		
		Vector<String> c1list = new Vector<String>();
		Vector<String> c2list = new Vector<String>();
		Vector<String> clist = new Vector<String>();
		
		for (int r = 0; r < strainTblModel.nRows; r++)
		{
			if (!strainTblModel.rowData[r][1].equals(""))
			{
				String name = strainTblModel.rowData[r][0].trim();
				String abbr = strainTblModel.rowData[r][1].trim();
				String isHybrid = strainTblModel.rowData[r][2].trim();
				if (!valNameAbbr(r, "#1", name, abbr)) return false;
				
				if (c1list.contains(abbr)) {
					ErrorReport.infoBox("Condition #1: Row#" + (r+1) + "Duplicate Abbr " + abbr);
					return false;
				}
				if (!isHybrid.equalsIgnoreCase("yes") && !isHybrid.equalsIgnoreCase("no")) {
					ErrorReport.infoBox("Condition #1: isHybrid " + abbr + " should be 'yes' or 'no'.");
				}
				c1list.add(abbr);
			}
		}
		for (int r = 0; r < condTblModel.nRows; r++)
		{
			if (!condTblModel.rowData[r][1].equals(""))
			{				
				String name = condTblModel.rowData[r][0].trim();
				String abbr = condTblModel.rowData[r][1].trim();
				if (!valNameAbbr(r, "#2", name, abbr)) return false;
				
				if (c2list.contains(abbr)) {
					ErrorReport.infoBox("Condition #2: Row#" + (r+1) + "Duplicate Abbr " + abbr);
					return false;
				}
				c2list.add(abbr);
			}		
		}
		clist.addAll(c1list); clist.addAll(c2list);
			
		if (c1list.size() == 0 && c2list.size() > 0)
		{
			ErrorReport.infoBox("Since you only have one set of conditions, " +
					" please enter them in the 'Condition 1' section " +
						" and leave 'Condition 2' empty");
			return false;
		}
		else if (c1list.size() == 0)
		{
			ErrorReport.infoBox("Please enter at least one row for Condition #1");
			return false;
			
		}		
		String c1Name = cond1Txt.getText().trim();
		if (!c1Name.matches("\\w+"))
		{
			ErrorReport.infoBox("Please a value for condition #1 using letters, numbers, underscores.");
			return false;
		}
		if (c2list.size() > 0)
		{
			String c2Name = cond2Txt.getText().trim();
			if (!c2Name.matches("\\w+"))
			{
				ErrorReport.infoBox("Please enter a value for condition #2 using letters, numbers, underscores.");
				return false;
			}
			bHaveCondition2 = true;
		}	
		// 
		// Check that variant files exist
		//
		String file = varText.getText().trim();
		if (file.equals(""))
		{
			ErrorReport.infoBox("Please enter one or more variant VCF file(s)");
			return false;
		}
		else {
			File f = new File(file);
			if (!f.exists()) {
				ErrorReport.infoBox("Variant file or directory does not exist " + file);
				return false;
			}
			if (f.isFile() && !file.endsWith(".vcf")) {
				ErrorReport.infoBox("File does not end with .vcf " + file);
				return false;
			}
		}
		file = varAnnoText.getText().trim();
		if (!file.equals("") && !file.startsWith("#"))
		{
			File f = new File(file);
			if (!f.exists()) {
				ErrorReport.infoBox("Variant Effect file or directory does not exist " + file);
				return false;
			}
			if (f.isFile() && !file.endsWith(".vcf")) {
				ErrorReport.infoBox("File does not end with .vcf " + file);
				return false;
			}
		}
		//
		// Check that a variant count directory exists
		// 
		String varPath = varCntText.getText().trim();
		if (varPath.equals("")) {
			ErrorReport.infoBox("Please enter variant count directory");
			return false;
		}
		else {
			File vf = new File(varPath);
			if (!vf.isDirectory())
			{
				ErrorReport.infoBox("Variant count directory does not exist: " + varPath);
				return false;
			}
		}	
		//
		// Check that there is a GTF file
		//
		String annPath = genomeAnnoText.getText().trim();
		if (annPath.equals(""))
		{
			ErrorReport.infoBox("Please enter a genome annotation file");
			return false;
		}
		else
		{
			File vf = new File(annPath);
			if (!vf.isFile())
			{
				ErrorReport.infoBox("Unable to find the genome annotation file: " + annPath);
				return false;
			}
		}	
		// 
		// Check that genome directory exists
		//
		String genPath = genomeSeqText.getText().trim();
		if (!genPath.equals("") && !genPath.startsWith("#"))
		{
			File vf = new File(genPath);
			if (!vf.isDirectory())
			{
				ErrorReport.infoBox("Unable to find the genome directory: " + vf.getAbsolutePath());
				return false;
			}
		}
		// 
		// Check that ncbi file
		//
		String ncbiFile = genomeNCBIText.getText().trim();
		if (!ncbiFile.equals("") && !ncbiFile.startsWith("#"))
		{
			File vf = new File(ncbiFile);
			if (!vf.isFile())
			{
				ErrorReport.infoBox("Unable to find the NCBI file: " + vf.getAbsolutePath());
				return false;
			}
		}
		// 
		// Check that ncbi file
		//
		String geneCntDir = transCntText.getText().trim();
		if (!geneCntDir.equals("") && !geneCntDir.startsWith("#"))
		{
			File vf = new File(geneCntDir);
			if (!vf.isDirectory())
			{
				ErrorReport.infoBox("Unable to find the gene/trans count directory: " + vf.getAbsolutePath());
				return false;
			}
		}
		System.err.println("File validations passed.");
		
		return true;
	}
	
	private boolean valNameAbbr(int r, String cond, String name, String abbr) {
		if (name.equals(""))
		{
			ErrorReport.infoBox("Condition" + cond + ": Row#" + (r+1) + " needs a name");
			return false;
		}
		if (name.contains(" ") || name.contains("\"") || name.contains("'"))
		{
			ErrorReport.infoBox("Condition" + cond + ": Row#" + (r+1) + " Name '" + name + 
					"' should not contain blanks or quotes");
			return false;	
		}
		if (abbr.equals(""))
		{
			ErrorReport.infoBox("Condition" + cond + ": Row#" + (r+1) + " needs an abbr");
			return false;
		}
		// tested here and in Cfg.java (in case run from command line
		if (!abbr.matches("\\w+") || abbr.contains("_"))
		{
			ErrorReport.infoBox("Condition" + cond + ": Row#" + (r+1) + " Abbr '" + abbr + 
					"' should be composed of letters and numbers");
			return false;	
		}
		return true;
	}
	/**********************************************
	 * save AW.cfg
	 */
	private void saveCfg()
	{
		if (currentProject==null) {
			ErrorReport.infoBox("Select or create a project");
			return;
		}
		if (!validateData()) {
			if (!LogTime.yesNoDialog(null, "Yes or No", "One or more errors. Save file anyway?")) {
				System.err.println("Cancel saving AW.cfg");
				return;
			}
		}
		String path = Globals.projDir + "/" + currentProject + "/" + Globals.cfgFile;
		System.out.println("Save " + path);
		bNewProject = false; 
		try
		{
			File f = new File(path);
			if (f.exists()) f.delete();
			
			f.createNewFile();
			BufferedWriter bw = new BufferedWriter(new FileWriter(f));
			
			bw.write("# Project " + currentProject + "\n");
			bw.write("> " + Cfg.keyCOND1 + " " + cond1Txt.getText() + "\n");
			for (int r = 0; r < strainTblModel.nRows; r++)
			{
				if (!strainTblModel.rowData[r][1].trim().equals(""))
				{
					String line = Format.strArrayJoin(strainTblModel.rowData[r], "\t");
					bw.write(line);
					bw.newLine();
				}
			}
			bw.newLine();
			
			if (bHaveCondition2)
			{
				bw.write("> " + Cfg.keyCOND2 + " " + cond2Txt.getText() + "\n");
				for (int r = 0; r < condTblModel.nRows; r++)
				{
					if (!condTblModel.rowData[r][1].trim().equals(""))
					{
						String line = Format.strArrayJoin(condTblModel.rowData[r], "\t");
						bw.write(line);
						bw.newLine();
					}
				}
				bw.newLine();				
			}
			bw.write("> " + Cfg.keyFILES + "\n");
			
			bw.write(Cfg.keyVARIANT + " " + varText.getText() + "\n\n");
			
			bw.write(Cfg.keyVARCOV + " " + varCntText.getText() + "\n\n");
			
			if (!varAnnoText.getText().equals(""))
				bw.write(Cfg.keyVARANNO + " " + varAnnoText.getText() + "\n\n");
			
			bw.write(Cfg.keyGTF + " " + genomeAnnoText.getText() + "\n\n");
			
			if (!genomeSeqText.getText().equals(""))
				bw.write(Cfg.keyGENOME + " " + genomeSeqText.getText() + "\n\n");
			
			if (!genomeNCBIText.getText().equals(""))
				bw.write(Cfg.keyNCBI + " " + genomeNCBIText.getText() + "\n\n");
			
			if (!transCntText.getText().equals(""))
				bw.write(Cfg.keyTRANSCNT + " " + transCntText.getText() + "\n\n");
		
			bw.close();
		}
		catch(Exception e)
		{
			ErrorReport.infoBox("Unable to write the configuration file:" + path);
			ErrorReport.prtError(e, errorMsg + " Save AW.cfg");
		}
	}

	public static void clearDir(File d)
	{
		if (d.isDirectory())
		{
			for (File f : d.listFiles())
			{
				if (f.isDirectory() && !f.getName().equals(".") && !f.getName().equals("..")) 
				{
					clearDir(f);
				}
				f.delete();
			}
		}
		//WN why needed?? checkCreateDir(d);
	}
	public static void deleteDir(File d)
	{
		try {
			clearDir(d);
			d.delete();
			if (d.exists()) {
				System.out.println("Cannot delete directory " + d.getName() + " -- may have hidden files ");
			}
		} catch (Exception e) {ErrorReport.prtError(e,  "Could not delete " + d.getAbsolutePath());}
	}
	private void horizSeparator(JPanel mainPanel) // need to limit its size
	{
		mainPanel.add(Box.createVerticalStrut(10));
		JSeparator separator = new JSeparator(JSeparator.HORIZONTAL);
		Dimension size = new Dimension(
		    separator.getMaximumSize().width,
		    separator.getPreferredSize().height);
		separator.setMaximumSize(size);
		mainPanel.add(separator);
		mainPanel.add(Box.createVerticalStrut(10));
	}
	private void enableControls(boolean enable)
	{
		btnRemProject.setEnabled(enable);
		btnSaveProject.setEnabled(enable);
		btnCopyProject.setEnabled(enable);
		btnOverProject.setEnabled(enable);
		btnBuildDatabase.setEnabled(enable);
	}
}
