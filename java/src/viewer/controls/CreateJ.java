package viewer.controls;
/*******************************************************
 * Various methods for display
 */
import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.BoxLayout;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.CaretListener;

import util.Globals;

public class CreateJ {
	static final int WIDTH=100;
	
	static public JPanel panelPage() {
	    JPanel retVal = new JPanel();
	    retVal.setBackground(Globals.COLOR_BG);
	    retVal.setAlignmentX(Component.LEFT_ALIGNMENT);         
	    retVal.setLayout(new BoxLayout(retVal, BoxLayout.PAGE_AXIS));
	    return retVal;
	}
	
	  //   centers the input window on the screen
    static public void centerScreen( Window win ) 
    {
          Dimension dim = win.getToolkit().getScreenSize();
          Rectangle abounds = win.getBounds();
          win.setLocation((dim.width - abounds.width) / 2,
              (dim.height - abounds.height) / 2);
    }
  
    //   centers the input window relative to its parent
    static public void centerParent ( Window win ) 
    {
          int x;
          int y;
        
          Container myParent = win.getParent();
          
          // Center using the parent if it's visible, just use the
          // screen otherwise
          if ( myParent.getWidth() > 0 && myParent.getHeight() > 0 )
          { 
                Point topLeft = myParent.getLocationOnScreen();
                Dimension parentSize = myParent.getSize();
            
                Dimension mySize = win.getSize();
            
                if (parentSize.width > mySize.width) 
                    x = ((parentSize.width - mySize.width)/2) + topLeft.x;
                else 
                    x = topLeft.x;
               
                if (parentSize.height > mySize.height) 
                    y = ((parentSize.height - mySize.height)/2) + topLeft.y;
                else 
                    y = topLeft.y;
               
                win.setLocation (x, y);
          }
          else
                centerScreen ( win );
    }  
	/** Lines (rows) **/
	static public JPanel panelLine() {
	    JPanel retVal = new JPanel();         
	    retVal.setBackground(Globals.COLOR_BG);
	    retVal.setAlignmentX(Component.LEFT_ALIGNMENT);         
	    retVal.setLayout(new BoxLayout(retVal, BoxLayout.LINE_AXIS)); 
	    return retVal;
	}
	static public JCheckBox createCheckBox(String label, boolean check, boolean enable) {
		JCheckBox chkBox = new JCheckBox(label);
		chkBox.setBackground(Color.white);
		chkBox.setSelected(check);
		chkBox.setEnabled(enable);
		return chkBox;
	}
	static public JPanel panelLabelLine (String label) {
		JPanel tempRow = CreateJ.panelLine();
		JLabel l = new JLabel(label);
		tempRow.add(l);
		tempRow.add(Box.createHorizontalStrut(5));
		return tempRow;
	}
	static public JPanel panelLabelLine (String label, Color c) {
		JPanel tempRow = CreateJ.panelLine();
		JLabel l = new JLabel(label);
		l.setForeground(c);
		//l.setFont(new Font("Courier", Font.ITALIC,16));
		tempRow.add(l);
		tempRow.add(Box.createHorizontalStrut(5));
		return tempRow;
	}
	// when there are multiple rows of Label [value], this spaces them the same
	static public JPanel panelTextLine (String label) {
		JPanel tempRow = CreateJ.panelLine();
		JLabel l = new JLabel(label);
		tempRow.add(l);
		Dimension d = l.getPreferredSize();
		int hGap = WIDTH -  d.width;
		if (hGap <= 0) hGap = 5;
		tempRow.add(Box.createHorizontalStrut(hGap));
		return tempRow;
	}
	static public JPanel panelTextLine (String label, int w) {
		JPanel tempRow = CreateJ.panelLine();
		JLabel l = new JLabel(label);
		tempRow.add(l);
		Dimension d = l.getPreferredSize();
		int hGap = w -  d.width;
		if (hGap <= 0) hGap = 5;
		tempRow.add(Box.createHorizontalStrut(hGap));
		return tempRow;
	}

