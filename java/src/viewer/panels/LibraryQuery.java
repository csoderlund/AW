package viewer.panels;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

import database.MetaData;
import util.Globals;
import viewer.ViewerFrame;
import viewer.controls.*;

public class LibraryQuery extends JPanel {
	private static final long serialVersionUID = 1672776836742705318L;

	private static final String [] SECTIONS = { "Basic"};
	private static final String [] SECTIONS_DESC = { "" };
	public static int resultCount = 0;
	
	public LibraryQuery(ViewerFrame vFrame) {
		theViewerFrame = vFrame;
		MetaData md = vFrame.getMetaData();
		strAbbr  = md.getStrAbbv();
		tisAbbr  = md.getTisAbbv();
		hasCond2 = md.hasCond2();
		
		mainPanel = new JPanel();
		setListeners();	
		
		createButtonPanel();
		createSections();
		
		removeAll();
		setBackground(Globals.COLOR_BG);
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		mainPanel.removeAll();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
		mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		mainPanel.setBackground(Globals.COLOR_BG);
		
		mainPanel.add(buttonPanel);
		mainPanel.add(Box.createVerticalStrut(15));
		if(showButtonPanel != null) {
			mainPanel.add(showButtonPanel);
			mainPanel.add(Box.createVerticalStrut(10));
		}
		
		Dimension d = mainPanel.getMaximumSize();
		d.height = mainPanel.getPreferredSize().height;
		mainPanel.setMaximumSize(d);
		
		add(mainPanel);
		/**
		for(int x=0; x<theSections.length; x++) {
			theSections[x].expand();
			add(theSections[x]);
		}
		**/
	}
	/**************************************************
	 * XXX Create top button panel
	 */
	private void createButtonPanel() {
		buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
		buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttonPanel.setBackground(Globals.COLOR_BG);
		
		btnSearch = CreateJ.buttonFun("Display Libraries");
		btnSearch.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				getSQLBasic();
				LibraryTable tempPanel = new LibraryTable(theViewerFrame, 
						"Library " + Globals.TAB_Q + (++resultCount) + ": ", strQueryWhereSQL, strQuerySummary);
				theViewerFrame.addResultPanel(tempPanel, tempPanel.getTabName(), strQuerySummary);
			}
		});
		buttonPanel.add(btnSearch);
		buttonPanel.add(Box.createHorizontalStrut(5));
		btnClear = new JButton("Clear");
		btnClear.setBackground(Globals.COLOR_BG);
		btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearFilters();
				updatePanelEnable();
			}
		});
		//buttonPanel.add(btnClear);
		buttonPanel.add(Box.createHorizontalStrut(5));
		JButton btnHelp = new JButton("Help");
        btnHelp.setBackground(Globals.COLOR_HELP);
        btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				ViewTextPane.displayHTML(theViewerFrame, "Library Query Help", "html/LibraryQuery.html");
			}
		});
		//buttonPanel.add(btnHelp);
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
	}

	/****************************************************************
	 * Specific sections
	 */
	private JPanel createBasicPanel() {
		JPanel page = CreateJ.panelPage();
		
		JPanel row1 = CreateJ.panelTextLine(Globals.cond1);
		txtStrain = new JTextField(20);
		txtStrain.addCaretListener(enableListener);
		row1.add(txtStrain);
		row1.add(Box.createHorizontalStrut(5));
		row1.add(new JLabel("(substring)"));	
		page.add(row1);
		page.add(Box.createVerticalStrut(10));

		JPanel row2 = CreateJ.panelTextLine(Globals.cond2);
		txtTissue = new JTextField(20);
		txtTissue.addCaretListener(enableListener);
		row2.add(Box.createHorizontalStrut(5));
		row2.add(txtTissue);
		row2.add(Box.createHorizontalStrut(5));
		row2.add(new JLabel("(substring)"));	
		if (hasCond2) page.add(row2);	
		
		return page;
	}
	
	private void clearFilters() {
		txtStrain.setText("");
		txtTissue.setText("");
	}
	
	private void updatePanelEnable() {
		// both fields can be entered
		txtTissue.setEnabled(true);
		txtStrain.setEnabled(true);
	}
	
	/**************************************************************
	 *  XXX MYSQL x
	 */
	private void getSQLBasic() {
		strQueryWhereSQL = strQuerySummary = "";
		
		String searchStr = txtStrain.getText().trim();
		if(txtStrain.isEnabled() && searchStr.length() > 0) {
			strQueryWhereSQL = "library.strain LIKE '" + 
						Static.getSubStr(searchStr,"library","strain") + "'";
			strQuerySummary = "Strain = '" + searchStr + "'";
		}
		searchStr = txtTissue.getText().trim();
		if(txtTissue.isEnabled() && searchStr.length() > 0) {
			if (!strQueryWhereSQL.equals("")) {
				strQueryWhereSQL += " and ";
				strQuerySummary += " and";
			}
			strQueryWhereSQL = "library.tissue LIKE '" + 
						Static.getSubStr(searchStr,"library", "tissue") + "'";
			strQuerySummary += "Tissue = '" + searchStr + "'";
		}
		if (strQueryWhereSQL.equals("")) strQuerySummary = "Show All";
	}
	
	/**************************************************************************/
	//Do not allow the user to search if there are non-int values in int fields
	private void setListeners() {
		validateIntListener = new CaretListener() {
			public void caretUpdate(CaretEvent arg0) {
				boolean valid = true;
				if(arg0.getSource() instanceof JTextField) {
					JTextField temp = (JTextField)arg0.getSource();				
					String val = temp.getText();					
					try {
						if(val.length() > 0) Integer.parseInt(val);
					} catch(Exception e) {
						valid = false;
					}
					btnSearch.setEnabled(valid);
					if(!valid) temp.setForeground(Color.RED);
					else temp.setForeground(Color.BLACK);
				}
			}
		};
		enableListener = new CaretListener() {
			public void caretUpdate(CaretEvent arg0) {
				updatePanelEnable();
			}
		};
	}
	private String [] strAbbr = null;
	private String [] tisAbbr = null;
	private boolean hasCond2;
	
	private String strQuerySummary=null;
	private String strQueryWhereSQL=null;
	private ViewerFrame theViewerFrame = null;
	
	//validation
	private CaretListener validateIntListener = null;
	private CaretListener enableListener = null;
	
	//Top button panel
	private JPanel buttonPanel = null;
	private JButton btnSearch = null;
	private JButton btnClear = null;
	
	//Main panel
	private JPanel mainPanel = null;
	private JPanel showButtonPanel = null;
	//Left out for now since we don't need expand/collapse all
//	private JButton btnExpand = null, btnCollapse = null;
	private CollapsiblePanel [] theSections = null;
	
	//Basic panel
	private JTextField txtStrain = null;
	private JTextField txtTissue = null;
}
