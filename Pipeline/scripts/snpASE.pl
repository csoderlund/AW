#!/bin/env perl
use strict;
use warnings;
$| = 1;
use Getopt::Std;

##################   EDITABLE VARIABLES ##########################

my $samPath =  "ext/samtools-0.1.19/samtools";

#################################################################

my $cmdline = join " ", $0, @ARGV;

my %opts;
my $ok = getopts('Ehi:v:q:b:p:t:n', \%opts);
if (0==scalar(keys %opts) or  $ok != 1)
{
	usage();
}
elsif (defined $opts{"h"})
{
	usage(0);
}

my $vcf;
my $bamFile;
getParam("i",\$bamFile,1,"");
getParam("v",\$vcf,1,"");


my $topDir = `pwd`;
$topDir =~ s/\s+$//; # it comes with a newline!
if (not $samPath =~ /^\//)
{
	$samPath = "$topDir/$samPath"
}

my $out = `$samPath 2>&1`;
if (not defined $out or not $out =~ /Usage/m)
{
	errorMsg("Can't run samtools command: $samPath\n");
	exit(1);
}

my $Dir = "SNPCOV";
$topDir =~ s/\s+$//;

if (-e $Dir)
{
	my $choice = "";
	while (not $choice =~ /^[yn]$/)
	{
		print "The directory $Dir already exists and will be overwritten. Continue? (y/n)\n";
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

my $useIndels = (defined $opts{"n"} ? 1 : 0);

open C, ">cmdline.txt" or die "Unable to create cmdline.txt\n";
print C "$cmdline\n";
close C;

open E, ">Errors.txt" or die "Unable to create Errors.txt file\n";

my @bamFiles;
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
}

my %snps;
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
	foreach my $file (<$vcf/*.vcf>)
	{
		next if -d $file;
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
	exit(0);
}
my $indelsFound = 0;
my %indelsSkipped;
my $nISkip = 0;
foreach my $vcf (@vcfs)
{
	if (open F, $vcf)
	{
		#my $indelWarned = 0;
		while (my $line = <F>)
		{
			next if $line =~ /^#/;
			my @f = split /\s+/, $line;
			my $chr = $f[0];
			my $pos = $f[1];
			my $ref = $f[3];
			my $alt = $f[4];
			if (length($ref) > 1 or length($alt) > 1)
			{
			#	if ($indelWarned == 0)
			#	{
			#		errorMsg("Indel variants seen; will be ignored\n");
			#		$indelWarned = 1;
			#	}
			#	next;
				$indelsFound++;
				if ($useIndels == 0)
				{
					$indelsSkipped{$chr}{$pos} = 1;
					$nISkip++;
					next;
				}
				if (length($ref) == 1 and not $alt =~ /,/)
				{
					if (substr($alt, 0, 1) eq $ref)
					{
						my $plus = length($alt) - length($ref);
						my $newalt = "+$plus".substr($alt,1);
						$snps{$chr}{$pos}{ref} = $ref;
						$snps{$chr}{$pos}{alt} = $newalt;
					}
					else
					{
						$indelsSkipped{$chr}{$pos} = 1;
						$nISkip++;
					}
				}
				elsif (length($alt) == 1 and not $ref =~ /,/)
				{
					if (substr($ref, 0, 1) eq $alt)
					{
						my $minus = length($ref) - 1;
						# Note pileup will have n's instead of the actual characters deleted
						my $newref = "-$minus".('N' x $minus);#substr($ref,1);
						$snps{$chr}{$pos}{ref} = $newref;
						$snps{$chr}{$pos}{alt} = $alt;
					}
					else
					{
						$indelsSkipped{$chr}{$pos} = 1;
						$nISkip++;
					}
				}
				else
				{
					$indelsSkipped{$chr}{$pos} = 1;
					$nISkip++;
				}
			}
			else
			{
				# not an indel
				$snps{$chr}{$pos}{ref} = $ref;
				$snps{$chr}{$pos}{alt} = $alt;
			}
		}
	}
	else
	{
		errorMsg("Unable to open variant file: $vcf\n");
		exit(1);
	}
}
foreach my $chr (sort keys %snps)
{
	print scalar(keys %{$snps{$chr}})." snps read for $chr\n";
}
if ($indelsFound > 0)
{
	print "$indelsFound indels read; $nISkip skipped\n";
}
my $qual;
getParam("q",\$qual,0, 10);
my $bqual;
getParam("b",\$bqual,0, 0);
my $Bopt = ($bqual != 0 ? " -Q $bqual  " : " -B "); # if not using BAQ, why compute it

my $threads = 4;
getParam("t",\$threads,0, 4);

if (not -d "Results")
{
	mkdir "Results";
}
foreach my $bamFile (@bamFiles)
{
	my $outFile = $bamFile;
	$outFile =~ s/.*\///;
	$outFile =~ s/\.bam$/\.bed/;
	$outFile = "Results/$outFile";
	if (-f $outFile)
	{
		print "Output file exists; removing $outFile\n";
		unlink $outFile;
	}
}
if (-f "Summary.html")
{
	unlink "Summary.html";
}
if (open F, ">Summary.html")
{
	print F "<html>\n<body>\n<table rules='all' cellpadding=3>\n<tr><td>Sample</td><td>Total Ref Count</td> <td>Total Alt Count</td><td>Ref%</td></tr>\n";
	close F;
}
else
{
	errorMsg("Unable to create Summary.html\n");
}
# Fork off the specified threads.
# Each one will work through the same list, skipping 
# bams for which the output already exists. 

if (scalar(@bamFiles) < $threads)
{
	$threads = scalar(@bamFiles);
}
# all the work is done in worker threads, so the parent can wait to append
# the final text onto the Summary.html report.
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

	if (open F, ">>Summary.html")
	{
		print F "</table>\n</body>\n</html>\n";
		close F;
	}
	print "SNP ASE completed.\nOutput is in $Dir/Results.\nSummary stats:$Dir/Summary.html\n";
	print "To view summary, use File|Open in a web browser, or open from the command line e.g.\n";
	print "firefox $Dir/Summary.html\n";
	exit(0);
}


foreach my $bamFile (@bamFiles)
{
	my $outFile = $bamFile;
	$outFile =~ s/.*\///;
	$outFile =~ s/\.bam$/\.bed/;
	$outFile = "Results/$outFile";
	next if -e $outFile;
	system("touch $outFile");
	if (open O, ">$outFile")
	{
	}	
	else
	{
		errorMsg("Could not open output file $outFile\n");
		exit(1);
	}
	# Note we call it without reference sequence so it won't use the . and , notations
	my $cmd = "$samPath mpileup -q $qual $Bopt -l $vcf $bamFile";
	print "$cmd\n";
	if (open F, "$cmd |")
	{
		my $total_ref = 0;
		my $total_alt = 0;
		while (my $line = <F>)
		{
			my @f = split /\s+/, $line;
			my $chr = $f[0];
			my $pos = $f[1];
			next if defined $indelsSkipped{$chr}{$pos};
			if (not defined $snps{$chr})
			{
				errorMsg("Unknown chromosome $chr in pileup output\n");
				next;
			}
			if (not defined $snps{$chr}{$pos})
			{
				errorMsg("Unknown locus $chr:$pos in pileup output\n");
				next;
			}
			my @pile = split "", lc $f[4];
			my $ref = lc $snps{$chr}{$pos}{ref};
			my $alt = lc $snps{$chr}{$pos}{alt};
			my $refcount = 0;
			my $altcount = 0;
			for (my $i = 0; $i < scalar(@pile); $i++)
			{
				my $char = $pile[$i];
				if ($char eq '$')
				{
					;
				}
				elsif ($char eq "^")
				{
					$i++;
				}
				elsif ($char eq "+")
				{
					# insertion: it needs to match the tag constructed for alt
					$i++;
					my $N = $pile[$i];
if (not $N =~ /^\d+$/)
{
	print "Bad indel in pile\n$line\n";
}	
					my $thisAlt = "+$N";
					for (my $j = 1; $j <= $N; $j++)
					{
						$i++;
						$thisAlt .= $pile[$i];
					}
					if ($thisAlt eq $alt)
					{
						$altcount++;
					}
				}
				elsif ($char eq "-")
				{
					# deletion: it needs to match the tag constructed for ref
					$i++;
					my $N = $pile[$i];
if (not $N =~ /^\d+$/)
{
	print "Bad indel in pile\n$line\n";
}	
					my $thisRef = "-$N";
					for (my $j = 1; $j <= $N; $j++)
					{
						$i++;
						$thisRef .= $pile[$i];
					}
					if ($thisRef eq $ref)
					{
						$refcount++;
					}
				}
				elsif ($char eq $ref)
				{
					$refcount++;
				}
				elsif ($char eq $alt)
				{
					$altcount++;
				}
			}

			$total_ref += $refcount;
			$total_alt += $altcount;
			my $bedpos = $pos - 1;
			if ($refcount+$altcount > 0)
			{
				print O "$chr\t$bedpos\t$bedpos\t$refcount:$altcount:$ref:$alt\n";
			}
		}
		close F;
		if (open F, ">>Summary.html")
		{
			my $ratio = "-";
			if ($total_ref + $total_alt > 0)
			{
				$ratio = 0.1*int(1000*$total_ref/($total_ref + $total_alt));
			}
			my $file = $outFile;
			$file =~ s/.*\///;
			print F "<tr><td>$file</td> <td>$total_ref</td><td>$total_alt</td> <td>$ratio</td></tr>\n";
			close F;
		}
		else
		{
			errorMsg("Unable to update Summary.txt for $outFile\n");
		}
	}	
	else
	{
		errorMsg("Can't run command $cmd\n");
		exit(1);
	}
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

snpASE.pl 

Computes allele-specific SNP coverage counts, i.e., the read counts of 
ref and alt variants aligned over given SNP locations.

OPTIONS:

    -i  BAM file of alignments OR directory of files 
    -v  VCF file describing SNPs (indels are ignored if present)

    -q  [optional] Minimum read mapping quality (MAPQ) [default: 10]
    -b  [optional] Minimum base mapping quality (BAQ) [default: 0]
    -t  [optional] number of process to launch [default: 4]

The script calls samtools mpileup on the given variant loci and parses the 
result to generate a bed-formatted count table named [bam file root].bed
with columns chromosome, position, position, ref:alt

END

if (defined $exit)
{
	exit($exit);
}
exit(1);

}
