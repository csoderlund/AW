package database;

/***
* CHAR: tinytext 1 byte, text 2 byte, mediumtext 2 byte
* max	256 char		65k char		16M char
* 		use VARCHAR for fields that need to be searched
* INT	tinyint 1 byte, smallint 2 byte,  mediumint 3 byte, int 4 byte, bigint 8 byte
* max	256             32k 			      16M				4394M		
* 		float 4 byte,  double 8 byte
*****/
/******************************************************
 * GTF file - see Genes.java
 * 
 * VCF file (from sanger)
 * 0 #CHROM, 1 POS, 2 ID, 3 REF, 4 ALT, 5 QUAL, 6 FILTER, 7 INFO <key>=<data>[,data];
 * the info could be of interest, but we'll wait to get GATK output

 * chr1    4785683 rs221502411     G       A       257.00  PASS    
 * AC1=0;AC=14;AF1=0;AN=36;DP4=248,257,196,184;DP=922;MQ=58;VDB=0.0374;
 * 	AC= allele count in genotypes, for each ALT allele, in the same order as listed
 * CSQ=ENSMUST00000045689:ENSMUSG00000033845:5_prime_UTR_variant,
 * NMD_transcript_variant:Allele,
 * A:Gene,Mrpl15+ENSMUST00000115538:ENSMUSG00000033845:non_coding_exon_variant,
 * nc_transcript_variant:Allele,A:Gene,
 * Mrpl15+ENSMUST00000130201:ENSMUSG00000033845:5_prime_UTR_variant:Allele,
 * A:Gene,Mrpl15+ENSMUST00000132625:ENSMUSG00000033845:non_coding_exon_variant,
 * nc_transcript_variant:Allele,A:Gene,
 * Mrpl15+ENSMUST00000146665:ENSMUSG00000033845:non_coding_exon_variant,
 * nc_transcript_variant:Allele,A:Gene,
 * Mrpl15+ENSMUST00000156816:ENSMUSG00000033845:5_prime_UTR_variant:Allele,
 * A:Gene,Mrpl15  GT:GQ:DP:SP:PL:FI       1/1:99:42:0:255,126,0:1
 */
import util.ErrorReport;
import util.Globals;

public class Schema {	
	// file loaders must make sure not to surpass these; defined in Globals
	//final public int nameLen = 30;
	//final public int shortLen = 128
	//final public int mediumLen = 256; 
	//final public int longLen = 512;

	public Schema (DBConn mDB) { // new database
		try {
			loadSchema(mDB);
			
			String user = System.getProperty("user.name");
			mDB.executeUpdate(
			"insert into metaData (version, state, buildDate, userName, overview, remark) "
					+ " values(" 
					+ quote(Globals.VERSION)	+ ","
					+ quote("start")			+ ","
					+ "NOW()" 				+ "," 
					+ quote(user) 			+ ", " 
					+ quote("START") 		+ "," 
					+ quote(" ") 			+ ")");
		} catch (Exception e) {
			ErrorReport.die(e, "entering schema version");
		}
	}

