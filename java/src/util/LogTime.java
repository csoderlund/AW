package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import javax.swing.JOptionPane;

import util.ErrorReport;
import util.Globals;
import viewer.ViewerFrame;

public class LogTime {

	static PrintWriter logFileObj=null;
	 /************************************************************************
     * Methods for printing to logFile, errFile and stdout
     **************************************************************************/
  
	static public void Print(String s) {
	    System.err.println(s);
	    System.out.flush();
	    if (logFileObj != null) { // not open if called by viewPAVE/Version
	    		logFileObj.println(s);
	        logFileObj.flush();
	    }
	}	
	static public void PrtError(String msg)
	{
	    	String s = "***Error: " + msg;
	    	Print(s);
	}	
	static public void PrtWarn(String msg)
	{
	    	String s = "+++Warning: " + msg;
	    	Print(s);
	}	
	static public void die(String msg)
	{
   		String s = "***Abort execution: " + msg;
		Print(s);
		System.exit(-1);
	}	
	static public long getTime () {
	    return System.currentTimeMillis(); 
	}	
	static public void PrtDateMsg (String msg) { // start section
	     Print("\n" + msg + " " + getDate());
	}		
    static public void PrtDateMsgTime (String msg, long t) 
    {
        Print(msg + " " + getDate() + " Elapse time " + getElapsedTimeStr(t));
    }	
    static public void PrtSpMsgTime (int i, String msg, long t)
    {
    		String sp = "";
    		for (int j=0; j < i; j++) sp += "   ";
    		String x = String.format("%s%-40s   %s", sp, msg, getElapsedTimeStr(t));
        Print(x);
    } 
    static public void PrtSpMsgTimeMem (int i, String msg, long t)
    {
    		String sp = "";
    		for (int j=0; j < i; j++) sp += "   ";
    		long mem = Runtime.getRuntime().totalMemory() -
  			      Runtime.getRuntime().freeMemory();
    		int m = (int) (((double) mem / 1000000.0)+0.5);
       	String x = String.format("%s%-40s   %8s  (%dM)", sp, msg, 
       			getElapsedTimeStr(t),m);
        Print(x);
    } 
    static public void PrtSpMsg (int i, String msg) {
    		String sp = "";
    		for (int j=0; j < i; j++) sp += "   ";
        sp += msg;
        Print(sp);
    }

	public static void createLogFile(String logPath, boolean append) 
	{	   
		// Make sure log directory exists
		File logDir = new File(logPath);
		if (!logDir.exists()) {
			PrtSpMsg(3, "Creating project log directory " + logPath);
			if (!logDir.mkdir()) {
				LogTime.PrtError("Failed to create project log directory '" + 
						logDir.getAbsolutePath() + "'.");
				return;
			}
		}
	    	
		// Create log file
		String logName = logDir + "/" + Globals.logFile;
		File logFile = new File (logName);
		
		// save last 5 logfiles
		if (logFile.exists() && !append) {
			String logN;
			File fileN;
			boolean success;
			
			for (int i=5; i >= 1; i--) {
				logN = logName + "." + Integer.toString(i);
				fileN = new File(logN);
				if (fileN.exists()) {
					logN = logName + "." + Integer.toString(i+1);
					File fileNx = new File (logN);
					success = fileN.renameTo(fileNx);
					if (!success)  PrtWarn("could not move " + 
							fileN.getName() + " to " + fileNx.getName());
				}
			}
			logN = logName + ".1";
			fileN = new File(logN);
			success = logFile.renameTo(fileN);
			if (!success) PrtWarn("could not move " + logFile.getName()
					+ " to " + fileN.getName());	
		}
		
		try {
			if (append) PrtSpMsg(1, "Append log to: " + logFile.getAbsolutePath());
			else PrtSpMsg(1, "Log file: " + logFile.getAbsolutePath());
			FileOutputStream out = new FileOutputStream(logFile.getAbsolutePath(), append);
			logFileObj =  new PrintWriter(out); 
		}
		catch (Exception e)
        {
            System.err.println ("Error writing to " + logFile.getAbsoluteFile());
            ErrorReport.prtError(e, "Opening logfile");
        }
	}

	 static public String getDate ( )
    {
        Date date=new Date();
        SimpleDateFormat sdf=new SimpleDateFormat("dd-MMM-yy HH:mm:ss"); 
        return sdf.format(date);
    }
    
    static public String getDateOnly ( )
    {
        Date date=new Date();
        SimpleDateFormat sdf=new SimpleDateFormat("dd-MMM-yy"); 
        return sdf.format(date);
    }
    
    static public String convertDate(String date) { // yyyy-dd-mm
   		int first = date.indexOf("-");
		int last = date.lastIndexOf("-");
		String yr = date.substring(0, first);
		yr = yr.substring(2); // remove 20
		String mn = date.substring(first+1, last);
		String dy = date.substring(last+1);
        return dy + "-" + getMonth(mn) + "-" + yr;
    }
    static public String getMonth(String mn) {
     	HashMap<String, String> hMap = new HashMap<String, String>();
       	hMap.put("01", "Jan"); hMap.put("02", "Feb"); hMap.put("03", "Mar");
       	hMap.put("04", "Apr"); hMap.put("05", "May"); hMap.put("06", "Jun");
       	hMap.put("07", "Jul"); hMap.put("08", "Aug"); hMap.put("09", "Sep");
       	hMap.put("10", "Oct"); hMap.put("11", "Nov"); hMap.put("12", "Dec");
       	return hMap.get(mn);
    }
    
    static public String longDate(long l) {
    		Date date = new Date(l);
    		return date.toString();
    }
	
	static public String getElapsedTimeStr(long t) 	{
		long et = System.currentTimeMillis() - t;
		return getElapsedTimeStrFromInterval(et);
	}
	static public String getElapsedTimeStrFromInterval(long et) 	{
		et /= 1000F;
		long min = et / 60;
		long sec = et % 60;
		long hr = min / 60;
		min = min % 60;
		long day = hr / 24;
		hr = hr % 24;
		String str = " ";
		if (day > 0) str += day + "d:";
		if (hr > 0 ) str += hr + "h:";
		str += min + "m:" + sec + "s";
		return str;
	}
	public static boolean yesNo(String question)
	{
		BufferedReader inLine = new BufferedReader(new InputStreamReader(System.in));

		System.err.print("?--" + question + " (y/n)? "); 
		try {
			String resp = inLine.readLine();
			if (resp.equalsIgnoreCase("y")) return true;
			return false;
		} catch (Exception e) {
			return false;
		}
	}
	static public int getIntStdin(String question, int min, int max)
	{
		BufferedReader inLine = new BufferedReader(new InputStreamReader(System.in));

		System.out.print("?--" + question + " (y/n)? "); 
		try
		{
			String resp = inLine.readLine();
			int i = Integer.parseInt(resp);
			if (i >= min && i <= max) return i;
			System.out.println("Incorrect integer");
			return -1;
		} 
		catch (Exception e)
		{
			return -1;
		}
	}
	static public String getStrStdin(String question, String name)
	{
		BufferedReader inLine = new BufferedReader(new InputStreamReader(System.in));

		System.out.print(question + " (" + name + "): "); 
		try
		{
			String resp = inLine.readLine();
			if (resp.equals("")) return name;
			else return resp.trim();
		} 
		catch (Exception e)
		{
			return name;
		}
	}
	public static void infoBox(String infoMessage)
    {
        JOptionPane.showMessageDialog(null, infoMessage, " ", JOptionPane.INFORMATION_MESSAGE);
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
}
