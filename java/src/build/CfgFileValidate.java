package build;

/****************************************************
 * Validate all file types -- not working yet
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Vector;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.ErrorReport;
import util.LogTime;

public class CfgFileValidate {
	private int cntWarn=0;
	Pattern rootPat = Pattern.compile("(\\D+)(\\d+)");
	
	public CfgFileValidate () {}
	
	public int getWarn() { 
		if (cntWarn>0) LogTime.PrtSpMsg(0, "Warnings: " + cntWarn + " -- continuing");
		return 0;
	}
	/***************************************************
	 * Methods to check files and make sure they are the correct format
	 */
	public boolean checkGTK(String annoPath) {
		if (!fileExists("Genome GTF file" , annoPath)) return false;
		if (!fileIsGTF("Genome GTF file" , annoPath)) return false;
		return true;
	}
	
	public Vector <String> checkGenomeDir(String genPath) {
		if (!dirExists("Genome sequence directory", genPath)) return null;
		LogTime.PrtSpMsg(2, "Genome sequence directory " + genPath);
		Vector <String> genFiles = new Vector <String> ();
		int cntErr=0;
		
		File f = new File(genPath);
		for (File f1 : f.listFiles())
		{
			String name = f1.getName();
			if (f1.isDirectory()) {
				LogTime.PrtSpMsg(3, "Directory '" + name + "' -- ignoring");
				continue;
			}
			if (!name.endsWith(".fa") && !name.endsWith(".fasta") || !f1.isFile()) {
				LogTime.PrtSpMsg(3, "Warning: ignore " + name + " -- must end with .fa or .fasta");
				cntWarn++;
				continue;
			}
			if (!fileIsFASTA("Genome sequence file " + name, f1)) cntErr++;
			else genFiles.add(name);
		}
		if (cntErr>0) return null;
		LogTime.PrtSpMsg(3, "Files found " + genFiles.size());
		return genFiles;
	}
	public boolean checkNCBI(String fileName) {
		if (!fileExists("Genome NCBI file", fileName)) return false;
		try {
			BufferedReader br = new BufferedReader(new FileReader(new File(fileName)));
			boolean hasGood=false;
			String line;
			int cnt=0;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("LOCUS")) {
					hasGood=true;
					break;
				}
				if (cnt>= 100000) break;
				cnt++;
			}
			if (!hasGood) {
				LogTime.PrtSpMsg(2, "Warning: NCBI file contains no LOCUS: " + fileName + " -- ignoring");
				return false;
			}
		}
		catch (Exception e) {ErrorReport.prtError(e, "checking GTK");}
		return true;
	}
	/***************************************************
	 * Variant files
	 */
	public Vector <String> checkVariant(String ford) {
		try {
			int cntErr=0;
			File f = new File(ford);
			if (!f.exists()) {
				LogTime.PrtError("Variant file or directory does not exist " + ford);
				return null;
			}
			Vector <String> varVec = new Vector <String> ();
			if (f.isFile()) {
				if (!fileIsVCF("Variant file " + ford, f)) return null;
				varVec.add(f.getAbsolutePath());
				return varVec;
			}
			LogTime.PrtSpMsg(2, "Variant directory " + ford);
			for (File file : f.listFiles() )
			{
				if (file.isDirectory()) {
					LogTime.PrtSpMsg(3, "Directory '" + file.getName() + "' -- ignoring");
					continue;
				}
				String path = file.getAbsolutePath();
				if (!file.getName().endsWith(".vcf")) {
					LogTime.PrtSpMsg(3, "Warning: " + file.getName() + " --- is not .vcf -- skipping");
					cntWarn++;
					continue;
				}
				if (!fileIsVCF("Variant file " + file, new File(path))) cntErr++;
				else {
					varVec.add(path);
					LogTime.PrtSpMsg(3, file.getName()); 
				}
			}
			if (cntErr>0) return null;
			else if (varVec.size()==0) {
				LogTime.PrtSpMsg(2, "Error: there are no valid .vcf files");
				return null;
			}
			else return varVec;
		}
		catch (Exception e) {ErrorReport.prtError(e, "checking Variant VCF");}
		return null;
	}
	
	public Vector <String> checkVarCovDir(String dirName) {
		if (!dirExists("Variant coverage directory", dirName)) return null;
		LogTime.PrtSpMsg(2, "Variant coverage directory " + dirName);
		Vector <String> varCovVec = dirOfLibs(dirName, ".bed");
		if (varCovVec==null) return null;
		
		try {
			for (int i=0; i<varCovVec.size(); i++) {
				String file = varCovVec.get(i).split(":")[0];
				
				BufferedReader br = new BufferedReader(new FileReader(dirName + "/" + file));
				String line="";
				while(line.equals(""))
					line = br.readLine().trim();
				
				String [] tok = line.split("\t");
				if (tok.length!=4) {
					LogTime.PrtWarn(file + " is incorrect bed, must have 4 columns, has " + tok.length);
					LogTime.PrtSpMsg(1, "First line: " + line);
					varCovVec.remove(i);
				}
			}
			if (varCovVec.size()==0) {
				LogTime.PrtError("No good bed files in this directory ");
				return null;
			}
			return varCovVec;
		}
		catch (Exception e) {ErrorReport.prtError(e, "checking variant count directory"); return null;}
	}
	
	/**
	 * A directory of Ensembl Variant Predictor files or snpEFF effect files
	 */
	public boolean isEVP=false, isSnpEFF=false;
	
	public Vector <String> checkVarAnno(String ford) {
		try {
			int cntErr=0;
			File f = new File(ford);
			if (!f.exists()) {
				LogTime.PrtError("variant file or directory does not exist " + ford);
				return null;
			}
			Vector <String> varAnnoVec = new Vector <String> ();
			// one file
			if (f.isFile()) {
				if (!ford.endsWith(".vcf")) {
					LogTime.PrtSpMsg(3, "Warning: no .vcf suffix " + ford);
					cntWarn++;
				}
				if (!checkVarAnnoFile(ford)) return null;
				varAnnoVec.add(f.getAbsolutePath());
				return varAnnoVec;
			}
			// directory of files
			LogTime.PrtSpMsg(2, "Variant annotation files " + ford);
			for (File file : f.listFiles() )
			{
				if (file.isDirectory()) {
					LogTime.PrtSpMsg(3, "Directory '" + file.getName() + "' -- ignoring");
					continue;
				}
				String path = file.getAbsolutePath();
				if (!file.getName().endsWith(".vcf")) {
					LogTime.PrtSpMsg(3, "Warning: No .vcf suffix - skipping " + file.getName());
					cntWarn++;
					continue;
				}
				if (!checkVarAnnoFile(path)) cntErr++;
				else {
					varAnnoVec.add(path);
					LogTime.PrtSpMsg(3, file.getName());
				}
			}
			if (cntErr>0) return null;
			
			if (!isEVP && !isSnpEFF) {
				LogTime.PrtError("Variant Annotation Directory has no correctly formated files");
				return null;
			}
			if (isEVP && isSnpEFF) {
				LogTime.PrtError("Cannot mix Ensembl Variant Predictor and snpEFF files");
				return null;
			}
			return varAnnoVec;
		}
		catch (Exception e) {LogTime.PrtError("Checking " + ford);}
		return null;
	}
	/// goes with above 
	private boolean checkVarAnnoFile(String file) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(new File(file)));
			boolean hasGood=false;
			String line="";
			while ((line = br.readLine()) != null) {
				if (line.startsWith("## ENSEMBL VARIANT EFFECT PREDICTOR")) {
					isEVP = true;
					hasGood=true;
					break;
				}
				else if (line.startsWith("##SnpEffVersion")) {
					//if (!isVCF) LogTime.PrtSpMsg(3, file + " -- first line is not ##fileformat=VCF...");
					isSnpEFF=true;
					hasGood=true;
					if (!line.contains("3.6b")) {
						LogTime.PrtSpMsg(3, "Warning: " + file + " -- if this file is not created with version >3.6b, it may not work ");
						LogTime.PrtSpMsg(4, line);
						cntWarn++;
					}
					break;
				}
			}
			if (!hasGood) {
				LogTime.PrtSpMsg(3, "Warning: " + file + " -- not a valid .vcf file - skipping ");
				return false;
			}
			return true;
		}
		catch (Exception e) {LogTime.PrtError("Checking " + file);}
		return false;
	}
	/***************************************************************/
	public Vector <String> checkGeneCovDir(String dirName) {
		if (!dirExists("Transcript coverage directory ", dirName)) return null;
		
		LogTime.PrtSpMsg(2, "Transcript coverage directory " + dirName);
		Vector <String> covVec = dirOfLibs(dirName, ".xprs");
		if (covVec==null) return null;
		
		try {
			for (int i=0; i<covVec.size(); i++) {
				String file = covVec.get(i).split(":")[0];
				
				BufferedReader br = new BufferedReader(new FileReader(dirName + "/" +file));
				String line="";
				while(line.equals(""))
					line = br.readLine().trim();
			
				String [] tok = line.split("\t");
				if (tok.length!=15) {
					LogTime.PrtWarn(file + " is incorrect bed, must have 15 columns, but has " + tok.length);
					LogTime.PrtSpMsg(1, "First line: " + line);
					covVec.remove(i);
				}
			}
			if (covVec.size()==0) {
				LogTime.PrtError("No good xprs files in this directory ");
				return null;
			}
			return covVec;
		}
		catch (Exception e) {ErrorReport.prtError(e, "checking transcript count files");return null;}
	}
	/***************************************************************
	 * check directory of files to see if the contain correct library prefixed names
	 */
	private Vector <String> dirOfLibs(String dirName, String suffix) {
		try
		{
			Vector <String> covVec = new Vector <String> ();
			Vector <String> libVec = new Vector <String> ();
			for (String c1: cond1Vec) {
				if (cond2Vec.size()>0) {
					for (String c2: cond2Vec) libVec.add(c1+c2);
				}
				else libVec.add(c1);
			}	
			
			int good=0, bad=0;
			TreeMap <String, Integer> repCnt = new TreeMap <String, Integer> ();
			Vector <String> noRep = new Vector <String> ();
			
			File fp = new File(dirName);
				
			for (File file : fp.listFiles() )
			{
				String fname = file.getName();
				if (file.isDirectory()) {
					LogTime.PrtSpMsg(3, "Directory '" + fname + "' -- ignoring");
					continue;
				}
				if (!fname.endsWith(suffix)) {
					LogTime.PrtSpMsg(3, "Warning: " + fname + " --- is not " + suffix + " -- skipping");
					cntWarn++;
					continue;
				}
				
				String root = fname.substring(0, fname.indexOf("."));
				root = root.replace("_", "");
				root = root.replace("-", "");
				root = root.replace(".", "");
				String repNum = "", libName=root;
				
				// get rep number
				for (int i=root.length()-1; i>0; i--) {
					String sub = root.substring(i);
					try {
						int n = Integer.parseInt(sub);
					}
					catch (Exception e) {break;}
					repNum = sub;
					libName = root.substring(0,i);
				}
				// match condition
				if (!libVec.contains(libName)) {
					LogTime.PrtError("improperly named file " + fname);
					bad++;
					continue;
				}
			
				if (!repNum.equals("")) {
					if (noRep.contains(libName)) { // could end up with two "1"s, which crashes
						LogTime.PrtError("At least one file for lib '" + libName +"' has a replica number but another does not");
						return null;
					}
				}
				else {
					repNum="1"; // just goes into database
					noRep.add(libName);
					if (repCnt.containsKey(libName)) {
						LogTime.PrtError("At least one file for lib '" + libName +"' has a replica number but another does not");
						return null;
					}
				}
				good++;
				if (repCnt.containsKey(libName)) 
					repCnt.put(libName, repCnt.get(libName)+1);
				else repCnt.put(libName, 1);
				
				covVec.add(fname + ":" + libName + ":" + repNum);
			}
			
			if (covVec.size()==0) {
				LogTime.PrtError("There are no valid " + suffix + " files in this directory.");			
				return null;
			}
			
			if (repCnt.size()>0) {
				LogTime.PrtSpMsg(3, "Number of reps per library:");
				String msg="";
				int cnt=0;
				for (String key : repCnt.keySet())
				{
					cnt++;
					String t = key + ":" + repCnt.get(key);
					msg += String.format("%-12s ", t);
					if (cnt==5) {
						LogTime.PrtSpMsg(4, msg);
						cnt=0;
						msg = "";
					}
				}
				if (!msg.equals("")) LogTime.PrtSpMsg(4, msg);
			}
			if (noRep.size()>0) {
				LogTime.PrtSpMsg(3, "Libraries with no reps:");
				String msg="";
				int cnt=0;
				for (String key : noRep)
				{
					cnt++;
					msg += String.format("%-12s ", key);
					if (cnt==5) {
						LogTime.PrtSpMsg(4, msg);
						cnt=0;
						msg = "";
					}
				}
				if (!msg.equals("")) LogTime.PrtSpMsg(4, msg);
			}
			return covVec;
		}
		catch (Exception e) {ErrorReport.prtError(e, "checking directory of " + suffix + " files");}
		return null;
	}
	/***************************************************************/
	private boolean fileIsVCF(String msg, File f) {
		try
		{
			BufferedReader br = new BufferedReader(new FileReader(f));
			while (br.ready())
			{
				String line = br.readLine();
				if (line.startsWith("#")) continue;
				String[] fields = line.split("\\s+");
				if (fields.length >= 8)
				{
					try 
					{
						Integer.parseInt(fields[1]);
						return true;
					}
					catch(Exception e) {}	
				}
				break;
			}
			br.close();
			
			LogTime.PrtError(msg + " -- is not a valid vcf file.");
			LogTime.PrtSpMsg(3, "It should have at least 8 fields on each line, with the second field being an integer (SNP position)");
		}
		catch(Exception e)
		{
			LogTime.PrtError(msg + " --  cannot read");		
		}
		return false;
	}
	// checks the first two nonempty lines
	private boolean fileIsFASTA(String msg, File f)
	{
		try
		{
			BufferedReader br = new BufferedReader(new FileReader(f));
			String line="";
		
			while(line.equals(""))
			{
				line = br.readLine().trim();
			}
			if (line.matches("^>\\w+.*$"))
			{
				line = br.readLine().trim();
				if (line.matches("^\\w+$"))
				{
					return true;
				}
			}
			LogTime.PrtError(msg + " -- is not a fasta file");
		}
		catch(Exception e)
		{
			LogTime.PrtError(msg + " -- cannot read");		
		}
		return false;
	}
	// checks the first nonempty line
	public boolean fileIsGTF(String msg, String file)
	{
		String errmsg=null;
		File f = new File(file);
		try
		{
			BufferedReader br = new BufferedReader(new FileReader(f));
			String line = br.readLine().trim();
			while(line.equals(""))
			{
				line = br.readLine().trim();
			}
			String[] t = line.split("\\t");
			if (t.length < 9)
			{
				errmsg = "GTF file has too fiew columns (" + t.length + ")";
			}
			else
			{
				try
				{
					Integer.parseInt(t[3]);
					Integer.parseInt(t[4]);
					Matcher m = rootPat.matcher(t[0]);
					if (m.find()) chrRoot = m.group(1);
					else {
						while((line = br.readLine())!=null) {
							if (m.find()) {
								chrRoot = m.group(1);
								break;
							}
						}
						if (!chrRoot.equals("")) 
							LogTime.PrtWarn("Could not find root of seqname (e.g. chr, contig)");
					}
					return true;
				}
				catch(Exception e)
				{
					errmsg = "GTF file should have position numbers in positions 4,5";
				}
			}
		}
		catch(Exception e)
		{
			errmsg = "Could not read file";
		}
		LogTime.PrtError(msg + ": " + file);
		LogTime.PrtSpMsg(1, errmsg);
		return false;
	}
	// file checking
	private boolean fileExists(String msg, String file) {
		if (file==null || file.equals("")) {
			LogTime.PrtError(msg + ": null file name"); 
			return false;
		}
		File f = new File(file);
		if (!f.exists()) 
		{
			LogTime.PrtError(msg + ": " + file + " not found"); 
			return false;
		}
		if (f.isDirectory()) {
			LogTime.PrtError(msg + ": " + file + " should not be a directory");
			return false;
		}
		return true;
	}
	private boolean dirExists(String msg, String file) {
		if (file==null || file.equals("")) {
			LogTime.PrtError(msg + ": null file name"); 
			return false;
		}
		File f = new File(file);
		if (!f.exists()) 
		{
			LogTime.PrtError(msg + ": " + file + " not found"); 
			return false;
		}
		if (!f.isDirectory()) {
			LogTime.PrtError(msg + ": " + file + " must be a directory");
			return false;
		}
	
		return true;
	}
	public void setCond(Vector <String> c1, Vector <String> c2) {
		cond1Vec = c1;
		cond2Vec= c2;
	}
	private Vector <String> cond1Vec, cond2Vec;
	private String chrRoot="";
}
