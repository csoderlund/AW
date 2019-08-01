package viewer.panels;
/****************************************
 * Query on SNP table only (not SNPlib, SNPgene or SNPtrans)
 */
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
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

import util.Globals;
import util.LogTime;
import viewer.ViewerFrame;
import viewer.controls.*;

public class SNPQuery extends JPanel {
	private static final long serialVersionUID = 7503398976887871683L;
	private static final String [] SECTIONS = { "Basic", "General", "Attributes", "Libraries"};
	private static final String [] SECTIONS_DESC = { "" , "", "", ""};
	public static int resultCount = 0;
	
	public SNPQuery(ViewerFrame vFrame) {
		theViewerFrame = vFrame;
		hasCond2 = vFrame.getMetaData().hasCond2();
		chrRoot = vFrame.getMetaData().getChrRoot();
		chrStr = vFrame.getMetaData().getChr();
		mainPanel = new JPanel();
		setValidate();	
		
		removeAll();
		setBackground(Globals.COLOR_BG);
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		createButtonPanel();
		add(buttonPanel);
		add(Box.createVerticalStrut(15));
		
		mainPanel = CreateJ.panelPage();
		JScrollPane sPane =  new JScrollPane ( mainPanel);
		sPane.setBorder( null );
		sPane.setPreferredSize(theViewerFrame.getSize());
		sPane.getVerticalScrollBar().setUnitIncrement(15);
		sPane.setAlignmentX(Component.LEFT_ALIGNMENT);
			
		createSections();
		for(int x=0; x<theSections.length; x++) {
			theSections[x].expand();
			mainPanel.add(theSections[x]);
		}
		Dimension d = mainPanel.getMaximumSize();
		d.height = mainPanel.getPreferredSize().height;
		mainPanel.setMaximumSize(d);
		
		add(sPane);
	}
	/**************************************************
	 * XXX Create top button panel
	 */
	private void createButtonPanel() {
		buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
		buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttonPanel.setBackground(Globals.COLOR_BG);
		
		btnSearch = CreateJ.buttonFun("Search Variants");
		btnSearch.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {		
				if (getSQLwhere()) {
					SNPTable tempPanel = new SNPTable(theViewerFrame, 
						"Var " + Globals.TAB_Q + (++resultCount) + ": ", 
						strQueryWhereSQL, strQuerySummary);
					theViewerFrame.addResultPanel(tempPanel, tempPanel.getTabName(), strQuerySummary);
				}
			}
		});
		buttonPanel.add(btnSearch);
		buttonPanel.add(Box.createHorizontalStrut(15));
		
		btnDefault = new JButton("Limit");
		btnDefault.setBackground(Globals.COLOR_BG);
		btnDefault.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updatePanelClear();
				updateDefaults();
			}
		});
		buttonPanel.add(btnDefault);
		buttonPanel.add(Box.createHorizontalStrut(5));
		
		btnClear = new JButton("Clear");
		btnClear.setBackground(Globals.COLOR_BG);
		btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updatePanelClear();
			}
		});
		buttonPanel.add(btnClear);
		buttonPanel.add(Box.createHorizontalStrut(15));
		
		JButton btnHelp = new JButton("Help");
        btnHelp.setBackground(Globals.COLOR_HELP);
        btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				ViewTextPane.displayHTML(theViewerFrame, "SNP Query Help", "html/SNPQuery.html");
			}
		});	
		buttonPanel.add(btnHelp);
		buttonPanel.setMaximumSize(buttonPanel.getPreferredSize());
	}
	/************************************************
	 * Create Sections
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
	}

	/****************************************************************
	 * Specific sections
	 */
	private JPanel createBasicPanel() {
		JPanel page = CreateJ.panelPage();
		JPanel row;
		
		txtSNPname = CreateJ.textField(20, enableListener, null);
		row = CreateJ.panelTextLine("rsID", txtSNPname, "(substring or list)");
		final JButton btnLoadNames = new JButton("Load file");
		btnLoadNames.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnLoadNames.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				FileMethods.readNamesFromFile(theViewerFrame,txtSNPname);
			}
		});        		
		page.add(row);
		
		return page;
	}
	private JPanel createGeneralPanel() {
		JPanel page = CreateJ.panelPage();
		JPanel row;
		
		txtFunction =  CreateJ.textField(20, enableListener, null);
		row = CreateJ.panelTextLine("effectList", txtFunction, "(substring)");
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		// Chromosome
		txtChr = CreateJ.textField(enableListener, null);
		String t = (chrRoot.equalsIgnoreCase("chr")) ? "Chr" : "Chr (" + chrRoot + ")";
		row = CreateJ.panelTextLine(t, txtChr, "(integer or letter, e.g. X, Y)");
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		row = CreateJ.panelLine();
		String [] choice = { "Has odRmk ", "No odRmk ", "Any"};
		optHasRemark= new OptionList(choice, 2);	
		row.add(optHasRemark);
		page.add(row);
		return page;
	}
	private JPanel createVariantPanel() {
		JPanel page = CreateJ.panelPage();
		JPanel row;
		
		row = CreateJ.panelTextLine("Variant");
		String [] var = {"SNP", "InDel", "Any"};
		optVariant= new OptionList(var, 2);	
		row.add(optVariant);
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		row = CreateJ.panelTextLine("Coding");
		String [] cod = {"Yes", "No", "Any"};
		optCoding= new OptionList(cod, 2);	
		row.add(optCoding);
		page.add(row);
		page.add(Box.createVerticalStrut(5));
				
		row = CreateJ.panelTextLine("SNP");
		String [] dam = {"Missense", "Damaging", "Any"};
		optDamaging= new OptionList(dam, 2);	
		row.add(optDamaging);
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		row = CreateJ.panelTextLine("SNP Cov(>=" + Globals.MIN_READS +")");
		String [] cov = {"Yes", "No", "Any"};
		optCoverage= new OptionList(cov, 2);	
		row.add(optCoverage);
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		row = CreateJ.panelTextLine("SNP AI(P<0.05)");
		String [] ai = {"Yes", "No", "Any"};
		optAIpval= new OptionList(ai, 2);	
		row.add(optAIpval);
		page.add(row);
		
		return page;
	}
	/****************************************************************
	 * XXX Library panel - 3 include/exclude - Strain, Tissue, Score
	 */
	private JPanel createLibraryPanel() {		
		JPanel page = CreateJ.panelPage();
		JPanel row;
		int sp1=75;
		int sp2=0;
	
		// Coverage: [   ]   o <=  o >=
		txtSNPCovMin = CreateJ.textField(enableListener, validateIntListener);
		txtSNPCovMin.setText(Integer.toString(Globals.MIN_READS));
		txtSNPCovMax = CreateJ.textField(enableListener, validateIntListener);

		row = CreateJ.panelTextLine("Coverage", sp1,  ">=", sp2,  txtSNPCovMin);
		CreateJ.addPanelTextLine(row, sp2, "and <=", txtSNPCovMax);
		row.add(Box.createHorizontalStrut(20));	
		row.add(new JLabel("(integer)"));
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		// Score: <= [   ] OR >= [   ]
		txtSNPScoreMin = CreateJ.textField(enableListener, validateRealListener);
		txtSNPScoreMax = CreateJ.textField(enableListener, validateRealListener);
		row = CreateJ.panelTextLine("Score", sp1,  "<=", sp2, txtSNPScoreMin);
		CreateJ.addPanelTextLine(row, sp2, " or  >=", txtSNPScoreMax);
		row.add(Box.createHorizontalStrut(20));
		row.add(new JLabel("(fraction)"));
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		// P-value: <= [   ] OR >= [   ]
		txtSNPpval = CreateJ.textField(enableListener, validateRealListener);
		txtSNPpval.setText("");
		row = CreateJ.panelTextLine("AI Pval", sp1, " < ", sp2,  txtSNPpval);
		row.add(Box.createHorizontalStrut(10));
		
		String [] REFALT = { "Ref>Alt", "Alt>Ref", "Any" };
		optPV= new OptionList(REFALT, 2);	
		row.add(optPV);
		
		row.add(Box.createHorizontalStrut(15));
		row.add(new JLabel("(fraction)"));
		page.add(row);
		page.add(Box.createVerticalStrut(5));
		
		row = CreateJ.panelTextLine("Libraries");
		String [] ALL = { "All", "Any" };
		optAllAny= new OptionList(ALL, 0);	
		row.add(optAllAny);
		page.add(row);
		page.add(Box.createVerticalStrut(10));
		
		// Select Strain			
		String [] strAbbr  = theViewerFrame.getMetaData().getStrAbbv();
		String [] tisAbbr  = theViewerFrame.getMetaData().getTisAbbv();
		
		ieStrain = new IncludeExclude(strAbbr, "Cond1: " +Globals.condition1, "Select from");
		ieStrain.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		page.add(ieStrain);
		page.add(Box.createVerticalStrut(5));
		
		// Select Tissue
		ieTissue = new IncludeExclude(tisAbbr, "Cond2: " + Globals.condition2, "Select from");
		ieTissue.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		if (hasCond2) page.add(ieTissue);
	
		return page;
	}
	private void updatePanelClear() {
		txtSNPname.setText("");
		txtFunction.setText("");	
		txtChr.setText("");
		optHasRemark.setSelectedOption(2);
		optCoding.setSelectedOption(2);
		optCoverage.setSelectedOption(2);
		optVariant.setSelectedOption(2);
		optDamaging.setSelectedOption(2);
		optAIpval.setSelectedOption(2);
		txtSNPCovMin.setText("");
		txtSNPCovMax.setText("");
		txtSNPScoreMin.setText("");
		txtSNPScoreMax.setText("");
		txtSNPpval.setText("");
		ieStrain.clear();
		ieTissue.clear();
	}
	private void updateDefaults() {
		optDamaging.setSelectedOption(0);
		optAIpval.setSelectedOption(0);
	}
	
	/**************************************************************
	 *  XXX MYSQL 
	 */
	private boolean getSQLwhere() {
		strQueryWhereSQL = strQuerySummary = "";
		getSQLBasic();
		if (!strQueryWhereSQL.equals("")) return true;
		
		int w = getCheckSections();
		if (w == -1) return false;
		
		getSQLGeneral();
		getSQLVariant();
		if (w==1) getSQLLibrary(); 	
		if (strQuerySummary.equals("")) strQuerySummary = "Show all ";
		
		return true;
	}	
	/*****************
	 * XXX test input from user
	 */
	private int getCheckSections() {
		if (txtChr.getText().trim().length()>0) {
			String x = getSQLChr();
			if (x.equals("")) return -1;
		}
	
		if (optVariant.getSelectedOption()==1 && optDamaging.getSelectedOption()!=2) {
			JOptionPane.showMessageDialog(theViewerFrame, 
					"Cannot select Indel and Missense or Damaging",
					"Attributes", JOptionPane.PLAIN_MESSAGE);
			return -1;
		}
		
		int state=0;
		String [] strainInc = ieStrain.includeList();
		String [] tissueInc = ieTissue.includeList();	
		if (strainInc!=null && strainInc.length > 0) state++;
		if (tissueInc!=null && tissueInc.length > 0) state++;
		if (state==0) return 0;
		
		if (state==1) {
			JOptionPane.showMessageDialog(theViewerFrame, 
					"Please enter both Strain and Tissue",
					"Library", JOptionPane.PLAIN_MESSAGE);
			return -1;
		}
		else if (state==2) {
			if (txtSNPCovMin.getText().trim().length()>0) state++;
			else if (txtSNPCovMin.getText().trim().length()>0) state++;
			else if (txtSNPScoreMin.getText().trim().length()>0) state++;
			else if (txtSNPScoreMin.getText().trim().length()>0) state++;
			else if (txtSNPpval.getText().trim().length()>0) state++;
			else {
				JOptionPane.showMessageDialog(theViewerFrame, 
						"Please enter Coverage and/or Score and/or P-value",
						"Library", JOptionPane.PLAIN_MESSAGE);
				return -1;
			}
		}		
		return 1;
	}
	private void getCombine() {
		if (strQueryWhereSQL.length()>0) {
			 strQueryWhereSQL += " AND ";
			 strQuerySummary += ", ";
		}
	}
	
	private void getSQLBasic() {
		String searchStr = "";
		
		searchStr = txtSNPname.getText().trim();
		if(searchStr.length() > 0) {
			strQueryWhereSQL = getSQLList(searchStr, "rsID");
			strQuerySummary = "rsID = '" + searchStr + "'";
		}		
		
	}	
	private void getSQLGeneral() {
		String searchStr = "";
		
		searchStr = txtFunction.getText().trim();
		if(searchStr.length() > 0) {
			getCombine();
			strQueryWhereSQL += "SNP.effectList like '%" + searchStr + "%'";
			strQuerySummary += "effectList = '" + searchStr + "'";
		}		
		if(txtChr.getText().trim().length() > 0) {
			getCombine();
			strQuerySummary += chrRoot + " = " + txtChr.getText().trim();	
			strQueryWhereSQL += getSQLChr();
		}
		if (optHasRemark.getSelectedOption()==0) {
			getCombine();
			strQuerySummary += " Has odRmk ";
			strQueryWhereSQL += "SNP.remark != '' ";
		}
		else if (optHasRemark.getSelectedOption()==1) {
			getCombine();
			strQuerySummary += " No odRmk ";
			strQueryWhereSQL += "SNP.remark = ''";
		}
	}
	private void getSQLVariant() {
		if (optVariant.getSelectedOption()==0) {
			getCombine();
			strQuerySummary += "SNP only";
			strQueryWhereSQL += "SNP.isSNP=1";
		}
		else if (optVariant.getSelectedOption()==1) {
			getCombine();
			strQuerySummary += "Indel only";
			strQueryWhereSQL += "SNP.isSNP=0";
		}
		if (optCoverage.getSelectedOption()==0) {
			getCombine();
			strQuerySummary += "LibCov" + Globals.MIN_READS;
			strQueryWhereSQL += "SNP.cntLibCov>0";
		}
		else if (optCoverage.getSelectedOption()==1) {
			getCombine();
			strQuerySummary += "No Coverage";
			strQueryWhereSQL += "SNP.cntLibCov=0";
		}
		
		if (optCoding.getSelectedOption()==0) {
			getCombine();
			strQuerySummary += "Is Coding";
			strQueryWhereSQL += "SNP.isCoding>0";
		}
		else if (optCoding.getSelectedOption()==1) {
			getCombine();
			strQuerySummary += "Is Not Coding";
			strQueryWhereSQL += "SNP.isCoding=0";
		}

		if (optDamaging.getSelectedOption()==0) {
			getCombine();
			strQuerySummary += "Missense";
			strQueryWhereSQL += "SNP.isMissense>0";
		}
		else if (optDamaging.getSelectedOption()==1) {
			getCombine();
			strQuerySummary += "Damaging";
			strQueryWhereSQL += "SNP.isDamaging>0";
		}
		
		if (optAIpval.getSelectedOption()==0) {
			getCombine();
			strQuerySummary += "LibAI pval<0.05";
			strQueryWhereSQL += "SNP.cntLibAI>0";
		}
		else if (optDamaging.getSelectedOption()==1) {
			getCombine();
			strQuerySummary += "Not AI";
			strQueryWhereSQL += "SNP.cntLibAI=0";
		}
	}	
	private String getSQLList(String list, String column) {
		String [] nameList = Static.str2arr(list.trim());
		String field = "SNP." + column;
		String subQuery;
		if (nameList.length==1)
			subQuery = field + " LIKE '" + Static.getSubStr(nameList[0], "SNP", column) + "'";
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
	/*********************************************************
	 * XXX Select Score, strain and tissue
	 */
	private void getSQLLibrary() {
		
	// get all text input, already been checked
		String [] strainInc = ieStrain.includeList();
		String [] tissueInc = ieTissue.includeList();		
		String covMin= txtSNPCovMin.getText().trim();
		String covMax= txtSNPCovMax.getText().trim();	
		String scoreMin = txtSNPScoreMin.getText().trim();
		String scoreMax = txtSNPScoreMax.getText().trim();
		String pvSNP = txtSNPpval.getText().trim();
		
		getCombine();
		
	// Coverage - used in Tissue/Strain clause (set Summary here)
		boolean hasCovMin = (covMin.length()==0) ? false : true;
		boolean hasCovMax = (covMax.length()==0) ? false : true;
		if (hasCovMin && hasCovMax) strQuerySummary += "Coverage >=" + covMin + " and <= " + covMax+ "; ";
		else if (hasCovMin) strQuerySummary += "Coverage >=" + covMin + "; ";
		else if (hasCovMax) strQuerySummary += "Coverage <=" + covMax + "; ";
		
	// Score - used in Tissue/Strain clause (set Summary here)
		
		boolean hasScoreMin = (scoreMin.length()==0) ? false : true;
		boolean hasScoreMax = (scoreMax.length()==0) ? false : true;	
		
		if (hasScoreMin && hasScoreMax) strQuerySummary += "Score <=" + scoreMin + " or >=" + scoreMax+ "; ";
		else if (hasScoreMin) strQuerySummary += "Score <=" + scoreMin + "; ";
		else if (hasScoreMax) strQuerySummary += "Score >=" + scoreMax + "; ";
		
		// ASE p-value 
		String totOp="";
		int optPVdirection = optPV.getSelectedOption();
		if (optPVdirection==0)  totOp=" (Ref>Alt)";
		else if (optPVdirection==1)  totOp=" (Alt>Ref)";
		boolean hasPV = (pvSNP.length()==0) ? false : true;
		if (hasPV) {
			if (totOp.equals("")) strQuerySummary += "Pval<" + pvSNP+ "; ";
			else strQuerySummary += "Pval<" + pvSNP + " " +  totOp + ";";
		}
		
	// Tissues and Strains
		String R = Globals.PRE_REFCNT;
		String A = Globals.PRE_ALTCNT;
					
		Vector <String> syncLibs = new Vector <String> (); 
		
		String libOp = " AND ";
		String libs="";
		if (optAllAny.getSelectedOption()==1) libOp =" OR ";
		if (strainInc.length >1 || tissueInc.length>1) {
			if (optAllAny.getSelectedOption()==1) libs = "|";
			else  libs = "&";
		}
		strQueryWhereSQL += "(";
		for (int s=0; s<strainInc.length; s++) {
			String strain = strainInc[s];
			for (int t=0; t<tissueInc.length; t++) {
				String lib = strain + tissueInc[t];
				libs += (t==0&&s==0) ? lib : ("," + lib);
				syncLibs.add(lib);
				
				String clause="(";
				boolean first=true;
				String Sref = "SNP." + R + lib; 	
				String Salt = "SNP." + A + lib; 	
				String Dsnp = "SNP." + lib ;		
				String DsnpA = "ABS(" + Dsnp + ")";		
				
				if (hasCovMin || hasCovMax) {
					first=false; 		
					String xx = "(" + Sref + "+" +  Salt + ")";
					if (hasCovMin && hasCovMax)
						clause +=  xx  + " >= " + covMin + " and " + xx + " <= " + covMax;
					else if (hasCovMin) clause += xx + " >= " + covMin;
					else clause += xx + " <= " + covMax;
				}	
				if (hasScoreMin || hasScoreMax) {
					if (first) first=false; else clause += " AND "; 
					String xx = Globals.scoreStr(Sref, Salt);
					if (hasScoreMin && hasScoreMax) 
						clause += "(" +  xx  + " <= " + scoreMin + " or " +  xx  + " >= " + scoreMax + ")" ;
					else if (hasScoreMin) clause +=  xx + " <= " + scoreMin ;
					else if (hasScoreMax) clause +=  xx + " >= " + scoreMax ;
				}
				if (hasPV) {
					if (first) first=false; else clause += " AND "; 
					if (optPVdirection==0) clause += Sref + " > " + Salt + "  AND ";
					else if (optPVdirection==1)  clause += Sref + " < " + Salt + " AND ";
					clause += DsnpA + " < " + pvSNP ;
				}
				clause += ")";

				if (s==0 && t==0) strQueryWhereSQL += clause; 
				else strQueryWhereSQL += libOp + clause;
			}
		}
		strQuerySummary = libs + "; " + strQuerySummary;
		strQueryWhereSQL += ")";
		theViewerFrame.getSNPColumnSync().setLibs(syncLibs);
		
		Vector <String> syncAbbr = new Vector <String> ();
		for (int s=0; s<strainInc.length; s++) syncAbbr.add(strainInc[s]);
		for (int t=0; t<tissueInc.length; t++) syncAbbr.add(tissueInc[t]);
		theViewerFrame.getSNPColumnSync().setAbbr(syncAbbr);
	}
	/************************************************************************/
	private void setValidate() {
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
	private String strQuerySummary="";
	private String strQueryWhereSQL="";
	private ViewerFrame theViewerFrame = null;
	private boolean hasCond2;
	private String chrRoot;
	private Vector <String> chrStr;
	
	//validation
	private CaretListener validateIntListener = null;
	private CaretListener validateRealListener = null;
	private CaretListener enableListener = null;
	
	//Top button panel
	private JPanel buttonPanel = null;
	private JButton btnSearch = null;
	private JButton btnClear = null;
	private JButton btnDefault = null;
	
	//Main panel
	private JPanel mainPanel = null;
	//Left out for now since we don't need expand/collapse all
//	private JButton btnExpand = null, btnCollapse = null;
	private CollapsiblePanel [] theSections = null;
	
	//Basic panel
	private JTextField txtSNPname = null;
	private JTextField txtFunction = null;
	private JTextField txtChr = null;
	private OptionList optHasRemark = null;
	
	private JTextField txtSNPCovMin = null;
	private JTextField txtSNPCovMax = null;
	private JTextField txtSNPScoreMin = null;
	private JTextField txtSNPScoreMax = null;
	private JTextField txtSNPpval = null;
	private OptionList optPV = null;
	
	private OptionList optAIpval = null;
	private OptionList optCoverage = null;
	private OptionList optCoding = null;
	private OptionList optVariant = null;
	private OptionList optDamaging = null;
	private OptionList optAllAny = null;
	private IncludeExclude ieStrain = null;
	private IncludeExclude ieTissue = null;
}
