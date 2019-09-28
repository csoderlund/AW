package util;

import java.awt.Color;

public class Globals {
	static final public String NAME = "AW";
	static final public String VERSION = "v1.1";
	static final public String VDATE = "(9 Sept 2019)";
	static final public String DBprefix = "AW_";
	static final public String TITLE = NAME + " (Allele Workbench) " + VERSION + " " + VDATE;

	static public final int MAX_REPS = 20;
	static public final int MIN_READS = 20; 
	static public final double NO_PVALUE = 2.0;
	static public final double AI_PVALUE = 0.05;
	static public int READ_LEN = 100;  // can be changed by the user in Bmain
	
	static public boolean InDEV = false; //command line for load and view - not used yet 
	static final public String hostFile = "HOSTS.cfg";
	static final public String cfgFile = NAME + ".cfg";
	static final public String projDir = "projects";
	
	// these two variable get set int metaData as its easier to access from everywhere
	// condition is full name  and cond = 1st 6 letters of it
	static public String condition1 = null, cond1 = null;
	static public String condition2 = null, cond2 = null;
	static public boolean toSTD=false; // whether Align and Draw go to stdout; maybe set as command line param
	
	static public double score(int ref, int alt) { // also computed in TableData
		double s = (double)ref/(double)(alt+ref);
		if (s>1.0) s = 1.0;
		return s;
	}
	static public String scoreStr(String ref, String alt) {
		return  ref + "/(" + ref + "+" + alt + ")"; // CAS 1/25/15 extra parentesis
	}
	/*******************************************
	 * for load
	 */
	static final public int nameLen = 30;	
	static final public int shortLen = 128; 
	static final public int mediumLen = 256; 
	static final public int longLen = 512;
	static public final String ovFile = 		"overview";	
	static public final String logFile = 	"log";
	static public final String errorFile = 	"error.log";
	
	/*******************************************
	 * for view
	 */
	public final static String TAB_Q = "Q"; // Counter on tab names from queries
	public final static String TAB_SEL = "for "; // Counter on tab names from selection
	
	public final static int MODE_GENE = 0;
	public final static int MODE_TRANS = 1;
	public final static int MODE_SNP = 2;
	
	public static final int MODE_GENE_REPS = 3;
	public static final int MODE_TRANS_REPS = 4;
	public static final int MODE_SNP_REPS = 5;
	public final static int MODE_LIB = 6;
	public final static int MODE_EXON = 7;
	public final static int MODE_GENE_LIBS = 8;
	public final static int MODE_TRANS_LIBS = 9;
	public final static int MODE_SNP_TRANS = 10;

	public final static String [] MODE_TABLE =
		{"Gene", "Trans", "Variants", "Gene Reps", "Trans Reps", "Variant Reps", 
		"Libs", "Exons",
		"Gene Library", "Trans Library", "Var by Trans"};
	
	// dynamic columns prefixes
	static public final String PRE_REFCNT = "R__"; // ref
	static public final String PRE_ALTCNT = "A__"; // alt
	static public final String SUF_TOTCNT = "2"; // total cnts have this suffix
	static public final String NO_COL = "none";   // metaData table holds dynamics columns, this says there are none
	
	// prefix must be the same for all column headings as it is removed for searching libs
	// prefixes or suffixes may be used in SortTable to determine how to format columns
	static public final int LIB_PREFIX=2;
	static public final String PRE_Sratio = "S:";
	static public final String PRE_Tratio = "R:";
	static public final String PRE_Sscore = "S/";
	static public final String PRE_Tscore = "R/";
	static public final String PRE_Spval = "Sp";
	static public final String PRE_Rpval = "Rp";
	static public final String COL_AAPOS = "AApos";
	public final static String SUF_PVAL = " Pval"; // LibList columns
	public final static String SUF_REP = " Rep";
	public final static String COUNT2 = "Read"; // for refCount2, altCount2 columns and queries
	static public final String PRE_REF = "Ref";
	static public final String PRE_ALT = "Alt";

