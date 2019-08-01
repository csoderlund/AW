#!/bin/env perl
use strict;
use warnings;
$| = 1;

my $cmdline = join " ", $0, @ARGV;

use Getopt::Std;

my %opts;
my $ok = getopts('ht:i:o:m:s:', \%opts);
if ((0 == scalar(keys %opts)) or  $ok != 1)
{
	usage();
}
elsif (defined $opts{"h"})
{
	usage(0);
}

my $topDir = `pwd`;
$topDir =~ s/\s+$//;

open C, ">cmdline2.txt" or die "Unable to create cmdline2.txt\n";
print C "$cmdline\n";
close C;

open E, ">Errors.txt" or die "Unable to create Errors.txt file\n";

my $vcfPath;
getParam("i",\$vcfPath,0, "Results");
if (not $vcfPath =~ /^\//)
{
	$vcfPath = "$topDir/$vcfPath"
}
if (not -d $vcfPath)
{
	errorMsg("Can't find directory $vcfPath\n");
	exit(1);
}

my $genoType;
getParam("t",\$genoType,0,"any");
if ($genoType ne "any" and $genoType ne "01" and $genoType ne "11"
		and $genoType ne "10")
{
	errorMsg("Invalid genotype (use 01,11,10,any)");
	exit(1);
}
if ($genoType eq "any")
{
	$genoType = "";
}
else
{
	$genoType =~ /(\d)(\d)/;
	$genoType = $1."/".$2; 
}

my $minFilesCalled;
getParam("m",\$minFilesCalled,0,5);

my $minPctCalled;
getParam("s",\$minPctCalled,0,0);

my $outFile;
getParam("o",\$outFile,0,"combined.vcf");

my $hasReps = (defined $opts{R} ? 1 : 0);

my $inVar = 0;
if (-d "VARIANTS")
{
	chdir "VARIANTS";
	$inVar = 1;
}
################################################
# Load the VCFs, organizing by sample and replicate
my %vcf;
my $line;
foreach my $vcf (<$vcfPath/*.vcf>)
{
	if ( (open F, $vcf) && ($line = <F>))
	{
		if (not $line =~ /VCF/)
		{
			print "$vcf does not look like a VCF file, skipping....\n";
		}
		else
		{
			my $samp = "";
			my $rep = 1;
			if ($hasReps)
			{
				if ($vcf =~ /.*\/(\S+)(\d)\.vcf/)
				{
					$samp = $1;
					$rep = $2;
				}
				else
				{
					print "VCF name $vcf has no replicate number, skipping....\n";
				}
			}			
			else
			{
				
				if ($vcf =~ /.*\/(\S+)\.vcf/)
				{
					$samp = $1;
				}
				else
				{
					print "VCF name $vcf cannot be parsed, skipping....\n";
				}
			}
			if ($samp ne "")
			{
				$vcf{$samp}{$rep} = $vcf;
			}
		}
		close F;
	}	
	else
	{
		print "Unable to open $vcf, skipping....\n";
	}
}
my %sampsnps;
my %sampdepth;
my %repcounts;
my $header = "";
my $numsamps = scalar(keys %vcf);
my $sampThresh = int($minPctCalled*$numsamps/100);
if ($sampThresh > $minFilesCalled )
{
	$minFilesCalled = $sampThresh;
}

print "Minimum files called threshold: $minFilesCalled\n";
print "Processing files....\n";
my %indels;
foreach my $samp (keys %vcf)
{
	foreach my $rep (keys %{$vcf{$samp}})
	{
		$repcounts{$samp}++;
		my $vcf = $vcf{$samp}{$rep};
		if ($header eq "")
		{
			$header = loadHeader($vcf);
		}
		loadSnps($vcf,$samp,\%sampsnps,\%indels,\%sampdepth);
	}	
}
my %snps;
foreach my $tag (keys %sampsnps)
{
	my $sampCount = 0;
	my $repCount = 0;
	foreach my $samp (keys %{$sampsnps{$tag}})
	{
		my $numreps = $sampsnps{$tag}{$samp};
		$repCount += $numreps;
	}
	if ($repCount >= $minFilesCalled)
	{
		my @f = split /\s+/, $tag;
		my $chr = $f[0];
		my $pos = $f[1];
		my $indel = (defined $indels{$tag} ? ";INDEL" : "");
		my $depth = (defined $sampdepth{$tag} ? $sampdepth{$tag} : "0");
		$snps{$chr}{$pos} = "$tag\t.\t.\tDP=$depth;sampCount=$repCount$indel";

	}
}
print "Writing to $outFile\n";
open F, ">$outFile" or die "can't create $outFile\n";
print F $header;
foreach my $chr (sort keys %snps)
{
	foreach my $pos (sort {$a <=> $b} keys %{$snps{$chr}})
	{
		print F $snps{$chr}{$pos}."\n";	
	}
}

close F;

if ($inVar)
{
	chdir("..");
}	
print "Complete variant calling\n\n";

############################################################

sub loadHeader
{
	my $vcf = shift;

	my $out = "";
	open F, $vcf or die "can't open $vcf\n";

	while (my $line = <F>)
	{
		if ($line =~ /^#/)
		{
			if ($line =~ /^#CHROM/)
			{
				$line =~ s/FORMAT.*//;
			}
			$out .= $line;
		}	
		else
		{
			last;
		}			
	}
	close F;

	return $out;	
}

sub loadSnps
{
	my $vcf = shift;
	my $samp = shift;
	my $p = shift;
	my $p2 = shift;
	my $pd = shift;

	print "   Reading $vcf\n";
	open F, $vcf or die "can't open $vcf\n";
	
	while (my $line = <F>)
	{
		next if $line =~ /^#/;	
		my @f = split /\s+/, $line;
		my $info = $f[9];
		if (not defined $info or $info eq "")
		{
			last;
		}
		if ($genoType ne "")
		{
			if (not $info =~ /^$genoType/)
			{
				next;
			}
		}
		my $tag = $f[0]."\t".$f[1]."\t".$f[2]."\t".$f[3]."\t".$f[4];	
		$p->{$tag}{$samp}++;
		my $keys = $f[7];
		if ($keys =~ /DP=(\d+)/)
		{
			my $depth = $1;
			$pd->{$tag} += $depth;
		}
		if (length($f[3]) > 1 or length($f[4]) > 1)
		{
			$p2->{$tag} = 1;
		}	
	}
	close F;
	
}
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

VarComb.pl combines sample+replicate variant files into one consensus variant set. 
Replicate calls (if any) are first grouped into sample calls, using the -r threshold.
Sample calls are then combined into consensus using the -s threshold. 

Replicates, if present, must be identified by a single digit before the file extension, 
e.g. SampleA1.vcf, SampleA2.vcf. 
This should be the result from prior pipeline steps. 

OPTIONS:
    -h  Show this message
    -i  directory of VCF files
    -m	minimum number of sample files containing called variant [5]	
    -t	genotype required [10,11,01,any] [default:any]
    -o  output file  [combined.vcf]

END

if (defined $exit)
{
	exit($exit);
}
exit(1);
}
