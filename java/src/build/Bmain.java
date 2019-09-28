package build;

/*******************************************
 * Creates the database -- loadHW

 * either called directly, or from runAW
 * expects to have AW.cfg file
 */
import build.compute.*;
import build.file.*;
import database.*;
import util.LogTime;
import util.Globals;

public class Bmain {	
	static public  String project = "mus"; // project = dbName without AW_ prefix
	static public boolean DOCLUSTERSNP = true;
	/*************************************
	 * called from buildAW -- AW.cfg must exist
	 */
	public static void main(String[] args) {	
		if (args.length==0 || args[0].startsWith("-")) {
			prtActions(args);
			return;
		}
		int action = 0;
		try {
			Integer.parseInt(args[0]);
			project += args[0];
			Globals.InDEV=true;
		}
		catch (Exception e) {
			project = args[0];
		}
		try {
			if (args.length==2) action = Integer.parseInt(args[1]);
		}
		catch (Exception e) {
			System.err.println("Second arg must be a number"); System.exit(-1);
		}
		boolean prtCfgToScreen = (action<=1) ? true : false;
		buildArgs(action, prtCfgToScreen);
	}
	/*******************************************
	 * called from runAW build.panels.ConfigureFrame after AW.cfg file is written
	 */
	public static void build(String dbName) {
		project = dbName;
		buildArgs(0, false /* do not print CFG */);
	}
	