	// indices into the ColumnSync type boolean array
	public final static int numType=6;
	public final static int SNPRatio=0;
	public final static int SNPScore=1;
	public final static int ReadRatio=2;
	public final static int ReadScore=3;
	public final static int SNPDE=4;
	public final static int ReadDE=5;
	
	// shared AW Table Column names - defined in the tables Columns.java and used elsewhere 
	// plus some mySQL tables and columns -- not sure why I'm doing them
	public final static String START = "Start";
	public final static String END = "End";
	
	public static final String GENE_TABLE = "gene";
	public final static String GENENAME = 	"Gene";
	public final static String GENESQLID = 	"GENEid";
	public static final String GENELIB_TABLE = "geneLib";
	public static final String SNPGENE_TABLE = "SNPgene";
	
	public static final String TRANS_TABLE = "trans";
	public final static String TRANSNAME = 	"Trans";
	public final static String TRANSIDEN = 	"Identifier";
	public final static String TRANSSQLID = 	"TRANSid";
	public static final String TRANSLIB_TABLE = "transLib";
	public static final String TRANSSTARTC = "ATG";
	public static final String TRANSENDC = "Stop";
	public static final String TRANSTRAND = "Strand";
	
	public static final String SNPTRANS_TABLE = "SNPtrans";
	public static final String SNP_TABLE = 	"SNP";
	public final static String SNPNAME = 	"rsID";
	public final static String SNPSQLID = 	"SNPid";
	public static final String SNPLIB_TABLE = "SNPlib";
	
	public static final String LIBRARY_TABLE = "library";
	public final static String LIBNAME=		"Lib Name";
	public final static String LIBSQLID = 	"LIBid";
	public final static String libTabId = "Id";	
	public final static String LIBREF = "SNP ref";
	public final static String LIBALT = "SNP alt";
	
	public static final String EXON_TABLE = "transExon";
	public final static String EXONSQLID = 	"EXONid";
	public final static String EXONN = "Exon";
	
	// add range of colors, i.e. for different type buttons like in TCW
	// starting with COLOR_ makes it easy to type the first part and see the options
	public static final Color COLOR_BG = Color.WHITE;
	public static final Color COLOR_BORDER = Color.BLACK;
	public static final Color COLOR_FUNCTION = new Color(240 ,240, 255); // light purple
	public static final Color COLOR_MENU = new Color(229, 245, 237);	// beige
	public static final Color COLOR_HELP = new Color(245, 213, 234);	// rose
	public static final Color COLOR_PROMPT = new Color(243, 235, 227);	// light beige
	public static final Color COLOR_LAUNCH = new Color(200, 200, 240); // light purple
	public static final Color COLOR_COLUMNS = new Color(240, 240, 255); 
	
	//Widths for column selection
	public static final int COLUMN_SELECT_WIDTH = 90;
	public static final int COLUMN_PANEL_WIDTH = 800;
	public static final int COLUMN_CLEAR_LOC = 500;
	
	static public final String[] ratioLabels = {
		"1k:1", "100:1", "20:1", "9:1", "5:1", "2:1", "1:1-", "1:1+", "1:2", "1:5", "1:9", "1:20", "1:100", "1:1k"};
	// if ref > alt then alt/ref else alt/ref
	static public final double [] ratioFreq = {
		0.001,   0.01,   0.05,   0.111, 0.2, 0.5, 1.0, -1.0, -0.5, -0.2, -0.111, -0.05,  -0.01, -0.001};
	// (ref+1)/(ref_alt+1); 
	static public final String[] ratioLabels2 = {
		"1k:1",   "100:1", "20:1",  "9:1", "5:1", "2:1",   "1:1-", 
		"1:1+",   "1:2",   "1:5",   "1:9", "1:20","1:100", "1:1k"};
	static public final double [] ratioFreq2 = {
		0.999, 	 0.99,	   0.95,	     0.9,	0.83,  0.67,		0.5, 		
		0.5,		 0.33,	   0.2,		0.11,	0.05,  0.01,	0.001};	
}