	// Label [   ] Label2 - using default spacing
	static public JPanel panelTextLine (String label1, JTextField txtField, String label2) {
		JPanel panel = CreateJ.panelLine();
		JLabel l = new JLabel(label1);
		panel.add(l);
		Dimension d = l.getPreferredSize();
		int hGap = WIDTH -  d.width;
		if (hGap <= 0) hGap = 5;
		panel.add(Box.createHorizontalStrut(hGap));
			
		panel.add(txtField);
		panel.add(Box.createHorizontalStrut(5));
		
		panel.add(new JLabel(label2));
		return panel;
	}
	// Label1:  Label2 [   ] Label3 [   ]
	static public JPanel panelTextLine (String label1, int len1, String label2, int len2, 
			JTextField txtField, String label3, JTextField txtField2) {
		JPanel panel = CreateJ.panelLine();
		JLabel l = new JLabel(label1);
		panel.add(l);
		Dimension d = l.getPreferredSize();
		int hGap = len1 -  d.width;
		if (d.width>hGap) hGap = d.width;
		panel.add(Box.createHorizontalStrut(hGap));
			
		l = new JLabel(label2);
		panel.add(l);
		if (len2!=0) {
			d = l.getPreferredSize();
			hGap = len2 -  d.width;
			if (d.width>hGap) hGap = d.width;
		}
		else hGap=5;
		panel.add(Box.createHorizontalStrut(hGap));
		
		panel.add(txtField);
		panel.add(Box.createHorizontalStrut(5));
		
		panel.add(new JLabel(label3));
		panel.add(Box.createHorizontalStrut(5));
		panel.add(txtField2);
		return panel;
	}
	// Label1:  Label2 [   ]  -- using len1 spacing
		static public JPanel panelTextLine (String label1, int len1, String label2, int len2, JTextField txtField) {
			JPanel panel = CreateJ.panelLine();
			JLabel l = new JLabel(label1);
			panel.add(l);
			Dimension d = l.getPreferredSize();
			int hGap = len1 -  d.width;
			if (hGap < 0) hGap = d.width;
			
			panel.add(Box.createHorizontalStrut(hGap));	
			l = new JLabel(label2);
			panel.add(l);
			
			if (len2!=0) {
				d = l.getPreferredSize();
				hGap = len2 -  d.width;
				if (d.width>hGap) hGap = d.width;
			}
			else hGap=5;
			panel.add(Box.createHorizontalStrut(hGap));
			
			panel.add(txtField);
			panel.add(Box.createHorizontalStrut(5));
			
			return panel;
		}
		
	static public void addPanelTextLine (JPanel panel, int len, String label, JTextField txtField) {
		JLabel l = new JLabel(label);
		panel.add(l);
		
		if (len>0) {
			Dimension d = l.getPreferredSize();
			int hGap = len -  d.width;
			if (hGap < 0) hGap = d.width;
			panel.add(Box.createHorizontalStrut(hGap));
		}
		else panel.add(Box.createHorizontalStrut(5));
		
		panel.add(txtField);
		panel.add(Box.createHorizontalStrut(5));
	}
	