	private static void prtActions(String [] args) {
		System.out.println("--------------------------------------------------------");
		System.out.println("buildAW <project> [optional action]");
		System.out.println("	   Read projects/<project>/AW.cfg for the parameters.");
		System.out.println("	   The database is " + Globals.DBprefix + "<project>");
		
		System.out.println("\nLoad all (no action)");
		System.out.println("   Load GTK genes and trans (GTK)");
		System.out.println("   Load Variant (VCF)");
		System.out.println("   Compute transcripts and proteins (genome sequence)");
		System.out.println("   Load Variant coverage (BED)");
	
		System.out.println(" Plus the following actions 5-8, which can also be run separately");
		System.out.println("   To run separately, add necessary info to runAW, save, then run buildAW");
		System.out.println("\nAction  Description");
		System.out.println("5   Load Variant effects (Ensembl Variant Predictor or snpEFF)");
		System.out.println("6   Load Gene NCBI descriptions (Genbank)");
		System.out.println("7   Compute AI=allele imbalance");
		System.out.println();
		System.out.println("8   Load Transcript counts (.xprs)");
		System.out.println("    This is typically run separately since the it needs the transcripts,");
		System.out.println("    which are output during the database build,");
		System.out.println("    but the trans counts will load during build if avaiable.");
		System.out.println();
		
		if (DOCLUSTERSNP) {
			System.out.println("N   (where N>20) Compute transcript coverage with read size=N");
			System.out.println("    by default, it uses " + Globals.READ_LEN + " during the addition of variant coverage");
		}
		System.out.println("--------------------------------------------------------");
		
		if (args.length==0) System.exit(0);
		
		System.out.println("\nFor developments:");
		System.out.println(" 1   Load Gene annotation");
		System.out.println(" 2   Load Variants");
		System.out.println(" 3   Compute transcripts and proteins"); 
		System.out.println(" 4   Load Variant Coverage");
		System.out.println(" 9   Compute additional columns");
		System.out.println("10   Overview");
		System.exit(0);
	}
	/****************************************************
	 * Build or update database
	 */
	public static void buildArgs(int action, boolean prtCfg) {
		System.out.println("\n" + Globals.TITLE);		
		System.out.println("Project: " + project);
		
		long ttime = LogTime.getTime();
		String logDir = Globals.projDir + "/" + project + "/log";
		boolean append = (action >1) ? true : false;
		LogTime.createLogFile(logDir, append);
		boolean loadCfg = (action==7 || action==9||action==10) ? false : true;
		Cfg cfg = new Cfg();
		if (loadCfg)
			if (!cfg.readCfg(project, prtCfg)) return;
		
		HostCfg hostCfg = new HostCfg();
		mDB = hostCfg.openDB(project, action); // checks if exists, creates, or use existing
		if (mDB==null) return;
		
		if (loadCfg) {
			if (!cfg.validate()) {
				System.out.println("Terminating");
				return;
			}
			if (action <=1) // only on first read of cfg
				if (!cfg.toDBcfg(mDB)) return;
		}
		
		if (action==0) {
			LogTime.PrtDateMsg("+++Start building entire database+++");
			// Order of these 4 is important. 
			new Genes(mDB, cfg); 		LogTime.PrtSpMsg(0, "--Finish Step 1");
			new Variants(hostCfg.renew(),cfg);		LogTime.PrtSpMsg(0, "--Finish Step 2"); 
			new VarAnno(hostCfg.renew(), cfg);		LogTime.PrtSpMsg(0, "--Finish Step 3"); // may add cDNApos
			new GenTrans(hostCfg.renew(), cfg, project); LogTime.PrtSpMsg(0, "--Finish Step 4"); 	// else add here
			new VarCov(hostCfg.renew(), cfg, 0);		LogTime.PrtSpMsg(0, "--Finish Step 5"); // needed here
			
			new GeneAnno(hostCfg.renew(), cfg);		LogTime.PrtSpMsg(0, "--Finish Step 6");
			if (cfg.hasExpDir()) 
				{new GeneCov(hostCfg.renew(), cfg); LogTime.PrtSpMsg(0, "--Step 6b");} 
			
			new ASE(hostCfg.renew());				LogTime.PrtSpMsg(0, "--Finish Step 7"); 
			new Compute(hostCfg.renew(), 0); 		LogTime.PrtSpMsg(0, "--Finish Step 8");
			new Overview(hostCfg.renew(), logDir);	LogTime.PrtSpMsg(0, "--Finish Step 9"); 
		}
		// these only execute from buildAW (command line)
		else if (action==1) new Genes(mDB, cfg);
		else if (action==2) new Variants(mDB, cfg);	
		else if (action==3) {
			new GenTrans(mDB, cfg, project);
			new Compute(mDB, 1); // missense summary
			new Overview(mDB, logDir); 
		}
		else if (action==4) {
			new VarCov(mDB,cfg, 0); 
			new ASE(mDB);
			new Compute(mDB, 3); // copy ref:alt to trans S: columns
			new Overview(mDB, logDir);
		}
		else if (action==5) {
			new VarAnno(mDB, cfg);	
			new Compute(mDB, 1); // missense summary
			new Overview(mDB, logDir); 
		}
		else if (action==6) new GeneAnno(mDB, cfg);
		
		else if (action==7) new ASE(mDB);	
		else if (action==8) { // add read counts
			new GeneCov(mDB, cfg);
			new ASE(mDB);
			new Compute(mDB, 2); // copy ref:alt to trans R: columns
			new Overview(mDB, logDir); 
		}
		else if (action==9) new Compute(mDB, 0); 
		else if (action==10) new Overview(mDB, logDir);	
		
		else if (action>20) { // update trans/gene SNP coverage counts
			Globals.READ_LEN = action;
			new VarCov(mDB, cfg, 1);	
			new ASE(mDB);
			new Compute(mDB, 3); 	
			new Overview(mDB, logDir); 
		}
		else if (action==12) Stats.prtStats(mDB);
		else if (action==16) {
			new MakeDemo(mDB, cfg, 1); 
			System.exit(0);
		}
		else {
			System.out.println("No such action " + action);
			System.exit(0);
		}
		
		try {
			String remark = cfg.getRemark();
			String read = "Read Len=" + Globals.READ_LEN;
			if (action>20) {
				mDB.executeUpdate("update metaData set chgDate=NOW(), state='" + read + "'"); // state is not being used so...
			}
			else {
				if (remark.equals("")) // didn't read so don't overwrite
					mDB.executeUpdate("update metaData set chgDate=NOW()");
				else  {
					remark = Globals.VERSION + "; " + remark;
					mDB.executeUpdate("update metaData set chgDate=NOW(),  state='" + read + "'" +
							", remark='" + remark + "'");
				}
			}
			mDB.close(); // CASQ 7Sept19
		}
		catch (Exception e) {LogTime.PrtSpMsg(1, "ChgData not added");}
		
		LogTime.PrtDateMsgTime("\n++++++ Complete build AW_" + project, ttime);
	}	
	static private DBConn mDB = null;
}
