package build;

/*****************************************************
 * Reads Cfg -- called from ConfigFrame to load into interface
 * 		Called by Bmain before executing
 * Checks files
 * Load files into database
 * 
 * All load classes get their needed filenames and directories from here
 * All files and directories are sanity checked before starting
 */
import java.sql.ResultSet;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Vector;

import util.ErrorReport;
import util.Globals;
import util.LogTime;
import database.DBConn;

public class Cfg {
	private final int abbrLen = 6;
	
	// all the files and directories that can be in AW.cfg
	private String gtfFile = 	null; 	// required
	private String ncbiFile = null;		// optional
	private String genomeDir = null;		// optional
	
	private String varDirFile = null; 		// required
	private String varCovDir = null;			// required
	private String varAnnoDirFile = null;	// optional
	private String geneCovDir = null;		// optional
	
	// return to file readers
	private Vector <String> genomeVec=null, varVec=null, varCovVec=null, varAnnoVec=null, geneCovVec=null;
	private boolean isEVP = false;
	private boolean isSnpEFF = false;
	
	private Vector <String> fileVec = new Vector <String> (); 
	private String cond1=null, cond2=null;
	private Vector <String> condVal1 = new Vector <String> ();
	private Vector <String> condVal2 = new Vector <String> ();
	private String remark="";
	
	private CfgFileValidate vd = new CfgFileValidate ();
	
	public Cfg () {}
	
	// for ConfigFrame
	public String getCond1() {return cond1;}
	public String getCond2() {return cond2;}
	public Vector <String> getCondVal1() {return condVal1;}
	public Vector <String> getCondVal2() { return condVal2;}
	public Vector <String> getFileVec() {return fileVec;}
	
	// for file readers
	public String getGTK () {return gtfFile;}
	
	public String getGenomeDir() { return genomeDir;}
	public Vector <String> getGenomeVec() { return genomeVec; }
	public String getNCBI () {return ncbiFile;}
	
	public Vector <String> getVarVec() { return varVec;}
	public String getVarDir() {return varDirFile;}
	
	public Vector <String>  getVarAnnoVec() { return varAnnoVec ;}
	public String getVarAnnoDir() {return varAnnoDirFile;}
	
	public String getVarCovDir() { return varCovDir;}
	public Vector <String> getVarCovVec() { return varCovVec;}
	
	public String getGeneCovDir () {return geneCovDir;}
	public Vector <String> getGeneCovVec() { return geneCovVec;}
	
	public String getRemark() {return remark;}
	
	public boolean hasVarAnno() { return (varAnnoDirFile!=null);}
	public boolean hasExpDir() { return (geneCovDir!=null);}
	public boolean hasNCBIFile() { return (ncbiFile!=null);}
	public boolean hasGenomeDir() { return (genomeDir!=null); }
	public boolean isEVP() { return isEVP;}
	public boolean isSnpEFF() { return isSnpEFF;}
	
	/**********************************************************
	 * XXX read AW.cfg into data structures
	 * > Strain
	 * > Tissue
	 * > Files
	 */
	private boolean prtCfg=false;
	
	public boolean readCfg(String project, boolean prt) {
		try {
			prtCfg = prt;
			String projPath = Globals.projDir + "/" + project + "/" + Globals.cfgFile;
			if (project.endsWith(Globals.cfgFile)) projPath = project;
			
			LogTime.PrtDateMsg("Read " + projPath);
			if (!fileExists(projPath)) return false;
			
			BufferedReader reader = new BufferedReader ( new FileReader ( projPath ) ); 
			int state=0;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("#") || line.equals("")) continue;
				
				// set section
				if (line.startsWith(">")) {
					if (containsString(line, keyCOND1)) {
						cond1 = getCond(line); 
						state=1;
					}
					else if (containsString(line, keyCOND2)) {
						cond2 = getCond(line); 
						state=2;
					}
					else if (containsString(line, keyFILES)) {
						state=3;
					}
					else {
						LogTime.PrtError("Incorrect line: " + line);
						cntErr++;
					}
					if (prtCfg) LogTime.PrtSpMsg(0, line);
					continue;
				}				
				tok = line.split("\\s+");
				if (tok.length<2) {
					System.out.println("Ignore: " + line);
					continue;
				}
				// add info
				if (state==1) addStrain();
				else if (state==2) addTissue();
				else if (state==3) addFileName();
				else {
					LogTime.PrtError("Incorrectly formated file " + line);
					return false;
				}
			}	
			
			if (gtfFile==null) {
				LogTime.PrtError("Must define GTF file in " + Globals.cfgFile);
				cntErr++;
			}
			if (varCovDir==null) {
				LogTime.PrtError("Must define "+ keyVARCOV +" directory in " + Globals.cfgFile);
				cntErr++;
			}
			if (varDirFile==null) {
				LogTime.PrtError("Must define at least one SNP and/or Indel file in " + Globals.cfgFile);
				cntErr++;
			}	
	
