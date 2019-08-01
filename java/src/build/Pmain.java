package build;

import build.panels.ConfigFrame;
import util.Globals;
import database.HostCfg;
import build.compute.Stats;

public class Pmain 
{
	static public  String dbSuffix = "mus"; // TODO - make dynamic
	
	public static void main(String[] args) 
	{	
		System.out.println("\n" + Globals.TITLE);
		ConfigFrame cf = new ConfigFrame();
		cf.setVisible(true);
	}
}
