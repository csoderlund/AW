package viewer.panels;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.ResultSet;
import java.util.TreeMap;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.JScrollPane;

import database.DBConn;
import database.MetaData;

import util.ErrorReport;
import util.Globals;
import util.LogTime;
import viewer.ViewerFrame;
import viewer.controls.*;

public class TransQuery extends JPanel {
	private static final long serialVersionUID = -7309717271965938526L;
	
	//Labels for the collapsible panels
	private static final String [] SECTIONS = { 
		"Basic", "General",  "Variant", "Library", "Complex queries"};
	private static final String [] SECTIONS_DESC = { "", "", "", "", ""};
	
	private static final String htmlFile = "/html/TransQuery.html";
	
	//Counter for display and to keep name of results unique
	private static int resultCount = 0;
		
	/************************************************
	 * Created once on startup, and called by tab on Left from ViewerFrame
	 */
	public TransQuery(ViewerFrame vFrame) {
		theViewerFrame = vFrame;
		theMetaData = vFrame.getMetaData();
		chrStr = theMetaData.getChr();
		chrRoot = theMetaData.getChrRoot();
		strAbbr  = theMetaData.getStrAbbv();
		tisAbbr  = theMetaData.getTisAbbv();
		hasCond2 = theMetaData.hasCond2();
		setListeners();
				
		removeAll();
		setBackground(Globals.COLOR_BG);
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		createButtonPanel(); // theButtonPanel at top
		add(buttonPanel);
		add(Box.createVerticalStrut(15));	
			
		mainPanel = CreateJ.panelPage();
		createSections();	
		for(int x=0; x<theSections.length; x++) {
			theSections[x].expand();
			mainPanel.add(theSections[x]);
		}
		
		Dimension d = mainPanel.getMaximumSize();
		d.height = mainPanel.getPreferredSize().height;
		mainPanel.setMaximumSize(d);
		
		JScrollPane sPane =  new JScrollPane ( mainPanel);
		sPane.setBorder( null );
		sPane.setPreferredSize(theViewerFrame.getSize());
		sPane.getVerticalScrollBar().setUnitIncrement(15);
		sPane.setAlignmentX(Component.LEFT_ALIGNMENT);
		sPane.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(sPane);
	}
	
