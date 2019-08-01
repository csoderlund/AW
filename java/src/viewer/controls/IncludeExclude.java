package viewer.controls;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import util.Globals;

/*******************************************
 * IncludeExclude copied from some ExV code
 * Everything is off by default
 */
public class IncludeExclude extends JPanel {
		private static final long serialVersionUID = 3168533480285816618L;
		private static final int WIDTH = 200;
		
		public IncludeExclude(String [] nameList, String label1, String label2) {

			theNameList = new String[nameList.length];
			isInclude = new boolean[nameList.length];
			
			for(int x=0; x<nameList.length; x++) {
				theNameList[x] = nameList[x];
				isInclude[x] = false;
			}
		
			setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setBackground(Globals.COLOR_BG);
			
			//Initialize both list to empty, refresh when all is built
			includeList = new JList (emptyList);
			includeList.setEnabled(false);
			
			excludeList = new JList(emptyList);
			excludeList.setEnabled(false);
			
			includeList.setVisibleRowCount(4);
			excludeList.setVisibleRowCount(4);
			
			JScrollPane incPane = new JScrollPane(includeList);
			incPane.setAlignmentX(Component.LEFT_ALIGNMENT);
			JScrollPane exPane = new JScrollPane(excludeList);
			exPane.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			JPanel incPanel = CreateJ.panelPage();
			JLabel incLabel = new JLabel(label1);
			incLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			incPanel.add(incLabel);
			incPanel.add(incPane);

			JPanel exPanel = CreateJ.panelPage();
			JLabel exLabel = new JLabel(label2);
			exLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			exPanel.add(exLabel);
			exPanel.add(exPane);

			JPanel buttonPanel = createButtonPanel();
			
			Dimension d = new Dimension(WIDTH, buttonPanel.getPreferredSize().height);
			incPane.setPreferredSize(d);
			exPane.setPreferredSize(d);

			incPane.setMaximumSize(incPane.getPreferredSize());
			exPane.setMaximumSize(exPane.getPreferredSize());

			add(incPanel);
			add(Box.createHorizontalStrut(10));
			add(buttonPanel);
			add(Box.createHorizontalStrut(10));
			add(exPanel);	
			
			updateView();
		}
		
		private JPanel createButtonPanel() {
			JPanel retVal = CreateJ.panelPage();
			
			btnAddAllInc = CreateJ.button(">>");
			btnAddAllInc.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					selectAll(false);
					updateView();
				}
			});

			btnAddInc = CreateJ.button(">");
			btnAddInc.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					Object [] vals = includeList.getSelectedValues();
					for(int x=0; x<vals.length; x++)
						flipItem(vals[x]);
					updateView();
				}
			});

			btnAddEx = CreateJ.button("<");
			btnAddEx.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					Object [] vals = excludeList.getSelectedValues();
					for(int x=0; x<vals.length; x++)
						flipItem(vals[x]);
					updateView();
				}
			});

			btnAddAllEx = CreateJ.button("<<");
			btnAddAllEx.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					selectAll(true);
					updateView();
				}
			});
			
			//Thrown in to make the controls line up
			retVal.add(new JLabel(" "));
			retVal.add(btnAddAllInc);
			retVal.add(Box.createVerticalStrut(5));
			retVal.add(btnAddInc);
			retVal.add(Box.createVerticalStrut(5));
			retVal.add(btnAddEx);
			retVal.add(Box.createVerticalStrut(5));
			retVal.add(btnAddAllEx);
			
			retVal.setMaximumSize(retVal.getPreferredSize());
			retVal.setMinimumSize(retVal.getPreferredSize());

			return retVal;
		}	
		private void flipItem(Object item) {
			for(int x=0; x<theNameList.length; x++) {
				if(theNameList[x].equals(item)) {
					isInclude[x] = !isInclude[x];
					return;
				}
			}
		}	
		
		public void selectAll(boolean include) {
			for(int x=0; x<isInclude.length; x++)
				isInclude[x] = include;
			updateView();
		}	
		public void selectItem(String name) {
			flipItem(name);
			updateView();
		}	
		public int getNumIncluded() {
			int retVal = 0;
			for(int x=0; x<isInclude.length; x++)
				if(isInclude[x])
					retVal++;
			return retVal;
		}		
		public int getNumExcluded() {
			int retVal = 0;
			for(int x=0; x<isInclude.length; x++)
				if(!isInclude[x])
					retVal++;
			return retVal;
		}		
		public String [] includeList() {
			if(getNumIncluded() == 0) return null;
			
			String [] retVal = new String[getNumIncluded()];
			int index = 0;
			for(int x=0; x<theNameList.length; x++)
				if(isInclude[x]) {
					retVal[index] = theNameList[x].toString();
					index++;
				}		
			return retVal;
		}		
		public String [] excludeList() {
			if(getNumExcluded() == 0) return null;
			
			String [] retVal = new String[getNumExcluded()];
			int index = 0;
			for(int x=0; x<theNameList.length; x++)
				if(!isInclude[x]) {
					retVal[index] = theNameList[x].toString();
					index++;
				}
			
			return retVal;
		}
		
		private void updateView() {
			Object [] incList = includeList();
			if(incList == null) {
				includeList.setListData(emptyList);
				includeList.setEnabled(false);
			} 
			else {
				includeList.setListData(incList);
				includeList.setEnabled(true);
			}

			Object [] exList = excludeList();
			if(exList == null) {
				excludeList.setListData(emptyList);
				excludeList.setEnabled(false);
			} 
			else {
				excludeList.setListData(exList);
				excludeList.setEnabled(true);
			}
		}
		
		public void setEnabled(boolean enabled) {
			includeList.setEnabled(enabled);
			excludeList.setEnabled(enabled);
			btnAddAllInc.setEnabled(enabled);
			btnAddInc.setEnabled(enabled);
			btnAddEx.setEnabled(enabled);
			btnAddAllEx.setEnabled(enabled);
		}
		
		public boolean isEnabled() { return includeList.isEnabled(); }
		
		public void clear() {
			includeList.setListData(emptyList);
			includeList.setEnabled(true);
			excludeList.setListData(theNameList);
			excludeList.setEnabled(true);
			for(int x=0; x<theNameList.length; x++) {
				isInclude[x] = false;
			}
		}
		
		//Data holders
		private Object [] theNameList = null;
		private boolean [] isInclude = null;
		private Object [] emptyList = {"empty list"};
		
		//UI controls
		private JList includeList = null;
		private JList excludeList = null;
		private JButton btnAddAllInc = null;
		private JButton btnAddInc = null;
		private JButton btnAddEx = null;
		private JButton btnAddAllEx = null;
}
