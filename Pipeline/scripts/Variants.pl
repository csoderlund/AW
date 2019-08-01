#!/bin/env perl
use strict;
use warnings;
$| = 1;
use Getopt::Std;


##################   EDITABLE VARIABLES ##########################

my $samPath =  "ext/samtools-0.1.19";
my $bcfPath =  "$samPath/bcftools";
my $vcfPath =  "$samPath/bcftools";

#################################################################

my $cmdline = join " ", $0, @ARGV;

my %opts;
my $ok = getopts('Ehn:m:i:v:d:p:g:q:b:p:t:', \%opts);
if ( $ok != 1)
{
	usage();
}
elsif (defined $opts{"h"})
{
	usage(0);
}

my $topDir = `pwd`;
$topDir =~ s/\s+$//;

my $varcombPath = $0; # path to this script
$varcombPath =~ s/Variants/VarComb/;
if (not $varcombPath =~ /^\//)
{
	$varcombPath = "$topDir/$varcombPath"
}
if (not -f $varcombPath)
{
	errorMsg("Can't find VarComb.pl at $varcombPath");
	exit(1);
}

getParam("S",\$samPath,0, $samPath);
if (not $samPath =~ /^\//)
{
	$samPath = "$topDir/$samPath"
}
if (not $samPath =~ /samtools$/)
{
	$samPath = "$samPath/samtools";
}
my $out = `$samPath 2>&1`;
if (not defined $out or not $out =~ /Usage/m)
{
	errorMsg("Can't run samtools command: $samPath\n");
	exit(1);
}

getParam("B",\$bcfPath,0, $bcfPath);
if (not $bcfPath =~ /^\//)
{
	$bcfPath = "$topDir/$bcfPath"
}
if (not $bcfPath =~ /bcftools$/ or -d $bcfPath)
{
	$bcfPath = "$bcfPath/bcftools";
}
$out = `$bcfPath 2>&1`;
if (not defined $out or not $out =~ /Usage/m)
{
	errorMsg("Can't run bcftools command: $bcfPath\n");
	exit(1);
}

getParam("V",\$vcfPath,0, $vcfPath);
if (not $vcfPath =~ /^\//)
{
	$vcfPath = "$topDir/$vcfPath"
}

getParam("V",\$vcfPath,0, $vcfPath);
if (not $vcfPath =~ /^\//)
{
	$vcfPath = "$topDir/$vcfPath"
}
if (not $vcfPath =~ /vcfutils\.pl$/)
{
	$vcfPath = "$vcfPath/vcfutils\.pl";
}
$out = `$vcfPath 2>&1`;
if (not defined $out or not $out =~ /Usage/m)
{
	errorMsg("Can't run vcfUtils command: $vcfPath\n");
	exit(1);
}

my $genomeDir;
getParam("g",\$genomeDir,1,"");
if (not $genomeDir =~ /^\//)
{
	$genomeDir = "$topDir/$genomeDir"
}
if (not -d $genomeDir)
{
	errorMsg("Can't find genome fasta directory $genomeDir\n");
	exit(1);
}

my $Dir = "VARIANTS";
if (-e $Dir)
{
	my $choice = "";
	while (not $choice =~ /^[yn]$/)
	{
		print "The directory $Dir already exists and will be overwritten. Continue? (y/n) ";
		$choice = <STDIN>;
	}
	chomp($choice);
	if ($choice ne "y")
	{
		exit(0);
	}
	system("rm -Rf $Dir");
}
mkdir $Dir;
chdir $Dir;

open C, ">cmdline.txt" or die "Unable to create cmdline.txt\n";
print C "$cmdline\n";
close C;

open E, ">Errors.txt" or die "Unable to create Errors.txt file\n";

my @bamFiles;
my $bamFile;
getParam("i",\$bamFile,1,"");
if (not $bamFile =~ /^\//)
{
	$bamFile = "$topDir/$bamFile";
}
if (-d $bamFile)
{
	@bamFiles = <$bamFile/*.bam>;
	if (0 == scalar(@bamFiles))
	{
		errorMsg("Bam file directory has no bam files: $bamFile\n");
		exit(1);
	}
	print "Found ".scalar(@bamFiles)." bam files in directory $bamFile\n";
}
else
{
	if (not $bamFile =~ /\.bam$/)
	{
		errorMsg("bam file does not have extension .bam: $bamFile\n");
		exit(1);
	}
	if (-f $bamFile)
	{
		push @bamFiles, $bamFile;
	}
	else
	{
		errorMsg("Cannot find bam file : $bamFile\n");
		exit(1);
	}
	print "Processing one bam file $bamFile\n";
}

my $qual;
getParam("q",\$qual,0, 10);
my $bqual;
getParam("b",\$bqual,0, 0);
my $Bopt = ($bqual != 0 ? " -Q $bqual  " : " -B "); # if not using BAQ, why compute it

my $depth;
getParam("d",\$depth,0, 500);
my $pval;
getParam("p",\$pval,0, 0.05);


my $hasReps = (defined $opts{R} ? 1 : 0);

my $maxMissingReps;
getParam("r",\$maxMissingReps,0,1);

my $minCalled;
getParam("m",\$minCalled,0,5);

my $genoType;
getParam("t",\$genoType,0,"any");
if ($genoType ne "any" and $genoType ne "01" and $genoType ne "11"
		and $genoType ne "10")
{
	errorMsg("Invalid genotype (use 01,11,10,any)");
	exit(1);
}

print "Collecting genome files...\n";
system("touch genome.fasta");
foreach my $gFile (<$genomeDir/*>)
{
	next if not -f $gFile;
	open F, $gFile or die "can't open $gFile\n";
	my $line = <F>;
	if (not $line =~ /\s*>\S+/)
	{
		errorMsg("$gFile is not a fasta file; skipping....");
		close F;
		next;
	}
	close F;
	system("cat $gFile >> genome.fasta");
}

my $cmd = "$samPath faidx genome.fasta";
print "$cmd\n";
system($cmd);

my $threads = 4;
getParam("n",\$threads,0, 4);

if (not -d "Results")
{
	mkdir "Results";
}
foreach my $bamFile (@bamFiles)
{
	my $outFile = $bamFile;
	$outFile =~ s/.*\///;
	$outFile =~ s/\.bam$/\.ase\.bed/;
	$outFile = "Results/$outFile";
	if (-f $outFile)
	{
		print "Output file exists; removing $outFile\n";
		unlink $outFile;
	}
}
# Fork off the specified threads.
# Each one will work through the same list, skipping 
# bams for which the output already exists. 

if (scalar(@bamFiles) < $threads)
{
	$threads = scalar(@bamFiles);
}
# all the work is done in worker threads, so the parent can wait to append
my $pid;
my @pids;
for (my $i = 1; $i <= $threads; $i++)
{
	$pid = fork();
	last if ($pid == 0); # it's the child - go to the work routine. 
	print "Launched thread $pid\n";
	push @pids, $pid;
	sleep(1);
}
if ($pid > 0)
{
	while ($pid = shift @pids)
	{
		print "Waiting on ".(1+scalar(@pids))." processes\n";
		waitpid($pid,0);
	}

	unlink "genome.fasta";
	unlink "genome.fasta.fai";

	$cmd = "perl $varcombPath  -m $minCalled -t $genoType";
	print "$cmd\n";
	system($cmd);

	exit(0);
}

# Work routine, reached if $pid==0

foreach my $bamFile (@bamFiles)
{
	my $outFile = $bamFile;
	$outFile =~ s/.*\///;
	$outFile =~ s/\.bam$/\.vcf/;
	$outFile = "Results/$outFile";
	next if -f $outFile;
	my $cmd = "$samPath mpileup $Bopt -q $qual -uf genome.fasta $bamFile | $bcfPath view  -p $pval -bvcg - ";
	$cmd .= " | $bcfPath view - | $vcfPath varFilter -D$depth > $outFile "; 
	print "$cmd\n";
	system($cmd);
	sleep(5); # for testing with small bam files that go really fast
}
close E;
if (-z "Errors.txt")
{
    unlink "Errors.txt";
}

#########################################################################

sub errorMsg
{
	my $msg = shift;
	my $oldwarn=$SIG{__WARN__};
	$SIG{__WARN__} = sub { };
	print E $msg;
	$SIG{__WARN__} = $oldwarn;
	print STDERR $msg;
}
sub getParam
{
	my $arg = shift;
	my $pvar = shift;
	my $required = shift;
	my $default = shift;

	$$pvar = $default;

	if (not defined $opts{$arg})
	{
		if ($required == 1)
		{
			print STDERR "Missing required parameter: $arg\n\n";
			usage();
		}
	} 
	else
	{
		if ($opts{$arg} eq "")
		{
			print STDERR "Empty argument for parameter: $arg\n\n";
			exit(1);	
		}
		$$pvar = $opts{$arg};
	}
}

sub usage
{
	my $exit = shift;
	print <<END;

Variants.pl computes variants from given alignment files, generating
one VCF per alignment. 
It then calls the helper script VarComb.pl to combine the separate calls
into one consensus set. For details on this process, run VarComb.pl. 

OPTIONS:
    -h  Show this message

----- Per-sample parameters ---------------------------
    -i  one BAM file of alignments OR directory of files
    -g	genome fasta directory
    -d  max depth for SNP call [vcfutils.pl -D: default:500]
    -p  p-value for SNP call [bcftools -p: default:.05]
    -q  Minimum read mapping quality (MAPQ) [default: 10]
    -b  Minimum base mapping quality (BAQ) [default: 0]
    -n  number of threads [default: 4]

----- Combined call parameters ------------------------
    -m  minimum number of samples supporting the call [default:5]
    -t	genotype required [10,11,01,any] [default:any]

----- Tool paths --------------------------------------
    -S  path to samtools dir
    -B  path to bcftools dir
    -U  path to vcfutils.pl 
    -V  path to vcftools dir

END

if (defined $exit)
{
	exit($exit);
}
exit(1);

}