	public void loadSchema(DBConn mDB) {
		try {			
			mDB.executeUpdate("CREATE TABLE metaData ( " +
				"version  	tinytext, " +
				"state		tinytext," +	
				"buildDate 	date, " +
				"chgDate 	datetime, " +
				"userName 	tinytext, " +
				"remark		tinytext, " + // entered using commandline mysql 
				"ref			tinytext, " + // from AW.cfg
				"alt			tinytext, " + // from AW.cfg
				"libType		tinytext, " + // condition = replaces Tissue in interface 
				"overview 	mediumtext," +  // for viewer
				"strains		text," + // fullname:abbr, fullname:abbr, etc
				"tissues		text," + // fullname:abbr, fullname:abbr, etc
				"libAbbr		text," + 
				"hasNames	tinytext," + // GTF has gene_name and trans_name
				"chrRoot		tinytext"  + // the root for chromosome names, e.g. chr, contig, scaffold
				");");
			
			mDB.executeUpdate("CREATE TABLE files ( " +
				"type		tinytext, " +
				"name		text,"		+
				"tag			tinytext"	+
				");");
			
			mDB.executeUpdate("CREATE TABLE library ( " + 
				"LIBid		bigint NOT NULL PRIMARY KEY AUTO_INCREMENT, " +
				"libName		varchar(30)," +
				"strain		varchar(30)," + 
				"tissue		varchar(30)," + // two conditions are allowed
				"reps		tinyint unsigned default 0," +
				"varRefSize 	bigint unsigned default 0, " +
				"varAltSize 	bigint unsigned default 0, " +
				"varLibSize 	bigint unsigned default 0, " +
				"readRefSize 	bigint unsigned default 0, " +
				"readAltSize 	bigint unsigned default 0, " +
				"readLibSize 	bigint unsigned default 0, " +
				"remark		text, " +
				"index		idx0(libName)," +
				"index 		idx1(LIBid) " +
				");");				

			// though gene and transcript tables are similar, having them separate 
			// allows us to add specific info to them both
			mDB.executeUpdate("CREATE TABLE gene ( " +
				"GENEid 		bigint NOT NULL PRIMARY KEY AUTO_INCREMENT, " +
				"geneName	varchar(30)," + // gene_name col 8 
				"geneIden	varchar(30), "+	// gene_id col 8		ensembl
				"chr			varchar(30)," + // col 0
				"start		bigint default 0," + // minimum coord over all trans
				"end			bigint default 0," + // maximum coord over all trans
				"cntUniqueExons	int default 0, " +
				"strand		varchar(3)," +
				"type		varchar(30), " + // e.g. protein_coding
				"synonyms	text," +			  // from NCBI file
				"descript	varchar(512)," +	  // from NCBI file
				"cntLibAI		smallint unsigned default 0," +
				"cntSNPCov		smallint unsigned default 0, " +
				"cntSNPAI		smallint unsigned default 0, " +
				"cntSNP			smallint unsigned default 0," +
				"cntMissense		smallint unsigned default 0," + 
				"cntIndel		smallint unsigned default 0," +	
				"cntTrans		smallint unsigned default 0," +	
				// compute from SNP/Read files
				"cntLib		tinyint unsigned default 0, " + // SNP
				"cntLib2		tinyint unsigned default 0, " + // Read				
				// dynamic columns for Ref/Alt summed over trans for each lib (columns created in LibList.java)
				"index		idx0(geneName)," +
				"index 		idx1(GENEid), " +
				"index		idx2(chr)" +
				");");

			mDB.executeUpdate("CREATE TABLE trans ( " +
				"TRANSid 		bigint NOT NULL PRIMARY KEY AUTO_INCREMENT, " +
				"GENEid		bigint, " +
				"transName	varchar(30)," + // transcript_name col 8 gene_name with suffix number
				"transIden	varchar(30), "+	// transcript_id col 8 	
				"chr			varchar(30)," + // col 0
				"start		bigint default 0," + // don't make these 4 unsigned so start-startCodon works in mysql
				"end			bigint default 0," + // bigint is plenty big enough
				"startCodon	bigint default 0," + 
				"endCodon	bigint default 0," + 
				"ntLen		int unsigned default 0, " +
				"UTR5		int unsigned default 0, " + // if startCodon=0, uses first exon start
				"UTR3		int unsigned default 0, " + // if endCodon=0, uses last exon end
				"strand		varchar(3)," +	
				"descript	varchar(256)," +	 		// from NCBI file
				"odRmk 		varchar(128)," + 
				"gtfRmk 		varchar(128)," + 
				
				"cntSNPCov		smallint unsigned default 0, " + // number of SNP with cov>=20 for any library
				"cntSNPAI		smallint unsigned default 0, " + // number of SNP with pval<05 for any library
				"cntLibAI		tinyint unsigned default 0, " +  // number of libraries with pval<0.05 for all SNPs 
				"cntSNP			smallint unsigned default 0, " +	
				"cntMissense 	smallint default 0," + 		// signed so can subtract in interface
				"cntCoding		smallint default 0," +
				"cntIDCoding		smallint default 0," +
				"cntDamage		smallint unsigned default 0," + 
				"cntIndel		smallint unsigned default 0," +
				"cntExon			smallint unsigned default 0, " +	
				// compute from SNP and Read count files
				"cntLib		int unsigned default 0, " +  // SNP
				"cntLib2		int unsigned default 0, " +  // Reads
				"totalRead	int unsigned default 0," +
				// read from peptide files
				"refProLen	int unsigned default 0, " +
				"altProLen	int unsigned default 0, " +
				"nProDiff	int default 0, " +   // signed so can subtract in interface
				// computed from read counts
				"rank		tinyint default 0," + // CAS 2/28/14
				// dynamic columns for Ref/Alt summed over all SNP for all reps for all libs (columns created in LibList.java)
				"index idx0(transName), " +
				"index idx1(TRANSid), " +
				"index idx2(GENEid) " +
				");");	
			
			// the start, end, frame are repeated for each transcript the exon is in,
			// but seems like the little added space is better than a third table
			mDB.executeUpdate("CREATE TABLE transExon ( " +
				"EXONid		bigint NOT NULL PRIMARY KEY AUTO_INCREMENT, " +
				"TRANSid		bigint NOT NULL, " +
				"transName	varchar(30), " + 
				"nExon		smallint default 0, " +
				"chr			varchar(30)," +
				"eStart		bigint default 0," + // may be non-coding
				"eEnd		bigint default 0," +
				"cStart		bigint default 0," + // CDS 
				"cEnd		bigint default 0," +
				"frame		tinyint default 0," + // frame is with CDS
				"intron		int default 0," + 	// length
				"cntSNP		smallint default 0," +
				"cntIndel	smallint default 0," +
				"remark		tinytext," +
				// we may want to add dynamic library summed SNP counts
				"index 		idx1(EXONid), " +
				"index		idx2(TRANSid)" +
				");");
			
			mDB.executeUpdate("CREATE TABLE SNP ( " +
				"SNPid 		bigint NOT NULL PRIMARY KEY AUTO_INCREMENT, " +
				"rsID		varchar(30)," +		
				"isdbSNP		tinyint unsigned default 0, " +  // rsID from dbSNP
				"isSNP		tinyint unsigned default 0," +	// vs indel
				"chr			varchar(30)," +					
				"pos			bigint default 0," +			// leave unsigned so can subtract in interface	
				"dist		text," + 
				"qual		double default 0," +
				"ref			text," +		
				"alt			text," +		
				"exonList	tinytext, " +	
				"effectList	text," +	
				"cntCov		int default 0, " +  // sum of all cover
				"cntLibCov	tinyint default 0, " +  // # libraries with Globals.MIN_READs
				"cntLibAI	tinyint default 0, " +
				"isMissense tinyint default 0," +
				"isDamaging	tinyint default 0," + 
				"isCoding	tinyint default 0," +
				"remark		varchar(128), " +
 				// dynamic columns for Ref/Alt summed over all reps for each lib (columns created in LibList.java)
				"index idx0 (chr), " +
				"index idx1 (rsID), " +
				"index idx2 (SNPid) " +
				");");
			
			/*************************************************
			 * Linking tables with extra info
			 */
			mDB.executeUpdate("CREATE TABLE geneLib ( " +
				"LIBid		bigint," +
				"libName		varchar(30)," +
				"repNum		tinyint," +
				"GENEid 		bigint, " +
				"geneName	varchar(30)," +
				"cntSNPCov	smallint default 0," +
				"cntSNPAI	smallint default 0," +
				"refCount	int default 0," +
				"altCount	int default 0," +	
				"pvalue		double default  " + Globals.NO_PVALUE + "," +
				"refCount2	int default 0," +
				"altCount2	int default 0," +
				"totCount2	int default 0," + // there may not be ref:alt for reads if no SNPs
				"pvalue2		double default  " + Globals.NO_PVALUE + "," +
				"primary key (GENEid, LIBid, repNum), " 	+
				"index 		idx0(LIBid), " +
				"index 		idx2(libName,repNum), " +
				"index 		idx1(GENEid) " +
				 ")");
			
			mDB.executeUpdate("CREATE TABLE transLib ( " +
				"LIBid		bigint," +
				"libName		varchar(30)," +
				"repNum		tinyint," +
				"TRANSid 	bigint, " +
				"transName	varchar(30)," +
				"cntSNPCov	smallint default 0," +
				"cntSNPAI	smallint default 0," +
				"refCount	int default 0," +
				"altCount	int default 0," +	
				"pvalue	double default  " + Globals.NO_PVALUE + "," +
				"refCount2	int default 0," +
				"altCount2	int default 0," +	
				"totCount2	int default 0," + 
				"pvalue2		double default  " + Globals.NO_PVALUE + "," +
				"primary key (TRANSid, LIBid, repNum), " 	+
				"index 		idx2(libName,repNum), " 	+
				"index 		idx1(TRANSid) " 	+
				 ")");	
				
			// Counts for each rep for each lib for each SNP, plus summed Rep 0
			mDB.executeUpdate("CREATE TABLE SNPlib ( " +
				"SNPid		bigint," +
				"LIBid		bigint," +		
				"libName		varchar(30)," +
				"repNum		tinyint default 0," +
				"refCount	int default 0," +
				"altCount	int default 0," +
				"pvalue		double default " + Globals.NO_PVALUE + "," +
				"index 		idx0(LIBid), " +
				"index 		idx1(SNPid), " +
				"primary key (SNPid, LIBid, repNum)" +
				 ")");	
			
			mDB.executeUpdate("CREATE TABLE SNPexon ( " +
				"SNPid		bigint," +
				"EXONid		bigint," +
				"index 		idx0(EXONid), " +
				"index 		idx1(SNPid), " +
				"primary key (SNPid, EXONid)" +
				 ")");	
			
			mDB.executeUpdate("CREATE TABLE SNPgene (" +
				"SNPid	bigint not null, " +
				"GENEid bigint not null," +
				"geneName	varchar(30), " + 
				"index idx1(SNPid), " +
				"index idx2(GENEid), " +
				"primary key (SNPid, GENEid)" +
				")");
			
			mDB.executeUpdate("CREATE TABLE SNPtrans (" +
				"SNPid			bigint not null, " +
				"TRANSid 		bigint not null," +
				"transName		varchar(30), " + 
				"nExon			smallint default 0, " + // exon# that contains it
				"locExon			tinytext, " + 			// x,y where x=distance from cStart, y=distance from cEnd
				"effect	 		tinytext," +
				"isCoding		tinyint default 0, " +
				"isMissense		tinyint default 0, " +
				"isDamaging		tinyint default 0, " +
				"dist			int default 0," +
				"cDNApos			int default 0," + // computed in Variants 
				"included		tinyint default 1," +
				"CDSpos			int default 0," + // read or computed in GenTrans
				"AApos			int default 0," + // ditto
				"AAs				tinytext, " +		// ditto A->A
				"codons			tinytext, " +		// ditto codon->codon
				"rscu			tinytext, " +		
				"bioChem			tinytext, " +
				"index idx1(SNPid), " +
				"index idx2(TRANSid), " +
				"primary key (SNPid, TRANSid)" +
				")");	
			
			mDB.executeUpdate("CREATE TABLE sequences ( " +
				"TRANSid bigint not null," +
				"parent tinyint default 0, " + // 1 for ref, 2 for alt
				"seq mediumtext, " +
				"index idx(TRANSid), " +
				"primary key (parent, TRANSid)" +
				")");
		} 
		catch(Exception e) {
			ErrorReport.die(e, "Failed entering schema");
    		} 
	} 

	private String quote(String word) {
		return "'" + word + "'"; 
	}	
}

