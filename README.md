**Download tar files and demo files:** www.agcol.arizona.edu/software/AW [from this site](https://github.com/csoderlund/AW/releases)

**Documentation:** www.agcol.arizona.edu/software/AW

**Requirements:** Perl for the pipeline. Java and MySQL to build the database and view the results.

**Reference:** C. Soderlund, W. Nelson, and S. Goff. (2014) Allele Workbench: transcriptome pipeline 
	and interactive graphics for allele specific expression. PLoS ONE

**Description:** The Allele Workbench (AW) is for the analysis of allele-specific expression. 
The pipeline has been tested on Linux, and the Java code has been tested on Linux and MacOSX. 

**The package contains:**

Pipeline specific to F1 hybrid (inbred) RNA-seq data.
	Input: RNA-seq from one or more libraries (optional replicas), 
		the genome sequence to align to, the gene annotation file, 
		and an optional VCF variant file.
    Output: The heterozygous SNPs. Optionally, it can also output the VCF file and 
    	transcript heterozygous read counts. 
    	
runAW is a Java interface to build the database.
    Input: The gene annotation file, genome sequence, VCF file and heterozygous SNP file. 
    	Optionally, it can take as input variant effects, NCBI annotation and transcript 
    	heterozygous read counts.
    Compute: Allele Imbalance (AI) is computed for both the heterozygous SNP coverage and 
    	heterozygous transcript read counts. If effects are not loaded, a subset are computed. 
    	The parental spliced transcripts and protein sequences are computed. 
    	Other various supporting information and summary statistics.
    Output: the AW database along with files of the parental spliced transcripts and 
    	protein sequences. 

viewAW is a Java interface to query view the results. 

**Release v1.1 beta:** (1) Improved error catching and trace output. (2) Speedups. (3) Runs with MariaDB v10.4.7.