			if (cntErr>0) {
				LogTime.PrtSpMsg(0, "Failure loading AW.cfg ");
				return false;
			}
			LogTime.PrtSpMsg(0, "Successful load of AW.cfg");
			return true;
		}
		catch (Exception e) {ErrorReport.prtError(e, "loadCfg");}
		return false;
	}
	/* getCond from AW.cfg */
	private String getCond(String line) {
		if (line.contains("#")) line = line.substring(0, line.indexOf("#"));
		tok = line.split("\\s+");
		if (tok.length==2) return tok[1];
		else if (tok.length==3) return tok[2];
		if (tok.length<2) {
			System.out.println("Provide a condition (e.g. tissue, stage, treatment: " + line);
			LogTime.PrtError("Line should be: > Condition <cond>");
			cntErr++;
		}
		return null;
	}
	/* set file variable */
	private void addFileName() {
		try {
			String key=tok[0].toLowerCase();
			String file = tok[1].trim();
			
			String msg = String.format("%-12s %s", tok[0], file);
			if (prtCfg) LogTime.PrtSpMsg(2, msg);
			
			fileVec.add(key + " " + file);
			
			if (key.equals(keyGTF.toLowerCase())) gtfFile = file;
			else if (key.equals(keyGENOME.toLowerCase())) genomeDir = file;
			else if (key.equals(keyNCBI.toLowerCase())) ncbiFile = file;
			else if (key.equals(keyVARIANT.toLowerCase()))  varDirFile = file;
			else if (key.equals(keyVARANNO.toLowerCase())) varAnnoDirFile = file;
			else if (key.equals(keyVARCOV.toLowerCase())) varCovDir = file;
			else if (key.equals(keyTRANSCNT.toLowerCase())) geneCovDir = file;
			else {
				LogTime.PrtError("Incorrect key " + line);
				cntErr++;
			}
		}
		catch (Exception e) {ErrorReport.prtError(e, "add file" + line);}
	}	
	/* Add Strain */
	private void addStrain() {
		try {	
			if (tok.length<3) {
				LogTime.PrtError("There must be 3 values on line");
				LogTime.PrtSpMsg(0, "Line: " + line);
				cntErr++;
				return;
			}
			String msg = String.format("%s: %-10s %-10s %-10s",cond1, tok[0], tok[1], tok[2]);
			if (prtCfg) LogTime.PrtSpMsg(2, msg);
			if (!tok[2].equalsIgnoreCase("no") && !tok[2].equalsIgnoreCase("yes")) {
				LogTime.PrtError("The 3rd value must be yes or no (indicating whether its a hybrid");
				LogTime.PrtSpMsg(1, "Line: " + line);
				cntErr++;
				return;
			}
			if (tok[0].length() > Globals.shortLen) {
				LogTime.PrtError(tok[0] + " condition1 must be < " + Globals.shortLen);
				cntErr++;
				return;
			}
			if (tok[1].length() > abbrLen) {
				LogTime.PrtError(tok[1] + " abbreviation must be < " + abbrLen);
				cntErr++;
				return;
			}	
			condVal1.add(tok[0] + ":" + tok[1] + ":" + tok[2]);
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Adding strain: " + line);
		}
	}
	/* Add tissue */
	private void addTissue() {
		try {	
			String msg = String.format("%s: %-10s %-10s", cond2, tok[0], tok[1]);
			if (prtCfg) LogTime.PrtSpMsg(2, msg);
			
			if (tok[0].length() > Globals.shortLen) {
				LogTime.PrtError(tok[0] + " condition2 must be < " + Globals.shortLen);
				cntErr++;
				return;
			}
			if (tok[1].length() > abbrLen) {
				LogTime.PrtError(tok[1] + " abbreviation must be < " + abbrLen);
				cntErr++;
				return;
			}		
			condVal2.add(tok[0] + ":" + tok[1]);
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Adding tissue: " + line);
		}
	}	
	private String quote(String word) {
		return "'" + word + "'"; 
	}
	private boolean containsString(String full, String sub)
	{
		full = full.toLowerCase();
		sub = sub.toLowerCase();
		return full.contains(sub);
	}
	
	// file checking
	private boolean fileExists(String file) {
		File f = new File(file);
		if (!f.exists()) 
		{
			LogTime.PrtError(file + " not found"); 
			return false;
		}
		if (f.isDirectory()) {
			LogTime.PrtError(file + " should not be a directory");
			return false;
		}
		return true;
	}
	
	/****************************************************
	 * XXX Validate all input from CFG file
	 */
	public boolean validate() {
		LogTime.PrtSpMsg(1, "Validate abbreviations");
		int cntErr=0, cntWarn=0;
		Vector <String> sAb = new Vector <String> ();
		for (String str1 : condVal1) {
			String [] tok1 = str1.split(":");
			String sA =  tok1[1];
			// tested here and in ConfigFrame.java (in case run from command line
			if (!sA.matches("\\w+") || sA.contains("_"))
			{
				LogTime.PrtError("Condition #1: '" + sA + "'  can only contain letters and digits");
				return false;	
			}
			if (sAb.contains(sA)) {
				LogTime.PrtError("Condition #1: '" + sA + "'  is duplicate");
				return false;	
			}
			sAb.add(sA);
		}
		Vector <String> tAb = new Vector <String> ();
		for (String str1 : condVal2) {
			String [] tok1 = str1.split(":");
			String tA =  tok1[1];
			// tested here and in ConfigFrame.java (in case run from command line
			if (!tA.matches("\\w+") || tA.contains("_"))
			{
				LogTime.PrtError("Condition #2: '" + tA + "'  can only contain letters and digits");
				return false;	
			}
			if (sAb.contains(tA)) {
				LogTime.PrtError("Condition #2: '" + tA + "'  is duplicate");
				return false;	
			}
			tAb.add(tA);
		}
		vd.setCond(sAb, tAb);
		LogTime.PrtSpMsg(1, "Checking files and directories");
		
		// Variants defined
		if (varDirFile!=null) {
			varVec = vd.checkVariant(varDirFile);
			if (varVec==null || varVec.size()==0) cntErr++; 
		} 
		else {
			LogTime.PrtError("Variant call file or directory must exist ");
			cntErr++;
		}
		
		// Variant coverage
		if (varCovDir!=null) { 
			varCovVec = vd.checkVarCovDir(varCovDir);
			if (varCovVec==null || varCovVec.size()==0) cntErr++; 
		}
		else {
			LogTime.PrtError("Variant coverage directory must exist ");
			cntErr++;
		}
		
		//Variant effect (optional)
		if (varAnnoDirFile!=null && !varAnnoDirFile.startsWith("#")) { 
			varAnnoVec = vd.checkVarAnno(varAnnoDirFile);
			if (varAnnoVec==null || varAnnoVec.size()==0) cntWarn++; 
			else {
				isSnpEFF = vd.isSnpEFF;
				isEVP = vd.isEVP;
				remark = (isEVP) ? "EVP" : "snpEFF";
			}
		}
		else LogTime.PrtSpMsg(2, "No variant effect file (optional)");
		
		// Genome annotation file
		if (gtfFile!=null) { 
			if (!vd.checkGTK(gtfFile)) cntErr++; 
		}
		else {
			LogTime.PrtError("Genome annotation file must exist ");
			cntErr++;
		}
		
		// Genome files
		if (genomeDir!=null && !genomeDir.startsWith("#")) {
			genomeVec = vd.checkGenomeDir(genomeDir);
			if (genomeVec==null || genomeVec.size()==0) cntErr++; 
			else remark += (remark.equals("")) ? "AAseqs" : "; AAseqs";
		}
		else {
			LogTime.PrtError("Genome directory of sequence files must exist ");
			cntErr++;
		}
		
		// NCBI (optional)
		if (ncbiFile!=null && !ncbiFile.startsWith("#")) {
			if (vd.checkNCBI(ncbiFile)) 
				remark += (remark.equals("")) ? "NCBI" : "; NCBI";
			else cntWarn++;
		}
		else LogTime.PrtSpMsg(2, "No NCBI functional annotation file (optional)");
		
		// Counts (optional)
		if (geneCovDir!=null && !geneCovDir.startsWith("#")) {
			geneCovVec = vd.checkGeneCovDir(geneCovDir);
			if (geneCovVec!=null && geneCovVec.size()>0)
				remark += (remark.equals("")) ? "Reads" : "; Read";
			else cntWarn++;
		}	
		else LogTime.PrtSpMsg(2, "No count files (optional)");
		
		if (cntErr>0) {
			LogTime.PrtSpMsg(0, "Fatal errors: " + cntErr);
			return false;
		}
		
		cntWarn += vd.getWarn();
		if (cntWarn>0) {
			if (!LogTime.yesNo(cntWarn + " warnings. Continue?")) return false;
		}
		return true;
	}	
	/**************************************************
	 * XXX The AW.cfg has been loaded and the database created
	 * Add data to database
	 */
	public boolean toDBcfg(DBConn mDB) {
		try {
			LogTime.PrtSpMsg(1, "Load library and metadata into database");
			if (condVal2.size()==0) toDBstrainsOnly(mDB);
			else toDBbothCond(mDB);
			if (cntErr>0) return false;
			
			for (String line : fileVec) {
				String [] x = line.split(" ");
				String key = x[0];
				String file = x[1];
				String tag= (x.length>=3) ? x[2] : ""; 
				
				mDB.executeUpdate("Insert files " +
					"set type=" + quote(key) + ",name=" + quote(file) + ", tag=" + quote(tag));
			}
			return true;
		}
		catch (Exception e) {ErrorReport.die(e, "Writing AW.cfg data to database");}
		return false;
	}
	/* add strains only to database */
	private void toDBstrainsOnly(DBConn mDB) {
		try {		
			String st = String.format("Finalizing libraries: strains=" + condVal1.size());
			LogTime.PrtSpMsg(1, st);
			
			String strList="", libList="";
			for (String str1 : condVal1) {
				String [] tok1 = str1.split(":");
				String str = tok1[0];
				String libName =  tok1[1];
				String type = (tok1[2].equals("yes")) ? "hybrid" : "inbred";
				strList += (strList=="") ? (str + ":" + libName) : ("," + str + ":" + libName);
				
				mDB.executeUpdate("INSERT library SET " +
						"libName=" + quote(libName) + "," +
						"strain=" + quote(str) + "," +
						"tissue=''," +
						"remark=" + quote(type));	
				libList += (libList=="") ? libName : ("," + libName);
			}	
			mDB.executeUpdate("UPDATE metaData SET " + 
					"libAbbr=" + quote(libList) +  ",libType=" + quote(cond1) +
					", strains=" + quote(strList) + ", tissues=''");
		}
		catch (Exception e) {
			ErrorReport.die(e, "Adding libraries ");
		}
	}
	// add both conditions to library and metadata (it says Strain and Tissue, but really does not matter)
	private void toDBbothCond(DBConn mDB) {
		try {		
			String strList="", tisList="", libList="";
			int loopCnt=0;
			for (String str1 : condVal1) {
				String [] tok1 = str1.split(":");
				String str = tok1[0];
				String sA =  tok1[1];
				String type = tok1[2];
				strList += (strList=="") ? (str + ":" + sA) : ("," + str + ":" + sA);
				
				for (String str2 : condVal2) {	
					String [] tok2 = str2.split(":");
					String tis = tok2[0];
					String tA = tok2[1];
					String libName = sA + tA;
					
					mDB.executeUpdate("INSERT library SET " +
							"libName=" + quote(libName) + "," +
							"strain=" + quote(str) + "," +
							"tissue=" + quote(tis) + "," +
							"remark=" + quote(type));
					
					libList += (libList=="") ? libName : ("," + libName);
					
					if (loopCnt==0) // goes through loop for each strain, only add for 1st
						tisList += (tisList=="") ? (tis + ":" + tA) : ("," + tis + ":" + tA);
				}
				loopCnt++;
			}
			String cond = cond1;
			if (cond2!=null) cond += "," + cond2;
			mDB.executeUpdate("UPDATE metaData SET " + 
					"libAbbr=" + quote(libList) + ",libType=" + quote(cond) +
					", strains=" + quote(strList) + ", tissues=" + quote(tisList));
		}
		catch (Exception e) {
			ErrorReport.die(e, "Adding libraries ");
		}
	}
	/*****************************************************
	 * load from DB files table -- this is just for doing a single update from Bmain
	 */
	public boolean readDBCfg(DBConn mDB) {
		try {
			LogTime.PrtSpMsg(1, "Read parameters from database");
			ResultSet rs = mDB.executeQuery("SELECT type, name from files");
			while (rs.next()) {
				String key = rs.getString(1);
				String file = rs.getString(2);
				
				if (key.equals("gtf")) gtfFile = file;
				else if (key.equals("genome")) genomeDir = file;
				else if (key.equals("ncbi")) ncbiFile = file;
				else if (key.equals("variant")) varDirFile = file;
				else if (key.equals("varanno")) varAnnoDirFile = file;
				else if (key.equals("mpileupdir")) varCovDir = file;
				else if (key.equals("expdir")) geneCovDir = file;
			}
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Cfg: load files from database");
			return false;
		}
		return true;
	}
	/***************************************************************/	
	private String [] tok;
	private int cntErr=0;
	private String line;
	
	/*********************************************
	 * Hard coded values shared by ConfigFrame.saveCFG and readCFG
	 */
	static public final String keyCOND1 = "Condition1";
	static public final String keyCOND2 = "Condition2";
	static public final String keyFILES = "Files";
	
	static public final String keyGTF = 		"GTF"; // required
	static public final String keyGENOME = 	"Genome"; // optional
	static public final String keyVARIANT = 	"Variants";	// required
	static public final String keyVARANNO = 	"VariantAnno"; // optional
	static public final String keyVARCOV = 	"VariantCov"; // required
	static public final String keyNCBI = 	"NCBI";			// optional
	static public final String keyTRANSCNT = "TransCnt";	// optional
}