	/** Buttons **/
	static public JButton button(String label) {
		JButton retVal = new JButton(label);
		retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
		retVal.setBackground(Globals.COLOR_BG);
		retVal.setMaximumSize(retVal.getPreferredSize());
		retVal.setPreferredSize(retVal.getPreferredSize());
		return retVal;
	}
	static public JButton button(String label, Color color) {
		JButton retVal = new JButton(label);
		retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
		retVal.setBackground(color);
		retVal.setMaximumSize(retVal.getPreferredSize());
		retVal.setPreferredSize(retVal.getPreferredSize());
		return retVal;
	}
	// for function, no color makes it blue
	static public JButton buttonFun(String label) {
		JButton retVal = new JButton(label);
		retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
		retVal.setMaximumSize(retVal.getPreferredSize());
		retVal.setPreferredSize(retVal.getPreferredSize());
		return retVal;
	}
	// these two are just so all Column panels use the same labels
	static public JButton buttonClear() {
		JButton retVal = new JButton("Clear All Columns");
		retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
		retVal.setBackground(Globals.COLOR_COLUMNS);
		retVal.setMaximumSize(retVal.getPreferredSize());
		retVal.setPreferredSize(retVal.getPreferredSize());
		return retVal;
	}
	static public JButton buttonApply() {
		JButton retVal = new JButton("Apply Selection");
		retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
		retVal.setBackground(Globals.COLOR_COLUMNS);
		retVal.setMaximumSize(retVal.getPreferredSize());
		retVal.setPreferredSize(retVal.getPreferredSize());
		return retVal;
	}
	/** JTextField **/
	static public JTextField textField(CaretListener enableListener, CaretListener typeListener) {
		JTextField retVal = new JTextField(4);
		if (enableListener!=null) retVal.addCaretListener(enableListener);
		if (typeListener!=null) retVal.addCaretListener(typeListener);
		retVal.setMaximumSize(retVal.getPreferredSize());
		return retVal;
	}
	static public JTextField textField(CaretListener enableListener, CaretListener typeListener, boolean disable) {
		JTextField retVal = new JTextField(4);
		if (disable) retVal.setEnabled(false);
		else {
			if (enableListener!=null) retVal.addCaretListener(enableListener);
			if (typeListener!=null) retVal.addCaretListener(typeListener);
		}
		retVal.setMaximumSize(retVal.getPreferredSize());
		return retVal;
	}
	static public JTextField textField(int size, CaretListener enableListener, CaretListener typeListener) {
		JTextField retVal = new JTextField(size);
		if (enableListener!=null) retVal.addCaretListener(enableListener);
		if (typeListener!=null) retVal.addCaretListener(typeListener);
		retVal.setMaximumSize(retVal.getPreferredSize());
		return retVal;
	}
	static public JScrollPane scrollPane(int w, int h) {
		JScrollPane sptbl = new JScrollPane();
		sptbl.setAlignmentX(Component.LEFT_ALIGNMENT);
    	
		sptbl.getViewport().setBackground(Color.WHITE);
		sptbl.getHorizontalScrollBar().setBackground(Color.WHITE);
		sptbl.getVerticalScrollBar().setBackground(Color.WHITE);
		sptbl.getHorizontalScrollBar().setForeground(Color.WHITE);
		sptbl.getVerticalScrollBar().setForeground(Color.WHITE);
		
		sptbl.getViewport().setMaximumSize(new Dimension(w, h)); // 500, 100
		sptbl.getViewport().setPreferredSize(new Dimension(w, h));
		sptbl.getViewport().setMinimumSize(new Dimension(w, h));
		return sptbl;
	}
	/******************************************************************
	 * For the Column panels.
	 */
	static public void addColumnRows(int offset, int end, JPanel page, 
			JCheckBox [] chkColumns, String [] colLabels, boolean [] defaults, 
			ActionListener colSelectChange) {
	
		JPanel row = CreateJ.panelLine();
	  	int rowWidth = 0; 	
	    for(int x=0; x<end; x++, offset++) { 
	    		int newWidth = 0;
	    		int space = 0;

	        	chkColumns[offset] = new JCheckBox(colLabels[offset]);
	        	chkColumns[offset].setBackground(Globals.COLOR_BG);
	        	chkColumns[offset].addActionListener(colSelectChange);
	        	chkColumns[offset].setSelected(defaults[offset]);
	        	if(Globals.COLUMN_SELECT_WIDTH - chkColumns[offset].getPreferredSize().width > 0) {
	        		newWidth = Globals.COLUMN_SELECT_WIDTH;
	        		space = Globals.COLUMN_SELECT_WIDTH - chkColumns[offset].getPreferredSize().width;
	        	}
	        	else {
	        		space = 0;
	        		newWidth = chkColumns[offset].getPreferredSize().width;
	        	}
	        	if(rowWidth + newWidth >= Globals.COLUMN_PANEL_WIDTH) { 		
	        		page.add(row);
	        		row = CreateJ.panelLine();	
	            	rowWidth = 0;
	        	}
	    		row.add(chkColumns[offset]);
	    		row.add(Box.createHorizontalStrut(space));
	    		row.add(Box.createHorizontalStrut(10));
	    		rowWidth += newWidth + 10;
	    	}		
	    	if(row.getComponentCount() > 0) page.add(row);
	}
	static public void addColumnRows(int offset, int end, JPanel page, 
			JCheckBox [] chkColumns, String [] colLabels, boolean [] defaults, 
			ActionListener colSelectChange, Vector <Integer> rowBreaks) {
		
		int colWidth = Globals.COLUMN_SELECT_WIDTH;
		JPanel row = CreateJ.panelLine();
	  	int rowWidth = 0, rb=0;
	  	boolean newRow=false;
	  	
	    for(int x=0; x<end; x++, offset++) 
	    { 	
	    		if (rowBreaks != null && rb < rowBreaks.size() && rowBreaks.get(rb) == x) {
	    			rb++;
	    			newRow=true;
	    		} 
	    		int newWidth = 0;
	    		int space = 0;
	        	chkColumns[offset] = new JCheckBox(colLabels[offset]);
	        	chkColumns[offset].setBackground(Globals.COLOR_BG);
	        	chkColumns[offset].addActionListener(colSelectChange);
	        	chkColumns[offset].setSelected(defaults[offset]);
	        if(Globals.COLUMN_SELECT_WIDTH - chkColumns[offset].getPreferredSize().width > 0) {
	        		newWidth = Globals.COLUMN_SELECT_WIDTH;
	        		space = Globals.COLUMN_SELECT_WIDTH - chkColumns[offset].getPreferredSize().width;
	        	}
	        	else {
	        		space = 0;
	        		newWidth = chkColumns[offset].getPreferredSize().width;
	        	}
	        	if(rowWidth + newWidth >= Globals.COLUMN_PANEL_WIDTH || newRow) { 		
	        		page.add(row);
	        		row = CreateJ.panelLine();	
	            	rowWidth = 0;
	            	newRow=false;
	        	}
	    		row.add(chkColumns[offset]);
	    		row.add(Box.createHorizontalStrut(space));
	    		row.add(Box.createHorizontalStrut(10));
	    		rowWidth += newWidth + 10;
	    	}		
	    	if(row.getComponentCount() > 0) page.add(row);
	}
	/*********************************************************
	 * Used for adding 'Column Group' in TransColumn and GeneColumn
	 * The row panel is already created.
	 */
	static public void addToColumnRow(JPanel row, int offset, JCheckBox [] chkFields, 	
			String [] columns,  ActionListener colSelectChange) {
	
		row.add(Box.createHorizontalStrut(10));
	    for(int x=0; x<columns.length; x++, offset++) {     		
	        	chkFields[offset] = new JCheckBox(columns[x]);
	        	chkFields[offset].setBackground(Globals.COLOR_BG);
	        if (colSelectChange!=null)	
	        		chkFields[offset].addActionListener(colSelectChange);
	        chkFields[offset].setSelected(false);
	        
	    		row.add(chkFields[offset]);
	    		row.add(Box.createHorizontalStrut(5));
	    	}	
		row.add(Box.createHorizontalStrut(10));
	}	
}
