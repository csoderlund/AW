#!/bin/env perl
use strict;
use warnings;
$| = 1;
use Getopt::Std;

##################   EDITABLE VARIABLES ##########################

my $fSuffix = "_R1";
my $rSuffix = "_R2";

my $samPath =  "ext/samtools-0.1.19/samtools";
my $expressPath =  "ext/express-1.5.1/express";
my $starPath =  "ext/STAR_2.3.1t/STAR";

my $starParams = " --alignIntronMax 1 --alignMatesGapMax 300 --outFilterMatchNmin 175 ".
                        " --outFilterMultimapNmax 100 --outFilterMultimapScoreRange 0";
my $starBuildParams = "";
my $expressParams = "  --rf-stranded ";

#################################################################

my $cmdline = join " ", $0, @ARGV;

my $topDir = `pwd`;
$topDir =~ s/\s+$//;
print "topdir:$topDir\n";

my %opts;
my $ok = getopts('ht:r:a:i:pX:A:Y:S:T:E:', \%opts);
if (0==scalar(keys %opts) or $ok != 1)
{
	usage();
}
elsif (defined $opts{"h"})
{
	usage(0);
}


my $fReads = "";
getParam("i",\$fReads,1,"");
if ($fReads eq "")
{
	print("Please specify a read directory\n");
	usage();
}

