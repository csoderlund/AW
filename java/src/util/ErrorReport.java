package util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;

import javax.swing.JOptionPane;

import util.LogTime; 

/**
 * A set of static methods to report errors to a file for debugging 
 */
public class ErrorReport {
	
	public static String strFileName= Globals.errorFile;	
	private static int nNumErrors = 0;

	// prints debugInfo to stdout and to log file
	public static void prtError(Throwable e, String debugInfo) {
		System.err.println("Error: " + debugInfo);
		System.err.println("See " + strFileName);
		reportError(strFileName, e, debugInfo, false);
	}
	
	public static void reportFatalError(Error e) {
		if(e.getLocalizedMessage().equals("Java heap space"))
			System.out.println("This application has run out of memory. The application must close immediately");
		else
			System.out.println("An unknown fatal error has occured");
		
		reportError(strFileName, e, "", false);
		
		System.exit(-1);
	}
	
	public static void reportError(String fname, Throwable e, String debugInfo, boolean replaceContents) {
		strFileName = fname;
		PrintWriter pWriter = null;
		try {
			if(replaceContents) {
				pWriter = new PrintWriter(new FileWriter(fname));
				nNumErrors = 0;
			}
			else {
				pWriter = new PrintWriter(new FileWriter(fname, true));
			}
		} catch (IOException e1) {
			System.err.println("An error has occurred, however AW was unable to create an error log file");
			System.err.println(debugInfo);
			e.printStackTrace();
			return;
		}
		
		nNumErrors++;
		
		pWriter.println("\n" + LogTime.getDate()); 
		if(debugInfo != null)
			pWriter.println(debugInfo + "\n");
		
		e.printStackTrace(pWriter);
		
		pWriter.close();
	}
	
	/**
	 * Must be called when execution is about to end. If errors have been written the file, the user is prompted
	 * to email the log file to the specified email address
	 */
	public static void notifyUserOnClose(String email)
	{
		if(nNumErrors==0) return;
		
		System.err.println("There were errors during execution. Please email the " + strFileName + 
				" file to " + email + " so that we may correct the problem");
		
		nNumErrors = 0;
	}
	
	public static void die(Throwable e) {
		reportError(strFileName, e, null, false);
		System.exit(-1);
	}
	public static void die(Throwable e, String debugInfo) {
		System.err.println("Fatal Error: " + debugInfo + "  (" + strFileName + ")");
		reportError(strFileName, e, debugInfo, false);
		System.exit(-1);
	}
	public static void die(String debugInfo) {
		System.err.println("Fatal Error: " + debugInfo);
		System.exit(-1);
	}
	
	public static void infoBox(String infoMessage)
    {
        JOptionPane.showMessageDialog(null, infoMessage, "Error: ", JOptionPane.INFORMATION_MESSAGE);
    }
}

