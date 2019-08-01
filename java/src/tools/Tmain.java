package tools;

import tools.VCFUtils;

import database.*;
import util.ErrorReport;
import util.LogTime;

/*******************************************
 * These tools were used during development and are now out of date
 * But may be useful with modification so are left in package.
 * Different methods to manipulate files for input into AW
 */
public class Tmain {
	static private int action = -1;  
	// for those tools that need the database.
	static public  String dbName = 	"AW_mus";
	static private String DBhost = "localhost";
	static private String DBuser = "";
	static private String DBpw =   "";

	static public final String dataDir = 	"./demo"; 
	static public final String fileDir = 	dataDir + "/Files/GRCm38.p2";

	static public final String ensmFile = 	dataDir + "/annotation/chr19.gtf";

	static public final String sangerStrain = "BALBcJ";

	static public final String snpFile = 	fileDir 	+ "/exon_snps.vcf";		   		// Sanger exon snps
	static public final String snpNewFile = fileDir		+ "/gatk.calls.final.vcf";		// Well-supported GATK calls
	static public final String snpFullFile = fileDir 	+ "/BALB_SNPS_v38.vcf";		   	// Sanger Bc snps
	static public final String indelFile = 	fileDir		+ "/final.indels.vcf";		  	// Exonic indels not overlapping snps
	static public final String indelFullFile = 	fileDir + "/BALB_INDELS_v38.vcf";		// Sanger Bc indels
	static public final String sangerSNP = fileDir 		+ "/mgp.v3.snps.rsIDdbSNPv137.vcf";	// Sanger full snp file, all strains
	static public final String sangerIndel = fileDir 	+ "/mgp.v3.indels.rsIDdbSNPv137.vcf"; // Sanger full indel file, all strains
	static public final String genomeFile = fileDir 	+ "/grcm38.p2.fa";		// Ref genome
	static public final String altGenome = fileDir 		+ "/Bc.fa";		// Bc genome, to be constructed
	static public final String altAnnot = fileDir 		+ "/Bc.gtf";	// Bc annotation, to be constructed
	static public final String refBed = fileDir 		+ "/B6.bed";	// B6 bed (positions of all snps/indels), to be constructed. 
	static public final String altBed = fileDir 		+ "/Bc.bed";	// Bc bed, to be constructed. 
	static public final String exprDir = dataDir 		+ "/Express/star/dirs";
	static public final String tcwDir = dataDir 		+ "/Express/TCW";
	static public final String mapFile = tcwDir 		+ "/trans.names";
	static public final String ensPep = fileDir			+ "/Mus_musculus.GRCm38.72.pep.all.fa";
	static public final String tcwPep = tcwDir			+ "/mus_pep.fa";
	static public final String maskedGenome = fileDir 		+ "/grcm38.p2.masked.fa";		// snp-masked genome

	static public final String selDir =  dataDir +	"/Files/Selected"; 
	static public final String b6Trans = selDir + "/B6_pep.fa";
	static public final String b6Trans2 = selDir + "/B6_orfs_pep.fa";
	static public final String bcTrans = selDir + "/Bc_orfs_pep.fa";
	static public final String selList = selDir + "/selList.txt";
	static public final String outTrans = selDir + "/hetSeqs.fa";

	
	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("toolsHW <action> [suffix to HW_mus]");
			System.out.println("1 Cull Sanger snps/indels, creating BALB files");
			System.out.println("2 Make Bc Genome");
			System.out.println("3 Make TCW Files");
			System.out.println("4 Make Masked Genome");
			System.out.println("5 Extract Transcripts");
			System.out.println("6 Extract one transcript");
			System.exit(0);
		}
		if (args.length != 0) action = Integer.parseInt(args[0]);
		if (action < 5) {
			if (args.length == 2) dbName += args[1];
			System.out.println(">>> Using database " + dbName);
			openHWdb();
		}
		if (action==0) {

		}
		else if (action==1) {
			LogTime.PrtDateMsg(">>Cull Sanger SNPs/Indels");
			new VCFUtils(mDB,"cull");	
		}
		else if (action==2) {
			LogTime.PrtDateMsg(">>Make Bc Genome");
			new VCFUtils(mDB,"altGenome");	
		}
		else if (action==3) {
			LogTime.PrtDateMsg(">>Make TCW Files");
			new VCFUtils(mDB,"TCW");	
		}
		else if (action==4) {
			LogTime.PrtDateMsg(">>Make Masked Genome");
			new VCFUtils(mDB,"maskGenome");	
		}
		else if (action==5) {
			LogTime.PrtDateMsg(">>Extract Transcripts");
			new FileUtils("extract");	
		}
		else if (action==6) {
			LogTime.PrtDateMsg(">>Extract one transcripts");
			new FileUtils("one");	
		}
	}

	/***************************************************************
	 * Routines specific to tools and shared by multiple tools
	 ***************************************************************/
	static private boolean openHWdb() {		  
		try {			
			boolean doesDBExist = DBConn.checkMysqlDB(DBhost, dbName, DBuser, DBpw);			
			if (!doesDBExist) LogTime.die("Database does not exist: " + dbName);
			
			String dbstr = "jdbc:mysql://" + DBhost + "/" 	+ dbName;
			mDB =  new DBConn(dbstr, DBuser, DBpw);	
		}
		catch (Exception e) {
			ErrorReport.die(e, "Cannot open " + dbName + " database on " + DBhost);
		}
		return false;
	}

	static private DBConn mDB = null;
}
