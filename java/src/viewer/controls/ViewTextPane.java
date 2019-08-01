package viewer.controls;

/************************************************
 * Display html
 * Display table
 * Display info
 */
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.Dimension;
import java.net.URL;

import util.ErrorReport;
import util.LogTime;

public class ViewTextPane {
	
	/** display information **/
	public static void displayInfo(String title, String message, boolean isModal) {
		displayInfoMonoSpace(null, title, message, isModal, false);
	}
	public static void displayInfo(String title, String [] message, boolean isModal) {
		displayInfo(null, title, message, isModal);
	}
	public static void displayInfo(String title, String [] message) {
		displayInfo(null, title, message, false);
	}
	public static void displayInfo(JFrame vFrame, String title, String [] message, 
			boolean isModal) {
		JOptionPane pane = new JOptionPane();
		pane.setMessage(message);
		pane.setMessageType(JOptionPane.PLAIN_MESSAGE);
		JDialog helpDiag = pane.createDialog(vFrame, title);
		helpDiag.setModal(isModal);
		helpDiag.setVisible(true);		
	}
	
	public static void displayInfoMonoSpace(JFrame vFrame, String title, String message, 
			boolean isModal, boolean sizeToParent) {
		JOptionPane pane = new JOptionPane();
		
		JTextArea messageArea = new JTextArea(message);

		JScrollPane sPane = new JScrollPane(messageArea); 
		messageArea.setFont(new Font("monospaced", Font.BOLD, 12));
		messageArea.setEditable(false);
		messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		pane.setMessage(sPane);
		pane.setMessageType(JOptionPane.PLAIN_MESSAGE);

		JDialog helpDiag = pane.createDialog(vFrame, title);
		helpDiag.setModal(isModal);
		helpDiag.setResizable(true);
		
		if(sizeToParent && vFrame != null && (helpDiag.getWidth() >= vFrame.getWidth() || helpDiag.getHeight() >= vFrame.getHeight()))
				helpDiag.setSize(vFrame.getSize());
		helpDiag.setVisible(true);		
	}
	
	/*****************************************************
	 * Display tables such as the blast tabular file
	 */
	public static void displayInfoTable(JFrame vFrame, String title, final String [] message, 
			boolean isModal, boolean sizeToParent) {
		final class TheModel extends AbstractTableModel {
			private static final long serialVersionUID = 2153498168030234218L;

			public int getColumnCount() {
				if(message == null || message.length == 0)
					return 1;
				return message[0].split("\\s").length; 
			}
			public int getRowCount() { return message.length; }
			public Object getValueAt(int row, int col) {
				if(row < message.length && col < message[row].split("\\s").length)
					return message[row].split("\\s")[col];
				return "";
			}
		}

		JOptionPane pane = new JOptionPane();
		final JButton btnCopySeqID = new JButton("Copy Seq ID");
	
		final JTable messageTable = new JTable();
		messageTable.setModel(new TheModel());
		messageTable.setFont(new Font("monospaced", Font.BOLD, 12));
		messageTable.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				btnCopySeqID.setEnabled(messageTable.getSelectedRow() >= 0);
			}
		});

		btnCopySeqID.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnCopySeqID.setEnabled(false);
		btnCopySeqID.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				int row = messageTable.getSelectedRow();
				
				String rowVal = (String)messageTable.getValueAt(row, 1);
				int strPos = 0;
				if((strPos = rowVal.indexOf('|')) >= 0)
					rowVal = rowVal.substring(strPos+1);
				
				Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
				cb.setContents(new StringSelection(rowVal), null);
			}
		});
		
		JScrollPane sPane = new JScrollPane(messageTable); 
		messageTable.setTableHeader(null);
		messageTable.setShowGrid(false);
		messageTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		messageTable.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		sPane.getViewport().setBackground(Color.WHITE);
		
		//Adjust column sizes
        TableColumn column;
        Component comp;
        int cellWidth;
        
        for (int i = 0;  i < messageTable.getColumnCount();  i++) { // for each column
            column = messageTable.getColumnModel().getColumn(i);
            
            cellWidth = 0;
            for (int j = 0;  j < messageTable.getModel().getRowCount();  j++) { // for each row
	            comp = messageTable.getDefaultRenderer(messageTable.getColumnClass(i)).
	                             getTableCellRendererComponent(
	                            		 messageTable, messageTable.getValueAt(j, i),
	                                 false, false, j, i);

	            if(comp != null) {
		            cellWidth = Math.max(cellWidth, comp.getMinimumSize().width);
	            }
            }

            column.setPreferredWidth(cellWidth);
        }
        
		JPanel buttonRow = new JPanel();
		buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.LINE_AXIS));
		
		buttonRow.add(btnCopySeqID);
		
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
		
		mainPanel.add(buttonRow);
		mainPanel.add(Box.createVerticalStrut(15));
		mainPanel.add(sPane);
		
		pane.setMessage(mainPanel);
		pane.setMessageType(JOptionPane.PLAIN_MESSAGE);

		JDialog helpDiag = pane.createDialog(vFrame, title);
		helpDiag.setModal(isModal);
		helpDiag.setResizable(true);
				
		if(sizeToParent && vFrame != null && (helpDiag.getWidth() >= vFrame.getWidth() || helpDiag.getHeight() >= vFrame.getHeight()))
				helpDiag.setSize(vFrame.getSize());
		helpDiag.setVisible(true);			
	}
	
	/*************************************************************
	 * display HTML where url=/html/....
	 */
	public static void displayHTML(Component vFrame, String title, String urlstr) {
		try {
			if (!urlstr.startsWith("/")) urlstr = "/" + urlstr;
			// Weirdly, the getResource will not work if the ViewEditorPane is in this file
			// the -C ./src html needs to be in makefile for this to work
			URL url= ViewTextPane.class.getResource(urlstr);
			if (url==null) {
				LogTime.PrtError("Null URL");
				return;
			}
			
			ViewEditorPane hlpPane = new ViewEditorPane(url);
			hlpPane.setBorder(BorderFactory.createEmptyBorder(15,15,15,15));
			hlpPane.setMaximumSize(hlpPane.getPreferredSize());
			hlpPane.setMinimumSize(hlpPane.getPreferredSize());
			
			JDialog hlpDiag = new JDialog();
			hlpDiag.setLocationRelativeTo(null); // CAS 6/10/14 - centers
			hlpDiag.setBackground(Color.WHITE);
			hlpDiag.setModal(false);
			
			JScrollPane sPane = new JScrollPane(hlpPane);
			sPane.setMinimumSize(hlpPane.getPreferredSize());
			hlpDiag.add(sPane, BorderLayout.CENTER);
			hlpDiag.pack();
			
			// WN: try to size to content (was opening to full screen)
			Dimension d = hlpPane.getPreferredSize();
			d.width += 100;
			d.height += 100;
			if (d.width>800) d.width=800;
			if (d.height>600) d.height=500;
			hlpDiag.setSize(d);

			hlpDiag.setTitle(title);
			hlpDiag.setVisible(true);
		}
		catch(Exception e) {
			ErrorReport.prtError(e, "Error displaying html");
		}
	}
}

