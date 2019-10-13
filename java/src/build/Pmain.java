package build;

import build.panels.ConfigFrame;
import util.Globals;

public class Pmain 
{
	public static void main(String[] args) 
	{	
		System.out.println("\n" + Globals.TITLE);
		ConfigFrame cf = new ConfigFrame();
		cf.setVisible(true);
	}
}
