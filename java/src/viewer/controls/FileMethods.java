package viewer.controls;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.File;
import java.io.PrintWriter;
import java.util.Vector;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import util.ErrorReport;
import viewer.ViewerFrame;
import viewer.table.SortTable;

public class FileMethods {

	static public void writeDelimFile(ViewerFrame theParentFrame, SortTable theTable, String delim) {
    	try {
			JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
			
			if(chooser.showSaveDialog(theParentFrame) == JFileChooser.APPROVE_OPTION) {
				if(chooser.getSelectedFile() != null) {
					PrintWriter out = new PrintWriter(new FileWriter(chooser.getSelectedFile()));
					
					for(int x=0; x<theTable.getColumnCount()-1; x++)
						out.print(theTable.getColumnName(x) + delim);
					out.println(theTable.getColumnName(theTable.getColumnCount()-1));
					
					for(int x=0; x<theTable.getRowCount(); x++) {
						for(int y=0; y<theTable.getColumnCount()-1; y++) {
							out.print(theTable.getValueAt(x, y) + delim);
						}
						out.println(theTable.getValueAt(x, theTable.getColumnCount()-1));
						out.flush();
					}
					out.close();
					System.out.println("Wrote file " + chooser.getSelectedFile());
				}
			}
		} catch(Exception e) {
			ErrorReport.prtError(e, "Error saving file");
		} catch(Error e) {
			ErrorReport.reportFatalError(e);
		}
    }
	
	static public void appendFile(ViewerFrame vFrame, String content) {
	    try {
			JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
			
			if(fc.showSaveDialog(vFrame) == JFileChooser.APPROVE_OPTION) {
				if(fc.getSelectedFile() == null) return;
				
				File file = fc.getSelectedFile();
				String fileName = file.getName();
				if (!fileName.endsWith(".txt")) {
					fileName += ".txt";
					file = new File (fileName);
				}
				FileWriter out=null;
				String [] options = {"Overwrite", "Append", "Cancel"};
				int opt=0;
				if (file.exists()) {
					opt = optionDialog(vFrame, "Export", "File exists", options);
				}
				if (opt==0) {
					out = new FileWriter(fileName, false);
					out.write(content+"\n");
					System.out.println("Create " + fileName);
				}
				else if (opt==1) {
					out = new FileWriter(fileName, true);
					out.append(content + "\n");
					System.out.println("Append to " + fileName);
				}
				else System.out.println("Cancel export");
				if (out!=null) out.close();
			}
		} catch(Exception e) {
			ErrorReport.prtError(e, "Error saving file");
	    }
	}
	static public boolean append;
	static public FileWriter appendFile(ViewerFrame vFrame) {
	    try {
			JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
			
			if(fc.showSaveDialog(vFrame) == JFileChooser.APPROVE_OPTION) {
				if(fc.getSelectedFile() == null) return null;
				
				File file = fc.getSelectedFile();
				String fileName = file.getName();
				if (!fileName.endsWith(".txt")) {
					fileName += ".txt";
					file = new File (fileName);
				}
				FileWriter out=null;
				String [] options = {"Overwrite", "Append", "Cancel"};
				int opt=0;
				if (file.exists()) {
					opt = optionDialog(vFrame, "Export", "File exists", options);
				}
				if (opt==0) {
					append=false;
					System.out.println("Create " + fileName);
					out = new FileWriter(fileName, false);
					return out;
				}
				else if (opt==1) {
					append=true;
					out = new FileWriter(fileName, true);
					System.out.println("Append to " + fileName);
					return out;
				}
				else System.out.println("Cancel export");
			}
		} catch(Exception e) {
			ErrorReport.prtError(e, "Error saving file");
	    }
	    return null;
	}
	static public void writeFile(ViewerFrame theParentFrame, String content) {
	    try {
			JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
			
			if(fc.showSaveDialog(theParentFrame) == JFileChooser.APPROVE_OPTION) {
				if(fc.getSelectedFile() == null) return;
				
				File file = fc.getSelectedFile();
				String fileName = file.getName();
				if (!fileName.endsWith(".txt")) {
					fileName += ".txt";
					file = new File (fileName);
				}
				if (file.exists()) System.out.println("Overwriting " + fileName);
				else System.out.println("Create " + fileName);
				FileWriter out = new FileWriter(fileName, false);
				out.write(content+"\n");
				out.close();
			}
		} catch(Exception e) {
			ErrorReport.prtError(e, "Error saving file");
	    }
	}
	static public Vector <String> readFile(ViewerFrame theViewerFrame) {
		try {	
			JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
			
			if(fc.showOpenDialog(theViewerFrame) == JFileChooser.APPROVE_OPTION) {
				if(fc.getSelectedFile() != null) {
					Vector<String> lines = new Vector<String> ();
					BufferedReader input =  new BufferedReader(new FileReader(fc.getSelectedFile()));
					String l = null;
					while((l = input.readLine()) != null) {
						if (l.trim().length()>0) lines.add(l);
					}
					input.close();	
					System.out.println("Read " + lines.size() + " from " + fc.getSelectedFile().getName());
					return lines;
				}
			}
		} catch(Exception e) {
			ErrorReport.prtError(e, "Error read file");
		} catch(Error e) {
			ErrorReport.reportFatalError(e);
		}
		return null;
	}

	static public void readNamesFromFile(ViewerFrame vFrame, JTextField txtField) {
		try {		
			JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
			
			if(chooser.showOpenDialog(vFrame) == JFileChooser.APPROVE_OPTION) {
				if(chooser.getSelectedFile() != null) {
					Vector<String> theNames = new Vector<String> ();
					BufferedReader input =  new BufferedReader(new FileReader(chooser.getSelectedFile()));
					String line = null;
					while((line = input.readLine()) != null) {
						String [] names = line.split("[\\s,]+");
						for(int x=0; x<names.length; x++)
							if(names[x].trim().length() > 0)
								theNames.add(names[x].trim());
					}
					input.close();						
					if(theNames.size() > 0) {
						String newList = theNames.get(0);
						for(int x=1; x<theNames.size(); x++)
							newList += ", " + theNames.get(x);							
						txtField.setText(newList);
					}
				}
			}
		} catch(Exception e) {
			ErrorReport.prtError(e, "Error read file");
		} catch(Error e) {
			ErrorReport.reportFatalError(e);
		}
	}
	static public boolean yesNoDialog(ViewerFrame vframe, String title, String msg) {
		boolean ans=false;
		if(JOptionPane.showConfirmDialog(vframe, msg, title, 
				JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.YES_OPTION)
					ans=true;
		return ans;
	}
	static public int optionDialog(ViewerFrame vframe, String title, String msg, String [] options) {
		int n = JOptionPane.showOptionDialog(vframe, msg, title, 
				0, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
		return n;
		
	}
	static public void msgDialog(ViewerFrame vframe,String title,  String msg) {
		JOptionPane.showMessageDialog(vframe, 
				msg, title, JOptionPane.PLAIN_MESSAGE);
	}
}