	/******************************************
	 * Create controls for top of panel
	 */
	private void createButtonPanel() {
		buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
		buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttonPanel.setBackground(Globals.COLOR_BG);
		
		btnSearch = new JButton("Search Trans");
		btnSearch.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (getSQLwhere()) { 
					if (strTabName.equals("")) strTabName= 
							"Trans " + Globals.TAB_Q + (++resultCount) + ": ";
					TransTable newPanel = new TransTable(Globals.MODE_TRANS, theViewerFrame, 
							strTabName, strQueryWhereSQL, strQuerySummary);
					theViewerFrame.addResultPanel(newPanel, newPanel.getTabName(), strQuerySummary);
				}
			}
		});
		
		JButton btnDefault = new JButton("Limit");
		btnDefault.setBackground(Globals.COLOR_BG);
		btnDefault.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updatePanelClear();
				updatePanelDefault();
			}
		});	
		
		JButton btnClear = new JButton("Clear");
		btnClear.setBackground(Globals.COLOR_BG);
		btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updatePanelClear();
			}
		});
		
			
		JButton btnExpand = new JButton("Expand All");
		btnExpand.setBackground(Globals.COLOR_BG);
		btnExpand.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for(int x=0; x<theSections.length; x++)
					theSections[x].expand();
			}
		});
		
		JButton btnCollapse = new JButton("Collapse All");
		btnCollapse.setBackground(Globals.COLOR_BG);
		btnCollapse.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for(int x=0; x<theSections.length; x++)
					theSections[x].collapse();
			}
		});
		
		JButton btnHelp = new JButton("Help");
        btnHelp.setBackground(Globals.COLOR_HELP);
        btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				ViewTextPane.displayHTML(theViewerFrame, "Trans Query Help", htmlFile);
			}
		});
     
		buttonPanel.add(btnSearch);
		buttonPanel.add(Box.createHorizontalStrut(10));
		buttonPanel.add(btnDefault);		    
		buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(btnClear);		    
		buttonPanel.add(Box.createHorizontalStrut(5));
		//buttonPanel.add(btnExpand);
		//buttonPanel.add(Box.createHorizontalStrut(5));
		//buttonPanel.add(btnCollapse);
		//buttonPanel.add(Box.createHorizontalStrut(10));
		buttonPanel.add(btnHelp);
		buttonPanel.setMaximumSize(buttonPanel.getPreferredSize());
	}
	
	/*****************************************************
	 * Create collapsiblePamels for query options
	 */
	private void createSections() {
		theSections = new CollapsiblePanel[SECTIONS.length];
		
		for(int x=0; x<theSections.length; x++) {
			theSections[x] = new CollapsiblePanel(SECTIONS[x], SECTIONS_DESC[x]);
			theSections[x].setAlignmentX(Component.LEFT_ALIGNMENT);
		}		
		theSections[0].add(createBasicPanel());
		theSections[1].add(createGeneralPanel());
		theSections[2].add(createVariantPanel());
		theSections[3].add(createLibraryPanel());
		theSections[4].add(createComplexQueryInstruct());
	}	

	/*****************************************
	 * Basic Search -- these searches are not combined with anything
	 */
	private JPanel createBasicPanel() {
		JPanel page = CreateJ.panelPage();
		JPanel row;		

		// main name
		row = CreateJ.panelLine();
		txtTransName = CreateJ.textField(20, enableListener, null);
		row = CreateJ.panelTextLine("Trans name", txtTransName, "(substring or exact list)");
		
		final JButton btnLoadNames = new JButton("Load file");
		btnLoadNames.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnLoadNames.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				FileMethods.readNamesFromFile(theViewerFrame, txtTransName);
			}
		});        		
		row.add(Box.createHorizontalStrut(5));
		row.add(btnLoadNames);	
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		// Ensembl	
		txtEnsembl = CreateJ.textField(20, enableListener, null);
		row = CreateJ.panelTextLine(Globals.TRANSIDEN, txtEnsembl, "(substring or exact list)");	
		
		final JButton btnLoadIDs = new JButton("Load file");
		btnLoadIDs.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnLoadIDs.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				FileMethods.readNamesFromFile(theViewerFrame, txtEnsembl);
			}
		});        		
		row.add(Box.createHorizontalStrut(5));
		row.add(btnLoadIDs);
		page.add(row);
		page.add(Box.createVerticalStrut(5));
			
		return page;
	}
	/*****************************************
	 * General
	 */
	private JPanel createGeneralPanel() {
		JPanel page = CreateJ.panelPage();
		JPanel row;		

		// Description	
		txtDescript = CreateJ.textField(20, enableListener, null);
		row = CreateJ.panelTextLine("Description", txtDescript, "(substring)");	
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		// Chromosome
		txtChr = CreateJ.textField(enableListener, null);
		String t = (chrRoot.equalsIgnoreCase("chr")) ? "Chr" : "Chr (" + chrRoot + ")";
		row = CreateJ.panelTextLine(t, txtChr, "(integer or letter, e.g. X, Y)");
		page.add(row);
		page.add(Box.createVerticalStrut(5));	
		
		txtOdRemark = CreateJ.textField(10, enableListener, null);
		row = CreateJ.panelTextLine("OD Remark", txtOdRemark, "(substring, see Help)");
		row.add(Box.createHorizontalStrut(10));
		String [] choice = { "Has odRmk ", "No odRmk ", "Any"};
		optHasOdRemark= new OptionList(choice, 2);	
		row.add(optHasOdRemark);
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		txtGtkRemark = CreateJ.textField(10, enableListener, null);
		row = CreateJ.panelTextLine("GTK Remark", txtGtkRemark, "(substring, see Help)");
		row.add(Box.createHorizontalStrut(10));
		String [] choice1 = { "Has gtfRmk", "No gtfRmk", "Any"};
		optHasGtkRemark= new OptionList(choice1, 2);	
		row.add(optHasGtkRemark);
		if (theMetaData.hasgtfRmk()) {
			page.add(row);
			page.add(Box.createVerticalStrut(5));
		}	
		row = CreateJ.panelLine();
		checkBestTrans = new JCheckBox("Trans with highest coverage for gene (Rank=1)");
		checkBestTrans.setBackground(Globals.COLOR_BG);
		checkBestTrans.setSelected(false);
		row.add(checkBestTrans);
		page.add(row);
		
		row = CreateJ.panelLine();
		checkTransAI = new JCheckBox("Trans with at least one library AI (P<" + Globals.AI_PVALUE + ")");
		checkTransAI.setBackground(Globals.COLOR_BG);
		checkTransAI.setSelected(false);
		row.add(checkTransAI);
		page.add(row);
		
		return page;
	}
	private JPanel createVariantPanel() {
		JPanel page = CreateJ.panelPage();
		JPanel row;
				
		// At least one SNP:  o With cov20 o don't care   o With AI  o don't care 
		row = CreateJ.panelTextLine("At least one SNP with", 50);
		
		checkSNPCov = new JCheckBox("Cov(>=" + Globals.MIN_READS + ")");
		checkSNPCov.setBackground(Globals.COLOR_BG);
		checkSNPCov.setSelected(false);
		row.add(checkSNPCov);
		row.add(Box.createHorizontalStrut(20));
		
		checkSNPAI = new JCheckBox("AI (P<" + Globals.AI_PVALUE + ")");
		checkSNPAI.setBackground(Globals.COLOR_BG);
		checkSNPAI.setSelected(false);
		row.add(checkSNPAI);
		
		page.add(row);
		page.add(Box.createVerticalStrut(5));
				
		// #SNPs >= [    ]  #Coding >= []   #Missense>= []   #Damaging>=[]
		row = CreateJ.panelTextLine("SNPs", 50);
		txtnSNP = CreateJ.textField(enableListener, validateIntListener);
		CreateJ.addPanelTextLine(row, 0, "#SNPs >=", txtnSNP);		
		
		txtnCoding= CreateJ.textField(enableListener, validateIntListener);
		txtnMissense = CreateJ.textField(enableListener, validateIntListener);
		txtnDamaging = CreateJ.textField(enableListener, validateIntListener);
		CreateJ.addPanelTextLine(row, 0, "#Coding >=", txtnCoding);	
		CreateJ.addPanelTextLine(row, 0, "#Missense >=", txtnMissense);	
		CreateJ.addPanelTextLine(row, 0, "#Damaging >=", txtnDamaging);
	
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		// o No Indel, etc
		row = CreateJ.panelTextLine("Indel", 50);
		String [] var = {"Has InDel", "No InDel", "Don't care"};
		optIndel= new OptionList(var, 2);	
		
		row.add(optIndel);
		page.add(row);
		
		return page;
	}

	/****************************************************************
	 * Library section
	 */
	private JPanel createLibraryPanel() {		
		JPanel page = CreateJ.panelPage();
		JPanel row;		
		int sp1=75;
		int sp2=0;
		
		boolean hasTotal = theMetaData.hasReadCnt();
		
		// Coverage: SNP [   ]  Total [     ]  
		txtSNPCovMin = CreateJ.textField(enableListener, validateIntListener);
		txtSNPCovMin.setText(Integer.toString(Globals.MIN_READS));
		txtSNPCovMax = CreateJ.textField(enableListener, validateIntListener);
		
		txtTotalCovMin = CreateJ.textField(enableListener, validateIntListener);
		txtTotalCovMax = CreateJ.textField(enableListener, validateIntListener);
	
		row = CreateJ.panelTextLine("Coverage", sp1,  "SNP >=", sp2,  txtSNPCovMin);
		CreateJ.addPanelTextLine(row, sp2, "and <=", txtSNPCovMax);
		row.add(Box.createHorizontalStrut(20));
		if (hasTotal) {
			CreateJ.addPanelTextLine(row, sp2, Globals.COUNT2 + " >=", txtTotalCovMin);
			CreateJ.addPanelTextLine(row, sp2, "and <=", txtTotalCovMax);
		}		
		row.add(Box.createHorizontalStrut(20));
		row.add(new JLabel("(integer)"));
		page.add(row);
		page.add(Box.createVerticalStrut(5));				
		
		// Score: SNP [   ]  Total [     ]  
		txtSNPScoreMin = CreateJ.textField(enableListener, validateRealListener);
		txtSNPScoreMax = CreateJ.textField(enableListener, validateRealListener);
		txtTotalScoreMin = CreateJ.textField(enableListener, validateRealListener);
		txtTotalScoreMax = CreateJ.textField(enableListener, validateRealListener);
	
		row = CreateJ.panelTextLine("Score", sp1,  "SNP <=", sp2, txtSNPScoreMin);
		CreateJ.addPanelTextLine(row, sp2, " or  >=", txtSNPScoreMax);
		row.add(Box.createHorizontalStrut(20));
		if (hasTotal) {
			CreateJ.addPanelTextLine(row, sp2, Globals.COUNT2 + " <=", txtTotalScoreMin);
			CreateJ.addPanelTextLine(row, sp2, " or  >=", txtTotalScoreMax);
		}
		row.add(Box.createHorizontalStrut(20));
		row.add(new JLabel("(fraction)"));
		page.add(row);
		page.add(Box.createVerticalStrut(10));
		
		// PV: SNP [   ]  Total [     ]  o <=  o >=
		txtSNPpval = CreateJ.textField(enableListener, validateRealListener);
		txtTotalpval =  CreateJ.textField(enableListener, validateRealListener);
		String [] REFALT = { "Ref>Alt", "Alt>Ref", "Any" };
		optPV= new OptionList(REFALT, 2);	
	
		row = CreateJ.panelTextLine("AI Pval", sp1, "SNP <  ", sp2,  txtSNPpval);
		
		if (hasTotal) {
			row.add(Box.createHorizontalStrut(16));
			row.add(new JLabel(Globals.COUNT2 + " <"));
			row.add(Box.createHorizontalStrut(5));
			row.add(txtTotalpval);
		}
		row.add(Box.createHorizontalStrut(10));
		row.add(optPV);	
		row.add(Box.createHorizontalStrut(20));
		row.add(new JLabel("(fraction)"));
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		// Libraries: o All o Any	SNP Filter: o yes o no
		row = CreateJ.panelTextLine("Libraries", 75);
		String [] ALL = { "And (All)", "Or (Any)" };
		optAllAny= new OptionList(ALL, 0);	
		row.add(optAllAny);
		row.add(Box.createHorizontalStrut(30));
		
		JLabel snp = new JLabel("SNP filter:");
		String [] YesNo = {"Yes", "No"};
		optSNPfilter = new OptionList(YesNo, 1);
		//row.add(snp);
		//row.add(Box.createHorizontalStrut(5));
		//row.add(optSNPfilter);				
				
		page.add(row);
		page.add(Box.createVerticalStrut(10));
		
		// Select Strain
		ieStrain = new IncludeExclude(strAbbr, Globals.condition1, "Select from");
		ieStrain.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		page.add(ieStrain);
		page.add(Box.createVerticalStrut(10));
		
		// Select Tissue
		ieTissue = new IncludeExclude(tisAbbr, Globals.condition2, "Select from");
		ieTissue.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		if (hasCond2) page.add(ieTissue);
		return page;
	}
	/************************************************
	 * Had a panel to build a query, but this is easier for the user and to implement
	 */
	private JPanel createComplexQueryInstruct() {
		JPanel page = CreateJ.panelPage();
		JPanel row = CreateJ.panelLine();
		
		row.add(new JLabel("1. Create two tables; 2. Go to >Results; "));
		page.add(row);
		row = CreateJ.panelLine();
		row.add(new JLabel("3. Select two tables; 4. Use union, intersection, or diff;"));
		page.add(row);
		
		return page;
	}

	/***********************************************************
	 * XXX SQL queries
	 */
	private boolean getSQLwhere() {
		strQueryWhereSQL = strQuerySummary = strTabName = "";
		getSQLBasic();
		if (!strQueryWhereSQL.equals("")) return true;
		
		int w = getCheckSections();
		if (w == -1) return false;
		getSQLGeneral();
		getSQLvariant();
		
		if (w==1) {
			syncLibsSave.clear();
			syncAbbrSave.clear();
			getSQLLibrary();	
		}
		if (w==1 && optSNPfilter.getSelectedOption()==0) { // executes the current query, and return list of transids
			getSQLfilterSNPs();
			if (strQueryWhereSQL.equals("")) return false;
		}
		if (strQuerySummary.equals("")) strQuerySummary = "Show all ";
	
		return true;
	}	
	/*****************
	 * XXX test input from user
	 */
	private int getCheckSections() {
		if(txtChr.getText().trim().length() > 0) {
			String chr = getSQLChr();
			if (chr.equals("")) return -1;
		}	
		int state=0, which=0;	
		// Library
		state=0;
		String [] strainInc = ieStrain.includeList();
		String [] tissueInc = ieTissue.includeList();	
		if (strainInc!=null && strainInc.length > 0) state++;
		if (hasCond2 && tissueInc!=null && tissueInc.length > 0) state++;
		if (state==0) return 0;
		
		if (hasCond2 && state==1) {
			JOptionPane.showMessageDialog(theViewerFrame, 
					"Please enter both Strain and Tissue",
					"Library", JOptionPane.PLAIN_MESSAGE);
			return -1;
		}
		else if (state==2 || (!hasCond2 && state==1)) {
			if (txtSNPCovMin.getText().trim().length()>0) { which=1; }
			else if (txtSNPScoreMin.getText().trim().length()>0) {which=1; }
			else if (txtTotalCovMin.getText().trim().length()>0) {which=1; }
			else if (txtTotalScoreMin.getText().trim().length()>0) {which=1; }
			else if (txtSNPCovMax.getText().trim().length()>0) {which=1;}
			else if (txtSNPScoreMax.getText().trim().length()>0) {which=1;}
			else if (txtTotalCovMax.getText().trim().length()>0) {which=1;}
			else if (txtTotalScoreMax.getText().trim().length()>0) {which=1;}
			else if (txtTotalpval.getText().trim().length()>0) {which=1;}
			else if (txtSNPpval.getText().trim().length()>0) {which=1; }
			else {
				JOptionPane.showMessageDialog(theViewerFrame, 
						"Please enter at least one entry of Coverage, Score, PV",
						"Library", JOptionPane.PLAIN_MESSAGE);
				return -1;
			}
		}
		if (optSNPfilter.getSelectedOption()==0) {
			if (txtSNPpval.getText().trim().length()>0 && txtTotalScoreMin.getText().trim().length()==0) {
				JOptionPane.showMessageDialog(theViewerFrame, 
						"This option only works with the score, not the Pvalue",
						"Library", JOptionPane.PLAIN_MESSAGE);
				return -1;
			}
		}
		return which;
	}
	private void getCombine() {
		if (strQueryWhereSQL.length()>0) {
			 strQueryWhereSQL += " AND ";
			 strQuerySummary += ", ";
		}
	}
	private void getCombineOr() {
		if (strQueryWhereSQL.length()>0) {
			 strQueryWhereSQL += " OR ";
			 strQuerySummary += ", ";
		}
	}
	/********************************************
	 *  Basic query
	 */	
	private void getSQLBasic() {
		if(txtTransName.getText().trim().length() > 0) {
			getCombine();
			strQuerySummary = "Trans Name = " + txtTransName.getText().trim();
			strTabName = txtTransName.getText().trim() + ": ";
			strQueryWhereSQL = getSQLList(txtTransName, "transName");
		}
		else if(txtEnsembl.getText().trim().length() > 0) {
			getCombine();
			strQuerySummary = Globals.TRANSIDEN + " = " + txtEnsembl.getText().trim();
			strTabName = txtEnsembl.getText().trim() + ": ";
			strQueryWhereSQL = getSQLList(txtEnsembl, "transIden");
		}
	}
	private void getSQLGeneral() {
		if(txtDescript.getText().trim().length() > 0) {
			getCombine();
			String d = txtDescript.getText().trim();
			strQuerySummary += "Description  = " + d;				
			strTabName = "Trans descrip:";
			strQueryWhereSQL += getSQLList(txtDescript, "descript");
		}
		if(txtChr.getText().trim().length() > 0) {
			getCombine();
			strQuerySummary += "Chr = " + txtChr.getText().trim();	
			strQueryWhereSQL += getSQLChr();
		}	
		
		if(txtOdRemark.getText().trim().length() > 0) {
			getCombine();
			String text = txtOdRemark.getText().trim();
			strQuerySummary += "odRmk = " + text;	
			strQueryWhereSQL += "odRmk like '%" + text + "%'";
		}	
		else if (optHasOdRemark.getSelectedOption()==0) {
			getCombine();
			strQuerySummary += " Has odRmk ";
			strQueryWhereSQL += "(trans.odRmk != '' && trans.odRmk is not null)";
		}
		else if (optHasOdRemark.getSelectedOption()==1) {
			getCombine();
			strQuerySummary += " No odRmk ";
			strQueryWhereSQL += "(trans.odRmk = '' || trans.odRmk is null)";
		}
		
		if(txtGtkRemark.getText().trim().length() > 0) {
			getCombine();
			strQuerySummary += "GTK Remark = " + txtGtkRemark.getText().trim();	
			strQueryWhereSQL += "trans.gtfRmk like '%" + txtGtkRemark.getText().trim() + "%'";
		}	
		else if (optHasGtkRemark.getSelectedOption()==0) {
			getCombine();
			strQuerySummary += " Has gtfRmk ";
			strQueryWhereSQL += "trans.gtfRmk != ''";
		}
		else if (optHasGtkRemark.getSelectedOption()==1) {
			getCombine();
			strQuerySummary += " No gtfRmk ";
			strQueryWhereSQL += "trans.gtfRmk = ''";
		}
		
		if (checkBestTrans.isSelected()) {
			getCombine();
			strQuerySummary += " Rank=1 ";
			strQueryWhereSQL += "trans.rank=1";
		}
		if (checkTransAI.isSelected()) {
			getCombine();
			strQuerySummary += " Library AI ";
			strQueryWhereSQL += "trans.cntLibAI>0";
		}
	}
	private String getSQLList(JTextField txt, String column) {
		String [] nameList = Static.str2arr(txt.getText().trim());
		String field = "trans." + column;
		String subQuery;
		if (nameList.length==1)
			subQuery = field + " LIKE '" + Static.getSubStr(nameList[0], "trans", column) + "'";
		else {
			subQuery = field + " IN ('" + nameList[0] + "'";
			for(int x=1; x<nameList.length; x++) {
				subQuery += ", '" + nameList[x] + "'";	
			}
			subQuery += ")";
		}
		return subQuery;
	}	
	private String getSQLChr() {
		txtChr.setForeground(Color.BLACK);
		String val = txtChr.getText().trim();
		if (chrStr.contains(val)) return "chr='" + val + "'";
		
		txtChr.setForeground(Color.RED);
		LogTime.infoBox(chrRoot + " " + val + " not in database");
		return "";
	}
	/******************************************************
	 * get SQL SNP
	 */
	private void getSQLvariant() {
		if (checkSNPCov.isSelected()) {
			getCombine();
			strQuerySummary += "SNP Cov>=" + Globals.MIN_READS;
			strQueryWhereSQL += "trans.cntSNPCov>0 ";
		}
		if (checkSNPAI.isSelected()) {
			getCombine();
			strQuerySummary += "Has AI SNP";
			strQueryWhereSQL += "trans.cntSNPAI>0 ";
		}
		if (optIndel.getSelectedOption()!=2) {
			getCombine();	
			if (optIndel.getSelectedOption()==1) {
				strQuerySummary += "No InDel ";
				strQueryWhereSQL += "trans.cntIndel=0 ";
			}
			else if (optIndel.getSelectedOption()==0) {
				strQuerySummary += "Has Indel ";
				strQueryWhereSQL += "trans.cntIndel>0";
			}
		}
		String nSNP = txtnSNP.getText().trim();
		if (!nSNP.equals("") && !nSNP.equals("0")) {
			getCombine();	
			strQuerySummary += "#SNP >= " + nSNP + " ";
			strQueryWhereSQL += "trans.cntSNP>=" + nSNP + " "; 
		}
		String nCode = txtnCoding.getText().trim();
		if (!nCode.equals("") && !nCode.equals("0")) {
			getCombine();	
			strQuerySummary += "#Coding >= " + nCode + " ";
			strQueryWhereSQL += "trans.cntCoding>=" + nCode + " "; 
		}
		String nMiss = txtnMissense.getText().trim();
		if (!nMiss.equals("") && !nMiss.equals("0")) {
			getCombine();	
			strQuerySummary += "#Mis >= " + nMiss + " ";
			strQueryWhereSQL += "trans.cntMissense>=" + nMiss + " "; 
		}
		String nDam = txtnDamaging.getText().trim();
		if (!nDam.equals("") && !nDam.equals("0")) {
			getCombine();	
			strQuerySummary += "#Dam >= " + nDam + " ";
			strQueryWhereSQL += "trans.cntDamage>=" + nDam + " "; 
		}
	}
	
	/*********************************************************
	 * XXX Select score, strain and tissue
	 */
	private void getSQLLibrary() {	
	// get all text input
		String [] strainInc = ieStrain.includeList();
		String [] tissueInc = ieTissue.includeList();
		if (tissueInc==null || tissueInc.length==0) {
			tissueInc = new String [1];
			tissueInc[0] = "";
		}
	
		String snpCovMin= txtSNPCovMin.getText().trim();	
		String snpCovMax= txtSNPCovMax.getText().trim();	
		String snpScoreMin = txtSNPScoreMin.getText().trim();
		String snpScoreMax = txtSNPScoreMax.getText().trim();
		String totCovMin= txtTotalCovMin.getText().trim();	
		String totScoreMin = txtTotalScoreMin.getText().trim();
		String totCovMax= txtTotalCovMax.getText().trim();	
		String totScoreMax = txtTotalScoreMax.getText().trim();
		
		String pvTotal= txtTotalpval.getText().trim();	
		String pvSNP = txtSNPpval.getText().trim();
		
		getCombine();
		
	// Set Counts scores fields for Tissue/Strain loop -- must be at least one entered
		boolean hasSNPCovMin = (snpCovMin.length()==0) ? false : true;
		boolean hasSNPCovMax = (snpCovMax.length()==0) ? false : true;
		boolean hasSNPScoreMin = (snpScoreMin.length()==0) ? false : true;
		boolean hasSNPScoreMax = (snpScoreMax.length()==0) ? false : true;
		boolean hasTotalCovMin = (totCovMin.length()==0) ? false : true;
		boolean hasTotalCovMax = (totCovMax.length()==0) ? false : true;
		boolean hasTotalScoreMin = (totScoreMin.length()==0) ? false : true;
		boolean hasTotalScoreMax = (totScoreMax.length()==0) ? false : true;
		
		boolean hasSNPPV = (pvSNP.length()==0) ? false : true;
		boolean hasTotalPV = (pvTotal.length()==0) ? false : true;
		
		String r = Globals.PRE_REFCNT;
		String c = Globals.PRE_ALTCNT;
		String x = Globals.SUF_TOTCNT;
					
		if (hasSNPCovMin && hasSNPCovMax) strQuerySummary += "SNP Cov >=" + snpCovMin + " and <= " + snpCovMax+ "; ";
		else if (hasSNPCovMin) strQuerySummary += "SNP Cov >=" + snpCovMin + "; ";
		else if (hasSNPCovMax) strQuerySummary += "SNP Cov <=" + snpCovMax + "; ";
		
		if (hasTotalCovMin && hasTotalCovMax) strQuerySummary += Globals.COUNT2 + " Cnt >=" + totCovMin + " and <= " + totCovMax+ "; ";
		else if (hasTotalCovMin) strQuerySummary += Globals.COUNT2 + " Cnt >=" + totCovMin + "; ";
		else if (hasTotalCovMax) strQuerySummary += Globals.COUNT2 + " Cnt <=" + totCovMax + "; ";
		
		if (hasSNPScoreMin && hasSNPScoreMax) strQuerySummary += "SNP Score <=" + snpScoreMin + " or >=" + snpScoreMax+ "; ";
		else if (hasSNPScoreMin) strQuerySummary += "SNP Score <=" + snpScoreMin + "; ";
		else if (hasSNPScoreMax) strQuerySummary += "SNP Score >=" + snpScoreMax + "; ";
		
		if (hasTotalScoreMin && hasTotalScoreMax) strQuerySummary += " Score <=" + totScoreMin + " or >=" + totScoreMax+ "; ";
		else if (hasTotalScoreMin) strQuerySummary += Globals.COUNT2 +" Score <=" + totScoreMin + "; ";
		else if (hasTotalScoreMax) strQuerySummary += Globals.COUNT2 +" Score >=" + totScoreMax + "; ";
		
		String totOp="";
		int optPVdirection = optPV.getSelectedOption();
		if (optPVdirection==0)  totOp=" (Ref>Alt)";
		else if (optPVdirection==1)  totOp=" (Alt>Ref)";
		if (hasSNPPV) 
			if (totOp.equals("")) strQuerySummary 	+=  " SNP Pval< "  + pvSNP + "; ";
			else strQuerySummary 	+= " SNP Pval<"  + pvSNP +  totOp + "; ";
		if (hasTotalPV) 
			if (totOp.equals("")) strQuerySummary 	+=  Globals.COUNT2 +" Pval<"  + pvTotal + "; ";
			else strQuerySummary 	+=  Globals.COUNT2 +" Pval<"  + pvTotal +  totOp + "; ";
		
	// Tissues and Strains
		syncLibs = new Vector <String> ();  // global for SNP query
		
		String libOp = " AND ", op="&", libs="";
		if (optAllAny.getSelectedOption()==1) {libOp =" OR "; op="|";}
		if (strainInc.length>1 || tissueInc.length>1) libs += op;
		
		strQueryWhereSQL += "(";
		for (int s=0; s<strainInc.length; s++) {
			String strain = strainInc[s];	
			for (int t=0; t<tissueInc.length; t++) {
				String lib = strain + tissueInc[t];
				syncLibs.add(lib);
				if (s==0&&t==0) libs += lib;
				else libs += ","+lib;
				
				String clause="(";
				boolean first=true;
				String Sref = "trans." + r + lib; 	// trans.R__NyfBr Ref SNP
				String Salt = "trans." + c + lib; 	// trans.A__NyfBr ALT SNP
				String Rref = "trans." + r + lib+x;	// trans.R__NyfBr2 Ref Total
				String Ralt = "trans." + c + lib+x;	// trans.A__NyfBr2 Alt Total
				
				String DsnpA = "trans." + lib;		// trans.NyfBr SNP pval
				String DtotalA = "trans." + lib+x;		// trans.NyfBr2 total pval
				
				if (hasSNPCovMin) {
					first=false; 		
					clause += " (" + Sref + "+" +  Salt + ")" + " >= " + snpCovMin;
				}	
				if (hasSNPCovMax) {
					if (first) first=false; else clause += " AND "; 
					clause += " (" + Sref + "+" + Salt + ")" + " <= " + snpCovMax;
				}	
				if (hasTotalCovMin) {
					if (first) first=false; else clause += " AND "; 
					clause += " (" + Rref + "+" + Ralt + ") >= " + totCovMin;
				}	
				if (hasTotalCovMax) {
					if (first) first=false; else clause += " AND "; 
					clause += " (" + Rref + "+" + Ralt + ") <= " + totCovMax;
				}
	
				if (hasSNPScoreMin || hasSNPScoreMax) {
					if (first) first=false; else clause += " AND "; 
					String xx = Globals.scoreStr(Sref, Salt);
					if (hasSNPScoreMin && hasSNPScoreMax) 
						clause += "(" +  xx  + " <= " + snpScoreMin + " or " +  xx  + " >= " + snpScoreMax + ")" ;
					else if (hasSNPScoreMin) clause +=  xx + " <= " + snpScoreMin ;
					else if (hasSNPScoreMax) clause +=  xx + " >= " + snpScoreMax ;
				}
				if (hasTotalScoreMin || hasTotalScoreMax) {
					if (first) first=false; else clause += " AND "; 
					String xx = Globals.scoreStr(Sref, Salt);
					if (hasTotalScoreMin && hasTotalScoreMax) 
						clause += "(" +  xx  + " <= " + totScoreMin + " or " +  xx  + " >= " + totScoreMax + ")" ;
					else if (hasTotalScoreMin) clause +=  xx + " <= " + totScoreMin ;
					else if (hasTotalScoreMax) clause +=  xx + " >= " + totScoreMax ;
				}
				
				if (hasSNPPV) {
					if (first) first=false; else clause += " AND "; 
					if (optPVdirection==0) 		 clause += Sref + " > " + Salt + " AND ";
					else if (optPVdirection==1)  clause +=  Sref + " < " + Salt + " AND ";
					clause += DsnpA + " < " + pvSNP ;
				}
				if (hasTotalPV) {
					if (first) first=false; else clause += " AND "; 
					if (optPVdirection==0)        clause += Rref + " > " + Ralt + " AND ";
					else if (optPVdirection==1)   clause +=  Rref + " < " + Ralt + " AND ";
					clause +=  DtotalA + " < "  + pvTotal ;
				}
				clause += ")";
				
				if (s==0 && t==0) strQueryWhereSQL += clause; 
				else strQueryWhereSQL += libOp + clause;
			}			
		}
		strQuerySummary = libs + "; " + strQuerySummary;
		strQueryWhereSQL += ")";
		theViewerFrame.getTransColumnSync().setLibs(syncLibs);
		
		syncAbbr = new Vector <String> ();
		for (int s=0; s<strainInc.length; s++) syncAbbr.add(strainInc[s]);
		if (hasCond2)
			for (int t=0; t<tissueInc.length; t++) syncAbbr.add(tissueInc[t]);
		theViewerFrame.getTransColumnSync().setAbbr(syncAbbr);
	}
	
	/****************************************************
	 * At least one SNP must have the specified coverage, 
	 * and general requirements, e.g. missense, indel, damaged
	 */
	private void getSQLfilterSNPs() {
		strQuerySummary =  "SNP filter; " + strQuerySummary;
		try {			
		/* get Trans that passed Trans Query */
			String sql = "SELECT TRANSid, transName FROM trans " +
						" WHERE " + strQueryWhereSQL;
			
			DBConn mDB = ViewerFrame.getDBConnection();
			ResultSet rs = mDB.executeQuery(sql);
			TreeMap <String, Integer> transMap = new TreeMap <String, Integer> ();
			while(rs.next()) {
				transMap.put(rs.getString(2), rs.getInt(1));
			}
			System.out.println("Passed first filter: " + transMap.size() + " trans");
		// build snp columns with only specific libraries, e.g. SNP.R__OfKid
			int nLibs = syncLibs.size(), n=0;
			String [] refCol = new String [nLibs];
			String [] altCol = new String [nLibs];
			
			boolean isMiss = (txtnMissense.getText().trim().length()>0) ? true : false;
			boolean isDam = (txtnDamaging.getText().trim().length()>0) ? true : false;
			boolean isCode = (txtnCoding.getText().trim().length()>0) ? true : false;
			
			String colSNP="SNP.isMissense, SNP.isDamaging, SNP.isCoding ";
			for (String lib : syncLibs) {
				colSNP += ",SNP." + Globals.PRE_REFCNT + lib + ",SNP." + Globals.PRE_ALTCNT + lib;	
				refCol[n] = Globals.PRE_REFCNT + lib;
				altCol[n] = Globals.PRE_ALTCNT + lib;
				n++;
			}
			
		// get parameters - one SNP needs to succeed	
			int snpCovMin=0, snpCovMax=0;
			double snpScoreMin=0.0, snpScoreMax=0.0, snpPval=0.0;
			if (txtSNPCovMin.getText().trim().length()>0)
				snpCovMin= Integer.parseInt(txtSNPCovMin.getText().trim());	
			if (txtSNPCovMax.getText().trim().length()>0)
				snpCovMax= Integer.parseInt(txtSNPCovMax.getText().trim());	
			
			if (txtSNPScoreMin.getText().trim().length()>0)
				snpScoreMin= Float.parseFloat(txtSNPScoreMin.getText().trim());	
			if (txtSNPScoreMax.getText().trim().length()>0)
				snpScoreMax= Float.parseFloat(txtSNPScoreMax.getText().trim());	
			if (txtSNPpval.getText().trim().length()>0)
				snpPval= Float.parseFloat(txtSNPpval.getText().trim());	
		
			String libOp = "AND";
			if (optAllAny.getSelectedOption()==1) libOp ="OR";
			
		// Trans loop
			strQueryWhereSQL = "trans.TRANSid IN (";
			int cntTrans=0;
		
			// Loop through Trans
			for (String transName : transMap.keySet()) 
			{
				int TRANSid = transMap.get(transName);
				rs = mDB.executeQuery("SELECT " + colSNP + 
					" FROM SNPtrans " +
					" JOIN SNP on SNP.SNPid=SNPtrans.SNPid " +
					" WHERE SNPtrans.TRANSid=" + TRANSid);  
				
				// Loop through SNPs for the trans
				while (rs.next()) 
				{
					int misVal = rs.getInt(1);
					int damVal = rs.getInt(2);
					int codeVal = rs.getInt(3);
					if (isMiss && misVal==0) continue;
					if (isDam && damVal==0) continue;
					if (isCode && codeVal==0) continue;
					
					System.out.println("Check libs");
					// Loop through librarys if the SNPs
					int cntLib=0;
					for (int i=0; i<nLibs; i++) {	
						int ref = rs.getInt(refCol[i]);
						int alt = rs.getInt(altCol[i]);
						int cov = ref+alt;
						double score = Globals.score(ref, alt);
						boolean fail=false;
						if (snpCovMin!=0 && cov < snpCovMin) fail=true;
						if (snpCovMax!=0 && cov > snpCovMax) fail=true;
						if (snpScoreMin != 0.0 || snpScoreMax != 0.0) {
							// valid score <= snpScoreMin || score >= snpScoreMax, i.e. either extreme
							if (snpScoreMin != 0.0 && snpScoreMax != 0.0) {
								if (score > snpScoreMin && score < snpScoreMax) fail=true;
							}
							else if (snpScoreMax != 0.0 && score < snpScoreMax) fail=true;
							else if (snpScoreMin != 0.0 && score > snpScoreMin) fail=true;
						}
						// TODO add p-value, need join on SNPpval
						if (!fail) cntLib++;
					}// end SNP loop
					
					// if OR, the GOOD snp may not be the GOOD overall lib.
					if ((cntLib>0 && libOp.equals("OR")) || (cntLib==nLibs)) {
						if (cntTrans>0) strQueryWhereSQL += ",";
						strQueryWhereSQL += TRANSid;
						cntTrans++;
					}
				}
			} // end Trans Loop
			if (cntTrans==0) {
				strQueryWhereSQL="";
				JOptionPane.showMessageDialog(theViewerFrame, 
						"No transcripts passed the SNP filter test",
						"Library", JOptionPane.PLAIN_MESSAGE);
			}
			else strQueryWhereSQL+=")";
		}
		catch (Exception e) {
			ErrorReport.die(e, "SQL Filter SNPs");
		}
	}
	
	/***************************************
	 * panel functions
	 */

	private void updatePanelDefault() {
		checkBestTrans.setSelected(true);
		checkSNPCov.setSelected(true);
	}
	private void updatePanelClear() {
		txtTransName.setText("");
		txtEnsembl.setText("");
		txtDescript.setText("");
		txtChr.setText("");
		txtOdRemark.setText("");
		optHasOdRemark.setSelectedOption(2);
		txtGtkRemark.setText("");
		optHasGtkRemark.setSelectedOption(2);
		
		checkTransAI.setSelected(false);
		checkBestTrans.setSelected(false);
		checkSNPCov.setSelected(false);
		checkSNPAI.setSelected(false);
		
		optIndel.setSelectedOption(2);
		txtnSNP.setText("");
		txtnCoding.setText("");
		txtnMissense.setText("");
		txtnDamaging.setText("");
		optSNPfilter.setSelectedOption(1);
		
		txtSNPCovMin.setText("");
		txtSNPScoreMin.setText("");
		txtSNPCovMax.setText("");
		txtSNPScoreMax.setText("");
		txtSNPpval.setText("");
		txtTotalCovMin.setText("");
		txtTotalScoreMin.setText("");
		txtTotalCovMax.setText("");
		txtTotalScoreMax.setText("");
		txtTotalpval.setText("");
		ieStrain.selectAll(false);
		ieTissue.selectAll(false);
		optPV.setSelectedOption(2);
		optAllAny.setSelectedOption(0);
		
		syncLibsSave.clear();
		syncAbbrSave.clear();
	}
		
	private void setListeners() {
		enableListener = new CaretListener() {
			public void caretUpdate(CaretEvent arg0) {
				JTextField temp = (JTextField)arg0.getSource();
				temp.setForeground(Color.BLACK);
			}
		};
		validateIntListener = new CaretListener() {
			public void caretUpdate(CaretEvent arg0) {
				boolean valid = true;
				if(arg0.getSource() instanceof JTextField) {
					JTextField temp = (JTextField)arg0.getSource();
					String val = temp.getText().trim();
					if (val.length() > 0) {
						try {
							Integer.parseInt(val);
						} catch(Exception e) {
							valid = false;
						}
					}
					btnSearch.setEnabled(valid);
					if(!valid) {
						temp.setBackground(Color.RED);
						LogTime.infoBox("Invalid Integer");
					}
					else temp.setBackground(Color.WHITE);
				}
			}
		};
		validateRealListener = new CaretListener() {
			public void caretUpdate(CaretEvent arg0) {
				boolean valid = true;
				if(arg0.getSource() instanceof JTextField) {
					JTextField temp = (JTextField)arg0.getSource();
					String val = temp.getText().trim();
					if (val.length()!=0) {
						try {
							Double d=10.0;
							d= Double.parseDouble(val);
							if (d > 1 || d < 0) valid = false; // 
						} catch(Exception e) {
							valid = false;
						}
					}
					btnSearch.setEnabled(valid);
					if(!valid) {
						temp.setBackground(Color.RED);
						LogTime.infoBox("Invalid fraction");
					}
					else temp.setBackground(Color.WHITE);
				}
			}
		};
	}
	
	private String strTabName="";
	private String strQuerySummary="";
	private String strQueryWhereSQL="";
	
	private ViewerFrame theViewerFrame = null;
	private MetaData theMetaData = null;
	private String chrRoot;
	private Vector <String> chrStr;
	private String [] strAbbr = null;
	private String [] tisAbbr = null;
	private boolean hasCond2;
	private Vector <String> syncLibs = null;
	private Vector <String> syncAbbr = null;
	private Vector <String> syncLibsSave = new Vector <String> ();
	private Vector <String> syncAbbrSave = new Vector <String> ();

	private CaretListener enableListener = null;
	private CaretListener validateIntListener = null;
	private CaretListener validateRealListener = null;

	//Top button panel
	private JPanel buttonPanel = null;
	private JButton btnSearch = null;
	
	//Main panel
	private JPanel mainPanel = null;
	private CollapsiblePanel [] theSections = null;
		
	// basic
	private JTextField txtTransName = null;
	private JTextField txtEnsembl = null;
	private JTextField txtDescript = null;
	private JTextField txtChr = null;
	private JTextField txtOdRemark = null;
	private OptionList optHasOdRemark = null;
	private JTextField txtGtkRemark = null;
	private OptionList optHasGtkRemark = null;
	private JCheckBox checkBestTrans = null;
	private JCheckBox checkTransAI = null;
	// SNP
	private JCheckBox checkSNPCov = null;
	private JCheckBox checkSNPAI = null;
	private JTextField txtnSNP = null;
	private JTextField txtnCoding = null;
	private JTextField txtnMissense = null;
	private JTextField txtnDamaging = null;
	private OptionList optIndel = null;
		
	// Score and coverage for selected Strain and Tissue
	private JTextField txtSNPCovMin = null;
	private JTextField txtSNPCovMax = null;
	private JTextField txtTotalCovMin = null;
	private JTextField txtTotalCovMax = null;
	private JTextField txtSNPScoreMin = null;
	private JTextField txtSNPScoreMax = null;
	private JTextField txtTotalScoreMin = null;
	private JTextField txtTotalScoreMax = null;
	private JTextField txtSNPpval = null;
	private JTextField txtTotalpval = null;
	
	private OptionList optPV = null;
	private OptionList optAllAny = null;
	private IncludeExclude ieStrain = null;
	private IncludeExclude ieTissue = null;
	private OptionList optSNPfilter=null;
}
