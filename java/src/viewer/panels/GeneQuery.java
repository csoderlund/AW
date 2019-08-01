package viewer.panels;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

import database.MetaData;
import util.ErrorReport;
import util.Globals;
import util.LogTime;
import viewer.ViewerFrame;
import viewer.controls.*;

public class GeneQuery extends JPanel {
	private static final long serialVersionUID = 1672776836742705318L;
	boolean prtSQL=false;
	//Labels for the collapsible panels
	private static final String [] SECTIONS = { "Basic", "General"};
	private static final String [] SECTIONS_DESC = { "", ""};
	
	private static final String htmlFile = "/html/GeneQuery.html";
	
	//Counter for display and to keep name of results unique
	private static int resultCount = 0;
		
	public GeneQuery(ViewerFrame vFrame) {
		theViewerFrame = vFrame;
		MetaData meta = vFrame.getMetaData();
		chrStr = meta.getChr();
		chrRoot = meta.getChrRoot();
		setListeners();
				
		createButtonPanel();
		createSections();
			
		removeAll();
		setBackground(Globals.COLOR_BG);
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		mainPanel = CreateJ.panelPage();
		mainPanel.add(buttonPanel);
		mainPanel.add(Box.createVerticalStrut(15));
		
		Dimension d = mainPanel.getMaximumSize();
		d.height = mainPanel.getPreferredSize().height;
		mainPanel.setMaximumSize(d);
	
		add(mainPanel);

		for(int x=0; x<theSections.length; x++) {
			theSections[x].expand();
			//else theSections[x].collapse();
			add(theSections[x]);
		}
	}
	
