#!/bin/env perl
use strict;
use warnings;
$| = 1;

##################   EDITABLE VARIABLES ##########################

my $bedtoolsPath = "ext/bedtools-2.17.0/bin/bedtools";

#################################################################

my $cmdline = join " ", $0, @ARGV;

use Getopt::Std;
my %opts;
my $ok = getopts('hi:v:sc:p:E', \%opts);
if ( 0==scalar(keys %opts) or  $ok != 1)
{
	usage();
}
elsif (defined $opts{"h"})
{
	usage(0);
}
# required params
my $seqFile;
getParam("i",\$seqFile,1,"");
my $vcf;
getParam("v",\$vcf,1,"");

my $Dir = "GSMASK";
my $topDir = `pwd`;
$topDir =~ s/\s+$//;
print "topdir:$topDir\n";

if (not $bedtoolsPath =~ /^\//)
{
	$bedtoolsPath = "$topDir/$bedtoolsPath";
}
my $out = `$bedtoolsPath 2>&1`;
if (not $out =~ /Usage/i)
{
	errorMsg("Unable to run bedtools command: $bedtoolsPath");
	exit(1);
}

if (not -e $Dir)
{
	mkdir $Dir;
}
else
{
	if (-e $Dir)
	{
		my $choice = "";
		while (not $choice =~ /^[yn]$/)
		{
			print "The directory $Dir already exists and will be overwritten. Continue? (y/n)\n";
			$choice = <STDIN>;
		}
		chomp($choice);
		if ($choice eq  "n")
		{
			exit(0);
		}
		else
		{
			system("rm -Rf $Dir");
			mkdir $Dir;
		}
	}
}
chdir $Dir;


open C, ">cmdline.txt" or die "Unable to create cmdline.txt\n";
print C "$cmdline\n";
close C;

open E, ">Errors.txt" or die "Unable to open error file Errors.txt\n";
if (not -d "Results")
{
	mkdir "Results";
}
else
{
	errorMsg("WARN: Results directory already exists! Data may be overwritten.");
}

if (not $seqFile =~ /^\//)
{
	$seqFile = "$topDir/$seqFile";
}
print "seqFile:$seqFile\n";
my @seqFiles;
if (-d $seqFile)
{
	foreach my $file (<$seqFile/*>)
	{
		next if -d $file;
		if (open F, $file)
		{
			my $line = <F>;
			if ($line =~ /^\s*>\S+/)
			{
				push @seqFiles, $file;
			}
			else
			{
				errorMsg("$file is not a fasta file; skipping");
			}
			close F;
		}
		else
		{
			errorMsg("Can't open $file; skipping");
		}
	}
	if (0 == scalar(@seqFiles))
	{
		print STDERR "Directory $seqFile has no fasta files\n";
		exit(1);
	}
	print "Found ".scalar(@seqFiles)." fasta files in directory $seqFile\n";
}
else
{
	if (-f $seqFile)
	{
		if (open F, $seqFile)
		{
			my $line = <F>;
			if ($line =~ /^\s*>\S+/)
			{
				push @seqFiles, $seqFile;
			}
			else
			{
				errorMsg("$seqFile is not a fasta file");
				exit(1);
			}
			close F;
		}
	}
	else
	{
		print STDERR "Cannot find file : $seqFile\n";
		exit(1);
	}
}

my $maskChar;
getParam("c",\$maskChar,0, "");
if (length($maskChar) > 1)
{
	errorMsg("Masking character can only be one character in length\n");
	exit(1);
}
my $maskParam = ($maskChar eq "" ? "" : " -mc $maskChar ");
my $softParam = (defined $opts{s} ? " -soft " : "");
my $maskIndels = (defined $opts{n} ? 1:0);


# The code was written to allow a directory but the merging was too
# complicated so it only allows one file now.
# Masking only snps (not indels) isn't supported either because that would
# have been done at the merging stage.
if (not $vcf =~ /^\//)
{
	$vcf = "$topDir/$vcf";
}
if (not -f $vcf and not -d $vcf)
{
    errorMsg("Cannot find variant file or directory: $vcf\n");
    exit(1);
}
my @vcfs;
if (-d $vcf)
{
	errorMsg("Please specify a single variant file (not a directory)\n");
	exit(1);
    foreach my $file (<$vcf/*.vcf>)
    {
        push @vcfs, $file;
    }
}
else
{
    push @vcfs, $vcf;
}
if (scalar(@vcfs) == 0)
{
    errorMsg("No VCF files found!\n");
    exit(1);
}
print scalar(@vcfs)." variant files found\n";

my $maskFile;
if (scalar(@vcfs) == 1)
{
	$maskFile =  $vcfs[0];
}
else
{
	# have to merge them etc etc - easier said than done
}
foreach my $seqFile (@seqFiles)
{
	my $outFile = $seqFile;
	$outFile =~ s/.*\///;
	$outFile =~ /\.(\S+)$/;
	my $ext = $1;
	$outFile =~ s/$ext$/vmask.$ext/;
	
	my $cmd = "$bedtoolsPath maskfasta -fo Results/$outFile ".
		" -fi $seqFile -bed $maskFile $softParam $maskParam ";
	print "$cmd\n";
	system($cmd);
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

GSmask.pl 

Masks fasta sequences at coordinates specified in a vcf, bed, or gff file. 
It uses the maskfasta function of bedtools. 

OPTIONS:
    -i  fasta file to mask OR directory of files
    -v  VCF/BED/GFF file giving regions to mask
    -s	soft masking [default: hard]
    -c	mask character [default: N]
END

if (defined $exit)
{
	exit($exit);
}
exit(1);

}
