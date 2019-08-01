package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;


// Runs a command-line command. Copied from PAVE.
public class RunCmd 
{
	public static int runCommand(String cmd, File dir, boolean showStdOut, boolean showStdErr)
	throws Exception
	{
		String[] args = cmd.split("\\s+");
		return runCommand(args,dir,showStdOut,showStdErr,null);
	
	
	}
	
	public static int runCommand(String cmd, boolean showStdOut, boolean showStdErr)
	throws Exception
	{
		String[] args = cmd.split("\\s+");
		return runCommand(args,null,showStdOut,showStdErr,null);
	
	
	}
	public static int runCommand(String[] args, File dir, boolean showStdOut, boolean showStdErr, File outFile)
	throws Exception
	{

		
		Process p = Runtime.getRuntime().exec(args,null,dir);
		
		BufferedReader stdOut = new BufferedReader(new InputStreamReader(p.getInputStream()));
		
		BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		
		BufferedWriter outWriter =  null;
		if (outFile != null)
		{
			outWriter = new BufferedWriter(new FileWriter(outFile));
		}
		StringBuilder errStr = new StringBuilder();
		while (true)
		{
			if (showStdOut)
			{
				while (stdOut.ready())
					System.out.append((char) stdOut.read());
			}
			else if (outWriter != null)
			{
				while (stdOut.ready())
					outWriter.write(stdOut.readLine() + "\n");
			}
			else
			{
				while (stdOut.ready()) stdOut.readLine();
			}
			if (showStdErr)
			{
				while (stdError.ready())
				{
					errStr.append((char)stdError.read());
					//System.out.append((char) stdError.read());
				}
			}
			else
			{
				while (stdError.ready()) stdError.readLine();
			}
			try
			{
				p.exitValue();
				break;
			} 
			catch (Exception e)
			{
				Thread.sleep(100);
				if (outWriter != null) outWriter.flush();
			}
		}
		if (showStdOut)
		{
			while (stdOut.ready())
				System.out.append((char) stdOut.read());
		}
		else if (outWriter != null)
		{
			while (stdOut.ready())
				outWriter.write(stdOut.readLine() + "\n");
		}
		else
		{
			while (stdOut.ready()) stdOut.readLine();
		}
		if (showStdErr)
		{
			while (stdError.ready())
				System.out.append((char) stdError.read());
		}
		else
		{
			while (stdError.ready()) stdError.readLine();
		}		
		stdOut.close();
		stdError.close();
		if (outWriter != null) 
		{
			outWriter.flush();
			outWriter.close();
		}
	
		//Long time = Utils.intTimerEnd(nThread,cmd);
		//mCmdTimes.put(cmd, time + mCmdTimes.get(cmd));
		int ev = p.exitValue();
		p.getInputStream().close();
		p.getOutputStream().close();
		p.getErrorStream().close();
		p.destroy();
		return ev;
	}
}