	/******************************************
	 * Create controls for top of panel
	 */
	private void createButtonPanel() {
		buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
		buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttonPanel.setBackground(Globals.COLOR_BG);
		
		btnSearch = new JButton("Search Genes");
		btnSearch.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				theColumnPanel = new GeneColumn(theViewerFrame);
				if (getSQLwhere() != -1) { // sets tblQuery and tblSummary
					if (strTabName.equals("")) strTabName= "Gene " + Globals.TAB_Q + (++resultCount) + ": ";
					GeneTable newPanel = new GeneTable(theColumnPanel, theViewerFrame, 
							strTabName, strQueryWhereSQL, strQuerySummary, "");
					theViewerFrame.addResultPanel(newPanel, newPanel.getTabName(), strQuerySummary);
				}
			}
		});	
		buttonPanel.add(btnSearch);
		buttonPanel.add(Box.createHorizontalStrut(10));
		
		JButton btnDefault = new JButton("Limit");
		btnDefault.setBackground(Globals.COLOR_BG);
		btnDefault.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updatePanelClear();
				updatePanelDefault();
			}
		});	
		buttonPanel.add(btnDefault);		    
		buttonPanel.add(Box.createHorizontalStrut(5));
		
		JButton btnClear = new JButton("Clear");
		btnClear.setBackground(Globals.COLOR_BG);
		btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updatePanelClear();
			}
		});	
		buttonPanel.add(btnClear);		    
		buttonPanel.add(Box.createHorizontalStrut(5));
		
		JButton btnHelp = new JButton("Help");
        btnHelp.setBackground(Globals.COLOR_HELP);
        btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				ViewTextPane.displayHTML(theViewerFrame, "Gene Query Help", htmlFile);
			}
		});  	
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
	}	

	/*****************************************
	 * XXX Basic Search
	 */
	private JPanel createBasicPanel() {
		JPanel page = CreateJ.panelPage();
		JPanel row = CreateJ.panelLine();		
		page.add(row);
		page.add(Box.createVerticalStrut(10));

		// main name
		row = CreateJ.panelLine();
		txtGeneName = new JTextField(20);
		txtGeneName.addCaretListener(enableListener);
		txtGeneName.setMaximumSize(txtGeneName.getPreferredSize());
		row = CreateJ.panelTextLine("Gene name", txtGeneName, "(substring or exact list)");
		
		final JButton btnLoadNames = new JButton("Load file");
		btnLoadNames.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnLoadNames.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				FileMethods.readNamesFromFile(theViewerFrame,txtGeneName);
			}
		});        		
		row.add(Box.createHorizontalStrut(5));
		row.add(btnLoadNames);	
		page.add(row);
		page.add(Box.createVerticalStrut(10));
		
		// Synonym	
		txtSynonym = new JTextField(20);
		txtSynonym.addCaretListener(enableListener);
		txtSynonym.setMaximumSize(txtSynonym.getPreferredSize());
		row = CreateJ.panelTextLine("Synonym", txtSynonym, "(substring)");	
		page.add(row);
		page.add(Box.createVerticalStrut(15));
		return page;
	}
	/*****************************************
	 * XXX General Search
	 */
	private JPanel createGeneralPanel() {
		JPanel page = CreateJ.panelPage();
		JPanel row = CreateJ.panelLine();		
		page.add(row);
		page.add(Box.createVerticalStrut(10));

		// Description	
		txtDescript = new JTextField(20);
		txtDescript.addCaretListener(enableListener);
		txtDescript.setMaximumSize(txtDescript.getPreferredSize());
		row = CreateJ.panelTextLine("Description", txtDescript, "(substring)");	
		page.add(row);
		page.add(Box.createVerticalStrut(10));
		
		// Chromosome
		txtChr = CreateJ.textField(enableListener, null);
		String t = (chrRoot.equalsIgnoreCase("chr")) ? "Chr" : "Chr (" + chrRoot + ")";
		row = CreateJ.panelTextLine(t, txtChr, "(integer or letter, e.g. X, Y)");
		page.add(row);
		page.add(Box.createVerticalStrut(10));
		
		// Variants
		row = CreateJ.panelTextLine("Variant", 50);
		String [] var = {"SNPs", "Missense", "Indel",  "Any"};
		optVariant= new OptionList(var, 3);	
		row.add(optVariant);
		page.add(row);
		page.add(Box.createVerticalStrut(20));
		return page;
	}
	
	/***********************************************************
	 * XXX SQL queries
	 */	
	/*****************
	 * XXX return which section is checked
	 * if multiple, put up message and return -1
	 */
	private int getSQLwhere() {
		strQuerySummary = strQueryWhereSQL = strTabName = "";
		int which = whichSection();
		if (which == -1) return -1;
		
		if (which==1) getSQLBasic();
		else getSQLGeneral();
		
		if (strQuerySummary.equals("")) {
			strQuerySummary = "Show all ";
			strTabName = "Gene: ";
		}
		return which;
	}
	private int whichSection() {
		int state=0;
		strQueryWhereSQL="";
		strQuerySummary=""; 
		
		// Basic
		if(txtGeneName.isEnabled() && txtGeneName.getText().trim().length() > 0) state++;
		if(txtSynonym.isEnabled() && txtSynonym.getText().trim().length() > 0) state++;
		if (state==2) {
			JOptionPane.showMessageDialog(theViewerFrame, 
					"both Gene or Synonym are entered, only enter one ",
					"Basic", JOptionPane.PLAIN_MESSAGE);
			return -1;
		}
		else if (state==1) return 1;
		
		if(txtChr.getText().trim().length() > 0) {
			String chr = getSQLChr();
			if (chr.equals("")) return -1;
		}	
		return 2;
	}

	/********************************************
	 *  Basic query
	 */

	private void getSQLBasic() {
		if(txtGeneName.getText().trim().length() > 0) {
			strQuerySummary = "Gene Name = " + txtGeneName.getText().trim() + " ";
			strTabName = txtGeneName.getText().trim() + ": ";
			strQueryWhereSQL= getSQLList(txtGeneName, "geneName");
		}		
		else if(txtSynonym.getText().trim().length() > 0) {
			getCombine();
			strQuerySummary += "Synonym  = " + txtSynonym.getText().trim();	
			strTabName = "Gene Syn: ";
			strQueryWhereSQL += getSQLList(txtSynonym, "synonyms");
		}
	}
	private void getSQLGeneral() {
		if(txtDescript.getText().trim().length() > 0) {
			getCombine();
			strQuerySummary += "Description  = " + txtDescript.getText().trim();	
			strTabName = "Gene Desc: ";
			strQueryWhereSQL += getSQLList(txtDescript, "descript");
		}
	
		if(txtChr.getText().trim().length() > 0) {
			getCombine();
			strQuerySummary += chrRoot + " = " + txtChr.getText().trim();	
			strQueryWhereSQL += getSQLChr();
		}
		if (optVariant.getSelectedOption()==0) {	
			getCombine();
			strQuerySummary += "Has SNPs ";
			strQueryWhereSQL += "(gene.cntSNP>0)";
		}
		else if (optVariant.getSelectedOption()==1) {	
			getCombine();
			strQuerySummary += "Has Missense ";
			strQueryWhereSQL += "(gene.cntMissense>0)";
		}
		else if (optVariant.getSelectedOption()==2) {	
			getCombine();
			strQuerySummary += "Has Indels ";
			strQueryWhereSQL += "(gene.cntIndel>0)";
		}
	}
	private String getSQLList(JTextField txt, String column) {
		String [] nameList = Static.str2arr(txt.getText().trim());
		String field = "gene." + column;
		String subQuery;
		
		if (nameList.length==1)
			subQuery = field + " LIKE '" + Static.getSubStr(nameList[0], "gene", column) + "'";
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
	
	/***************************************
	 * panel functions
	 */

	private void updatePanelDefault() {
		optVariant.setSelectedOption(1);
	}
	private void updatePanelClear() {
		txtGeneName.setText("");
		txtDescript.setText("");
		txtSynonym.setText("");
		txtChr.setText("");
		optVariant.setSelectedOption(3);
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
					try {
						if(val.length() > 0) Integer.parseInt(val);
					} catch(Exception e) {
						valid = false;
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
	}
	/*******************************************************
	 * Variables
	 */
	private String strQuerySummary="";
	private String strQueryWhereSQL="";
	private String strTabName="";
	
	private ViewerFrame theViewerFrame = null;
	private GeneColumn theColumnPanel = null;
	private String chrRoot;
	private Vector <String> chrStr;
	
	private CaretListener enableListener = null;
	private CaretListener validateIntListener = null;

	//Top button panel
	private JPanel buttonPanel = null;
	private JButton btnSearch = null;
	
	//Main panel
	private JPanel mainPanel = null;
	private CollapsiblePanel [] theSections = null;
		
	// basic
	private JTextField txtGeneName = null;
	private JTextField txtDescript = null;
	private JTextField txtSynonym = null;
	private JTextField txtChr = null;
	private OptionList optVariant = null;
}