if (not $samPath =~ /^\//)
{
	$samPath = "$topDir/$samPath"
}
my $out = `$samPath 2>&1`;
if (not defined $out or not $out =~ /Usage/m)
{
	print("Can't run samtools command: $samPath\n");
	exit(1);
}
if (not $starPath =~ /STAR$/)
{
	$starPath = "$starPath/STAR";
}
if (not $starPath =~ /^\//)
{
	$starPath = "$topDir/$starPath";
}

if (not $expressPath =~ /^\//)
{
	$expressPath = "$topDir/$expressPath";
}
$out = `$expressPath 2>&1`;
if (not defined $out or not $out =~ /Usage/)
{
	print "Can't run express command: $expressPath\n";
	exit(1);
}


my $Dir = "EXPRESS";

if (-e $Dir)
{
	my $choice = "";
	while (not $choice =~ /^[yn]$/)
	{
		print "The directory $Dir already exists. Do you want to keep the previous results? (y/n)\n";
		$choice = <STDIN>;
	}
	chomp $choice;
	if ($choice eq "n")
	{
		system("rm -Rf $Dir");
		mkdir $Dir;
	}
}
else
{
	mkdir $Dir;
}
chdir $Dir;

$out = `$starPath 2>&1`;
if (not defined $out or not $out =~ /EXITING/)
{
	print("Can't run STAR command: $starPath\n");
	exit(1);
}

open C, ">cmdline.txt" or die "Unable to create cmdline.txt\n";
print C "$cmdline\n";
close C;

open E, ">Errors.txt" or die "Unable to create Errors.txt\n";
my $paired = (defined $opts{p} ? 1 : 0);

my $nThreads;
getParam("n",\$nThreads,0,4);

my $transFile;
getParam("r",\$transFile,1,"");

my $transFileALT;
getParam("a",\$transFileALT,0,"");
my $hasAlt = ($transFileALT ne "");


mkdir "Results";
mkdir "ResultsTCW";

if ($transFile ne "")
{
	if (not $transFile =~ /^\//)
	{
		$transFile = "$topDir/$transFile";
	}
	if (not -e $transFile)
	{
		errorMsg("Can't find transcript file: $transFile\n");
		exit(1);
	}
}
if ($transFileALT ne "")
{
	if (not $transFileALT =~ /^\//)
	{
		$transFileALT = "$topDir/$transFileALT";
	}
	if (not -e $transFileALT)
	{
		errorMsg("Can't find ALT transcript file: $transFileALT \n");
		exit(1);
	}
	combineTransFiles($transFile,$transFileALT,"transComb.fasta");
	$transFile = "transComb.fasta";
}
buildIndex($transFile);
if (not -f "./Index/SAindex")
{
	errorMsg("Index build did not succeed \n");
	exit(1);
}

if (not $fReads =~ /^\//)
{
	$fReads = "$topDir/$fReads";
}
my @fFiles;
my @rFiles;

checkAddReads($fReads,\@fFiles,$fSuffix);
@fFiles = sort @fFiles;

if ($paired == 1)
{
	checkAddReads($fReads,\@rFiles,$rSuffix);
	@rFiles = sort @rFiles;
	if (scalar(@rFiles) != scalar(@fFiles))
	{
		errorMsg("forward files count (".scalar(@rFiles).") is different from reverse (".scalar(@fFiles).")\n");
		exit(1);
	}
}

my @fileNames;
foreach my $name (@fFiles)
{
	my $root = $name;
	$root =~ s/.*\///;
	push @fileNames, $root;
}
my %stats;
for (my $i = 0; $i < scalar(@fFiles); $i++)
{
	my $f = $fFiles[$i];
	my $r = "";
	if (scalar(@rFiles) > 0)
	{
		$r = $rFiles[$i];
	}
	my $out = makeOutputName($f,$r);
	my $bamFile = "$out.bam";
	my $xprFile = "$out.xprs";
	if (-e "Results/$xprFile")
	{
		print("$xprFile already exists; skipping\n");
		next;
	}
	if (-f "Aligned.out.sam")
	{
		unlink "Aligned.out.sam";
	}	
	my $rfCmd = "";
	if ($f =~ /\.gz$/)
	{
		$rfCmd = "  --readFilesCommand zcat ";
	}
	my $cmd = "$starPath --genomeDir ./Index --runThreadN $nThreads ".
	" --readFilesIn $f $r $rfCmd ".
	" $starParams ";

	print "$cmd\n";
	system($cmd);

	if (not -f "Aligned.out.sam")
	{
		print "STAR alignment failed for $f,$f!\n";
		next;
	}

	if (-f $bamFile) 
	{
		unlink $bamFile;
	}
	$cmd = "$samPath view -hSb Aligned.out.sam > $bamFile";
	print "$cmd\n";
	system($cmd);

	unlink "Aligned.out.sam";

	if (-d "express_tmp")
	{
		system("rm -Rf express_tmp");
	}
	mkdir "express_tmp";

	$cmd = "$expressPath  -o express_tmp $expressParams  $transFile $bamFile ";
	print "$cmd\n";
	system($cmd);
	my $expect = "express_tmp/results.xprs";
	if (not -f $expect or -z $expect)
	{
		errorMsg("Express failed to generate results for $bamFile\n");
	}
	else
	{
		system("cp $expect Results/$xprFile");
		# make the TCW count file, and collect stats for the report
		my $datFile = $xprFile;
		$datFile =~ s/\.xprs/\.dat/;
		buildCountFile("Results/$xprFile","ResultsTCW/$datFile",$out,\%stats);
	}
	unlink $bamFile;
	system("rm -Rf express_tmp");

}
printReport(\%stats);

system("rm -Rf Index");
system("rm -Rf _tmp");
system("rm  Log.*");
system("rm  SJ.*");

close E;
if (-z "Errors.txt")
{
    unlink "Errors.txt";
}
##############################################

sub printReport
{
	my $ps = shift;

	if (-f "Summary.html")
	{
		unlink "Summary.html";
	}
	if (open F, ">Summary.html")
	{
		if ($hasAlt)
		{
			print F "<html>\n<body>\n<table rules='all' cellpadding=3>\n<tr><td>Sample</td><td>Total Ref Count</td> <td>Total Alt Count</td><td>Ref %</td><td>Total Undifferentiated</td><td>Total Count</td></tr>\n";

			foreach my $name (sort keys %$ps)
			{
				my $R = int($ps->{$name}{ref});
				my $A = int($ps->{$name}{alt});
				my $Rpct = .1*int(1000*$R/($R+$A));
				my $B = int($ps->{$name}{both});
				my $T = $A + $B + $R;
				print F "<tr>";
				print F "<td>$name</td>\n";
				print F "<td>$R</td>\n";
				print F "<td>$A</td>\n";
				print F "<td>$Rpct</td>\n";
				print F "<td>$B</td>\n";
				print F "<td>$T</td>\n";
				print F "</tr>\n";
			}
			print F "</table>\n";
		}
		else
		{

			print F "<html>\n<body>\n<table rules='all' cellpadding=3>\n<tr><td>Sample</td><td>Total Count</td></tr>\n";

			foreach my $name (sort keys %$ps)
			{
				my $B = int($ps->{$name}{both});
				print F "<tr>";
				print F "<td>$name</td>\n";
				print F "<td>$B</td>\n";
				print F "</tr>\n";
			}
			print F "</table>\n";
		}
		close F;
	}
	else
	{
		errorMsg("Unable to create Summary.html\n");
	}
}

##########################################################
sub combineTransFiles
{
	my $f1 = shift;
	my $f2 = shift;
	my $f3 = shift;

	open F1, $f1 or die "Can't open transcript file $f1\n";
	open F2, $f2 or die "Can't open transcript file $f2\n";
	open F3, ">$f3" or die "Can't write combined transcript file $f3\n";

	my %seqs;

	my $name;
	while (my $line = <F1>)
	{
		if ($line =~ /^>(\S+)/)
		{
			$name = $1;	
		}
		else
		{
			$line =~ s/\s+//g;
			$seqs{$name}{A} .= $line;
		}
	}
	while (my $line = <F2>)
	{
		if ($line =~ /^>(\S+)/)
		{
			$name = $1;	
		}
		else
		{
			$line =~ s/\s+//g;
			$seqs{$name}{B} .= $line;
		}
	}
	foreach my $name (sort keys %seqs)
	{
		my $seqA = $seqs{$name}{A};
		my $seqB = $seqs{$name}{B};
		if ($seqA eq $seqB)
		{
			print F3 ">C.$name\n$seqA\n";
		}
		else
		{
			print F3 ">A.$name\n$seqA\n";
			print F3 ">B.$name\n$seqB\n";
		}
	}
	close F1; close F2; close F3;
	
}
##########################################################
sub buildCountFile
{
	my $xprFile = shift;
	my $datFile = shift;
	my $tag = shift;
	my $pstats = shift;


	$pstats->{$tag}{ref} = 0;
	$pstats->{$tag}{alt} = 0;
	$pstats->{$tag}{both} = 0;

	if ( (open X, $xprFile) and (open D, ">$datFile" ))
	{
		my %counts;
		<X>;
		while (my $line = <X>)
		{
			my @f = split /\s+/, $line;
			my $name = $f[1];
			my $count = $f[7];
			if ($hasAlt)
			{
				if ($name =~ /^([ABC])\./)
				{
					my $let = $1;
					if ($let eq "A")
					{
						$pstats->{$tag}{ref} += $count;
					}			
					elsif ($let eq "B")
					{
						$pstats->{$tag}{alt} += $count;
					}			
					else
					{
						$pstats->{$tag}{both} += $count;
					}			
					$name =~ s/\w\.//; # cut off the initial A.,B.,C.
				}
				else
				{
					errorMsg("Bad transcript name in eXpress file $xprFile:\n$line\n");
					exit(1);
				}
			}
			else
			{
				$pstats->{$tag}{both} += $count;
			}	
			$counts{$name} += $count;	
		}
		close X;
		foreach my $name (sort keys %counts)
		{
			my $count = int($counts{$name} + 0.5);
			print D "$name\t".$counts{$name}."\n";
		}
		close D;
	}
	else
	{
		errorMsg("Unable to write TCW file $datFile");
	}
}
##########################################################
sub checkExpress
{
	my $out = `$expressPath 2>&1`;
	if ($out =~ /Usage/)
	{
		return 1;
	}
	print STDERR "Can't run express command: $expressPath\n";
	exit(1);
}
##########################################################
sub makeOutputName
{
	my $f = shift;
	my $r = shift;

	$f =~ s/.*\///;
	$r =~ s/.*\///;

	$f =~ s/$fSuffix\..*$//;
	$r =~ s/$rSuffix\..*$//;

	return $f; # $r not used anymore for this
}
sub isFastq
{
	my $name = shift;

	my $ret = ( ($name =~ /\.fq$/) || ($name =~ /\.fastq$/)
				|| ($name =~ /\.fq\.gz$/) || ($name =~ /\.fastq\.gz$/));
	return $ret;
}
sub checkAddReads
{
	my $fReads = shift;
	my $plist = shift;
	my $suff = shift;

	if (-d $fReads)
	{
		foreach my $file (<$fReads/*>)
		{
			next if not -f $file;
			next if not $file =~ /$suff\./;
			if (!isFastq($file))
			{
				errorMsg("WARN: $file does not look like a fastq file!\n");
			}
			push @$plist, $file;
		}
		if (scalar(@$plist) == 0)
		{
			errorMsg("No fastq files found in directory $fReads\n");
			exit(1);
		}
	}
	else
	{
		errorMsg("-i parameter:$fReads is not a file or directory\n");
		usage();
	}

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
			errorMsg("Missing required parameter: $arg\n\n");
			usage();
		}
	} 
	else
	{
		if ($opts{$arg} eq "")
		{
			errorMsg("Empty argument for parameter: $arg\n\n");
			exit(1);	
		}
		$$pvar = $opts{$arg};
	}
}
sub buildIndex
{
	my $file = shift;

	if (open F, $file)
	{
		my $line = <F>;
		if (not $line =~ /^\s*>/)
		{
			errorMsg("ERROR: $file is not a fasta file\n");
			exit(1);
		}
		close F;
	}
	else
	{
		errorMsg("WARN: Unable to open file $file. Skipping\n");
	}

	if (-d "Index")
	{
		system("rm -Rf Index");
	}
	mkdir "Index";

	my $cmd = "$starPath --runMode genomeGenerate --genomeDir Index".
	 " --genomeChrBinNbits 10 ".
	" $starBuildParams ".
	" --genomeFastaFiles $file ";

	print "$cmd\n";

	system($cmd);

}
sub usage
{
	my $exit = shift;
	print <<END;

transASE.pl

Calls STAR to align a read file or directory of files (fastq format)
to transcript sequences (fasta format),
and then calls eXpress for expression quantification.

The program is designed to work with two transcript files, representing
REF and ALT alleles, resulting in allele-specific expression counts, plus
combined (allele-independent) counts. 

May also be run with only a single transcript set, resulting in
only the allele-independent counts.

OPTIONS:

    -r	target REF (or only) transcript file 
    -a	target ALT transcript file (if present)
    -i 	directory of read files 
    -p  read files are paired 
    -t	number of threads [default: 4]

default eXpress parameters (edit script header to change):
	--rf-stranded

default STAR parameters (edit script header to change):
     --alignIntronMax 1 --alignMatesGapMax 300 --outFilterMatchNmin 175 
     --outFilterMultimapNmax 100 --outFilterMultimapScoreRange 0
END

if (defined $exit)
{
	exit($exit);
}
exit(1);

}
