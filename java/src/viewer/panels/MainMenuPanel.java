package viewer.panels;

/**************************************************
 * Called by ViewerFrame
 */
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

import viewer.ViewerFrame;
import util.Globals;

public class MainMenuPanel extends JPanel {
	private static final long serialVersionUID = 7029184547060500871L;
	
	private static final String FONT_NAME = "Dialog.plain";
	private static final int FONT_STYLE = Font.PLAIN;
	private static final int FONT_SIZE = 14;
	
	private final static int TABSIZE = 10;
	
	public MainMenuPanel(ActionListener select, ActionListener close) {
		theSelectionListener = select;
		theCloseListener = close;
		
		theClickListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(e.getSource() instanceof JButton) {
					setSelectedByClick((JButton)e.getSource());
				}
			}
		};	
		root = new MenuItem(null, null, "", null, null);
	}
	
	public void setSelectedByClick(JButton refClick) {
		setSelectedChildByClick(root.child, refClick);
	}
	
	private static void setSelectedChildByClick(MenuItem rootNode, JButton refButton) {
		if(rootNode == null) return;
		if(refButton.equals(rootNode.btnItem)) {
			rootNode.theRefPanel.setVisible(true);
			rootNode.btnItem.setFont(new Font(rootNode.btnItem.getFont().getName(), Font.BOLD, 
					rootNode.btnItem.getFont().getSize()));
		}
		else if(rootNode.btnItem != null) {
			rootNode.theRefPanel.setVisible(false);
			rootNode.btnItem.setFont(new Font(rootNode.btnItem.getFont().getName(), Font.PLAIN, 
					rootNode.btnItem.getFont().getSize()));
		}		
		setSelectedChildByClick(rootNode.child, refButton);
		setSelectedChildByClick(rootNode.sibling, refButton);
	}	

	public void setSelected(JPanel refPanel) {
		setSelectedChild(root.child, refPanel);
		refresh();
	}
	
	private static void setSelectedChild(MenuItem rootNode, JPanel refPanel) {
		if(rootNode == null) return;
		
		if(refPanel.equals(rootNode.theRefPanel)) {
			rootNode.theRefPanel.setVisible(true);
			rootNode.btnItem.setFont(new Font(rootNode.btnItem.getFont().getName(), Font.BOLD, 
					rootNode.getFont().getSize()));
		}
		else if(rootNode.theRefPanel != null) {
			rootNode.theRefPanel.setVisible(false);
			rootNode.btnItem.setFont(new Font(rootNode.btnItem.getFont().getName(), Font.PLAIN, 
					rootNode.btnItem.getFont().getSize()));
		}
		setSelectedChild(rootNode.child, refPanel);
		setSelectedChild(rootNode.sibling, refPanel);
	}
	
	public JPanel getSelectedPanel() {
		return getSelectedChild(root.child);
	}
	
	private static JPanel getSelectedChild(MenuItem rootNode) {
		if(rootNode.btnItem.getFont().getStyle() == Font.BOLD)
			return rootNode.theRefPanel;
		
		JPanel retVal = null;
		
		if(rootNode.child != null)
			retVal = getSelectedChild(rootNode.child);
		if(retVal == null && rootNode.sibling != null)
			retVal = getSelectedChild(rootNode.sibling);
		
		return retVal;
	}
	
	public void addMenuItem(JPanel refParentPanel, JPanel refPanel, String name) {
		if(refParentPanel == null)
			root.child = addChild(root.child, new MenuItem(null, refPanel, name, theSelectionListener, null));
		else 
			root.child = addChild(root.child, new MenuItem(refParentPanel, refPanel, name, 
					theSelectionListener, theCloseListener));
		
		setSelected(refPanel);
		
		refresh();
	}
	
	private static MenuItem addChild(MenuItem rootNode, MenuItem newItem) {
		if(rootNode == null)
			return newItem;
		if((newItem.theRefParentPanel == null && rootNode.theRefParentPanel == null) || 
			(newItem.theRefParentPanel != null && newItem.theRefParentPanel.equals(rootNode.theRefParentPanel))) {
			rootNode.sibling = addChild(rootNode.sibling, newItem);
			return rootNode;
		}
		if((newItem.theRefParentPanel == null && rootNode.theRefParentPanel == null) || 
			(newItem.theRefParentPanel != null && newItem.theRefParentPanel.equals(rootNode.theRefPanel))) {
				rootNode.child = addChild(rootNode.child, newItem);
				return rootNode;
		}
		
		if(rootNode.child != null)
			rootNode.child = addChild(rootNode.child, newItem);
		if(rootNode.sibling != null)
			rootNode.sibling = addChild(rootNode.sibling, newItem);
		return rootNode;
	}
	
	public JPanel getMenuItem(JButton closeButton) {
		return getMenuItem(root.child, closeButton);
	}
	
	private static JPanel getMenuItem(MenuItem rootNode, JButton closeButton) {
		if(closeButton.equals(rootNode.btnClose))
			return rootNode.theRefPanel;
		
		JPanel retVal = null;
		if(rootNode.child != null)
			retVal = getMenuItem(rootNode.child, closeButton);
		if(retVal == null && rootNode.sibling != null)
			retVal = getMenuItem(rootNode.sibling, closeButton);
		
		return retVal;
	}
	
	public void removeMenuItem(JPanel refPanel) {
		root.child = removeMenuItem(root.child, refPanel);
		refresh();
	}
	
	private static MenuItem removeMenuItem(MenuItem rootNode, JPanel refPanel) {
		if(rootNode == null) return null;
		
		if(refPanel.equals(rootNode.theRefPanel))
			return rootNode.sibling;
		
		rootNode.child = removeMenuItem(rootNode.child, refPanel);
		rootNode.sibling = removeMenuItem(rootNode.sibling, refPanel);
		
		return rootNode;
	}
	
	public void renameMenuItem(JPanel refPanel, String newString) {
		 renameMenuItem(root.child, refPanel, newString);
	}
	
	private static void renameMenuItem(MenuItem rootNode, JPanel refPanel, String newString) {
		if(rootNode == null) return;
		if(refPanel.equals(rootNode.theRefPanel)) {
			rootNode.btnItem.setText(newString);
			return;
		}
		renameMenuItem(rootNode.child, refPanel, newString);
		renameMenuItem(rootNode.sibling, refPanel, newString);
	}
	
	private void refresh() {
		removeAll();
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBackground(Globals.COLOR_BG);

		buildPanel(root.child, 1);
		setMaximumSize(getPreferredSize());

		setVisible(false);
		setVisible(true);
	}
	
	private void buildPanel(MenuItem rootNode, int tabLevel) {
		if(rootNode == null) return;
		
		JPanel temp = createSubPanel(rootNode, tabLevel);
		
		int width = 0;
		for(int x = 0; x<temp.getComponentCount(); x++) {
			width += temp.getComponent(x).getPreferredSize().width;
		}
		
		temp.setMaximumSize(new Dimension(Math.max(300, temp.getPreferredSize().width), temp.getPreferredSize().height));
		
		add(temp);
		buildPanel(rootNode.child, tabLevel + 1);
		buildPanel(rootNode.sibling, tabLevel);
	}
	
	private static JPanel createSubPanel(MenuItem item, int tabLevel) {
		JPanel temp = new JPanel();
		temp.setLayout(new BoxLayout(temp, BoxLayout.LINE_AXIS));
		temp.setBackground(Globals.COLOR_BG);
		temp.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		if(item.isLeaf())
			item.btnClose.setVisible(true);
		else if(item.btnClose != null)
			item.btnClose.setVisible(false);
		
		temp.add(Box.createHorizontalStrut(TABSIZE * tabLevel));
		temp.add(item.theDisplayPanel);

		return temp;
	}
	
	private ActionListener theSelectionListener = null;
	private ActionListener theCloseListener = null;
	private ActionListener theClickListener = null;
	
	private MenuItem root = null;
	
	private class MenuItem extends JPanel {
		private static final long serialVersionUID = 1L;

		public MenuItem(JPanel refParentPanel, JPanel refPanel, String name, 
				ActionListener selectListener, ActionListener closeListener) {
			theRefParentPanel = refParentPanel;
			theRefPanel = refPanel;
			
			theDisplayPanel = new JPanel();
			theDisplayPanel.setLayout(new BoxLayout(theDisplayPanel, BoxLayout.LINE_AXIS));
			theDisplayPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			theDisplayPanel.setBackground(Globals.COLOR_BG);
			
			if(selectListener != null) {
				btnItem = new JButton(name);
				btnItem.setBackground(Globals.COLOR_BG);
				btnItem.setBorderPainted(false);
				btnItem.setFocusPainted(false);
				btnItem.setContentAreaFilled(false);
				btnItem.setMargin(new Insets(0, 0, 0, 0));
				btnItem.setVerticalAlignment(AbstractButton.TOP);
				btnItem.setHorizontalAlignment(AbstractButton.LEFT);
				btnItem.setFont(new Font(FONT_NAME, FONT_STYLE, FONT_SIZE));
				btnItem.addActionListener(theClickListener);
				btnItem.addActionListener(selectListener);
				theDisplayPanel.add(btnItem);
			}
			
			if(closeListener != null) {
				btnClose = new JButton("x");
				btnClose.setBackground(Globals.COLOR_BG);
				btnClose.setBorderPainted(false);
				btnClose.setFocusPainted(false);
				btnClose.setContentAreaFilled(false);
				btnClose.setMargin(new Insets(0, 0, 0, 0));
				btnClose.setVerticalAlignment(AbstractButton.TOP);
				btnClose.setHorizontalAlignment(AbstractButton.LEFT);
				btnClose.setFont(new Font(FONT_NAME, FONT_STYLE, FONT_SIZE));
				btnClose.addActionListener(closeListener);
				theDisplayPanel.add(Box.createHorizontalStrut(5));
				theDisplayPanel.add(btnClose);
			}
		}
		
		public boolean isLeaf() {
			return btnClose != null && child == null;
		}

		private MenuItem sibling = null;
		private MenuItem child = null;
		
		private JButton btnItem = null;
		private JButton btnClose = null;

		private JPanel theDisplayPanel = null;
		private JPanel theRefParentPanel = null;
		private JPanel theRefPanel = null;
	}
}
