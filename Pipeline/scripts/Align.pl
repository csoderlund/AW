#!/bin/env perl
use strict;
use warnings;
$| = 1;
use Getopt::Std;

##################   EDITABLE VARIABLES ##########################

my $fSuffix = "_R1";
my $rSuffix = "_R2";

my $tophatPath =  "ext/tophat-2.0.12/tophat2";
my $bowtiePath =  "ext/bowtie2-2.2.3"; # NOTE use DIRECTORY path here
my $samPath =  "ext/samtools-0.1.19/samtools";

my $tophatParams = ""; # additional parameters for tophat
my $btOpts = "";  # additional parameters for bowtie2-build

#################################################################

my $fReads = "";

my $cmdline = join " ", $0, @ARGV;

my %opts;
my %stats;
my %fail;

my $ok = getopts('Eht:r:g:L:I:a:A:b:B:S:X:TpO:', \%opts);
if (0==scalar(keys %opts) or  $ok != 1)
{
	usage();
}
elsif (defined $opts{"h"})
{
	usage(0);
}

my $paired = (defined $opts{p} ? 1 : 0);

my $fastaFiles;
getParam("g",\$fastaFiles,1,"");
getParam("r",\$fReads,1,"");

my $inDE = (defined $opts{E} ? 1 : 0);

my $Dir = "TOPHAT";
my $topDir = `pwd`;
$topDir =~ s/\s+$//;
print "topdir:$topDir\n";

my $useExisting = 0;

if (-e $Dir)
{
	my $choice = "";
	while (not $choice =~ /^[yn]$/)
	{
		print "The directory $Dir already exists. Do you want to keep previous results? (y/n)\n";
		$choice = <STDIN>;
	}
	chomp($choice);
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
print "Output directory: $Dir\n";
chdir $Dir;

open C, ">cmdline.txt" or die "Unable to create cmdline.txt\n";
print C "$cmdline\n";
close C;

open E, ">Errors.txt" or die "Unable to create Errors.txt\n";

my $outputDir;
getParam("O",\$outputDir,0,"Results");
if ($outputDir ne "Results")
{
	if (not $outputDir =~ /^\//)
	{
		$outputDir = "$topDir/$outputDir";
	}
	if (not -d $outputDir)
	{
		errorMsg("Output directory does not exist: $outputDir");
		exit(1);		
	}
	system("touch $outputDir/foo123456.txt");
	if (not -f "$outputDir/foo123456.txt")
	{
		errorMsg("Unable to write to output directory $outputDir; check
permissions");
		exit(1);
	}
	unlink "$outputDir/foo123456.txt";
	print "Output directory:$outputDir\n";
}

if (not -e $outputDir)
{
	# should not happen except for Results
	mkdir $outputDir;
}


if (not $tophatPath =~ /^\//)
{
	$tophatPath = "$topDir/$tophatPath"
}
my $out = `$tophatPath 2>&1`;
if (not defined $out or not $out =~ /Usage/m)
{
	errorMsg("Can't run tophat2 command: $tophatPath\n");
	exit(1);
}

if (not $samPath =~ /^\//)
{
	$samPath = "$topDir/$samPath"
}
if (not $samPath =~ /samtools$/)
{
	$samPath = "$samPath/samtools";
}

$out = `$samPath 2>&1`;
if (not defined $out or not $out =~ /Usage/m)
{
	errorMsg("Can't run samtools command: $samPath\n");
	exit(1);
}


if (not $bowtiePath =~ /^\//)
{
	$bowtiePath = "$topDir/$bowtiePath"
}
$out = `$bowtiePath/bowtie2-build 2>&1`;
if (not defined $out or not $out =~ /Usage/m)
{
	errorMsg("Can't run bowtie2-build command: $bowtiePath/bowtie2-build\n");
	exit(1);
}
$out = `$bowtiePath/bowtie2 2>&1`;
if (not defined $out or not $out =~ /Usage/m)
{
	errorMsg("Can't run bowtie2-build command: $bowtiePath\n");
	exit(1);
}
chomp($ENV{PATH} = `echo \$PATH`);

if (not defined $ENV{"PATH"})
{
	errorMsg("WARN: path not defined");
}
if ($bowtiePath ne "")
{
	$ENV{"PATH"} = "$bowtiePath:".$ENV{"PATH"};
}
if ($samPath ne "")
{
	$ENV{"PATH"} = "$samPath:".$ENV{"PATH"};
}
my $nThreads;
getParam("t",\$nThreads,0,4);

if ($fastaFiles eq "")
{
    errorMsg("Please specify fasta file or directory\n");
    usage();
}
if (not $fastaFiles =~ /^\//)
{
	$fastaFiles = "$topDir/$fastaFiles";
}
my @files;
if (-d $fastaFiles)
{
    foreach my $file (<$fastaFiles/*>)
    {
        next if not -f $file;
        if (open F, $file)
        {
            my $line = <F>;
            if (not $line =~ /^\s*>/)
            {
                errorMsg("WARN: $file is not a fasta file. Skipping\n");
            }
            else
            {
                push @files, $file;
            }
            close F;
        }
        else
        {
            errorMsg("WARN: Unable to open file $file. Skipping\n");
        }
    }
    if (scalar(@files) == 0)
    {
        errorMsg("No fasta files found in directory $fastaFiles\n");
        exit(1);
    }
    else
    {
         print scalar(@files)." fasta files to be indexed\n";
    }
}
elsif (-f $fastaFiles)
{
	if (open F, $fastaFiles)
	{
		my $line = <F>;
		if (not $line =~ /^\s*>/)
		{
			errorMsg("Genome $fastaFiles is not a fasta file. Cannot build bowtie2 index.\n");
			exit(1);
		}
		else
		{
			push @files, $fastaFiles;
		}
		close F;
	}
	else
	{
		errorMsg(" Unable to open file $fastaFiles. Cannot build bowtie2 index.\n");
		exit(1);
	}	
}
else
{
    errorMsg("genome fasta parameter:$fastaFiles is not a file or directory\n");
    usage();
}

my $btIdx;
getParam("I",\$btIdx,0,"");
if ($btIdx eq "")
{
	my $files = join ",", @files;

	mkdir "btidx";
	my $btcmd = "$bowtiePath/bowtie2-build $btOpts $files btidx/btidx ";
	print "$btcmd\n";
	system($btcmd);
	$btIdx = "btidx/btidx";
}
else
{
	if (not $btIdx =~ /^\//)
	{
		$btIdx = "$topDir/$btIdx";
	}
	if (not -d $btIdx)
	{
		errorMsg("Bowtie2 index directory could not be found: $btIdx\n");
		exit(1);
	}
	my @files = (<$btIdx/*1.bt2>);	
	if (scalar(@files) == 0)
	{
		errorMsg("Directory $btIdx does not have a bowtie2 index (no file *.1.bt2)\n");
		exit(1);
	}
	my $root = "";
	foreach my $file (@files)
	{
		next if $file =~ /\.rev\.1\.bt2$/;
		$file =~ s/\.1\.bt2$//;
		$root = $file;
		last;
	}
	if ($root eq "")
	{
		errorMsg("Unable to identify bowtie2 index root in directory $btIdx\n");
		exit(1);
	}
	$btIdx = $root;
	print "Bowtie2 index root: $btIdx\n";
}


if (not $fReads =~ /^\//)
{
	$fReads = "$topDir/$fReads";
}

if ($paired == 0)
{
	$fSuffix = "";
}
my @fFiles;
checkAddReads($fReads,\@fFiles,$fSuffix);
@fFiles = sort @fFiles;
my @rFiles;
if ($paired)
{
	checkAddReads($fReads,\@rFiles,$rSuffix);
	@rFiles = sort @rFiles;
}

if (scalar(@rFiles) > 0)
{
    if (scalar(@rFiles) != scalar(@fFiles))
    {
        errorMsg("The number of reverse read files does not match the forward (".scalar(@rFiles).")\n");
        exit(1);
    }
    for (my $i = 0; $i < scalar(@fFiles); $i++)
    {
        my $f = $fFiles[$i];
        my $r = $rFiles[$i];
        #print "$f : $r\n";
        my $f1 = $f;
        my $r1 = $r;
        $f1 =~ s/.*\///;
        $r1 =~ s/.*\///;
        $f1 =~ s/$fSuffix\.\S+$//;
        $r1 =~ s/$rSuffix\.\S+$//;
        if ($f1 ne $r1)
        {
            errorMsg("WARN: forward file $f does not appear to match reverse file $r\nMake sure that file names are consistent and can be matched by alphabetical ordering.");
            exit(1);
        }
    }
}

my $libType;
getParam("L",\$libType,0, "fr-unstranded");
if (not $libType =~ /fr-unstranded|fr-firststrand|fr-secondstrand/)
{
	errorMsg("Library type should be one of fr-unstranded,fr-firststrand,fr-secondstrand");
	exit(1);
}

my $gtf;
getParam("a",\$gtf,0,"");
if ($gtf ne "")
{
	if (not $gtf =~ /^\//)
	{
		$gtf = "$topDir/$gtf";
	}
	if (not -f $gtf)
	{
		errorMsg("Can't find annotation file $gtf\n");
		exit(1);
	}
}

my $aIdx;
getParam("A",\$aIdx,0,"");
if ($aIdx ne "")
{
	if ($gtf eq "")
	{
		errorMsg("Annotation index directory was specified but no annotation file was provided.\n");
		exit(1);
	}
	if (not $aIdx =~ /^\//)
	{
		$aIdx = "$topDir/$aIdx";
	}
    if (not -d $aIdx)
    {
        errorMsg("Bowtie2 annotation index directory could not be found: $aIdx\n");
        exit(1);
    }
    my @files = (<$aIdx/*1.bt2>);
    if (scalar(@files) == 0)
    {
        errorMsg("Directory $aIdx does not have a bowtie2 index (no file *.1.bt2)\n");
        exit(1);
    }
    my $root = "";
    foreach my $file (@files)
    {
        next if $file =~ /\.rev\.1\.bt2$/;
        $file =~ s/\.1\.bt2$//;
        $root = $file;
        last;
    }
    if ($root eq "")
    {
        errorMsg("Unable to identify bowtie2 annotation index root in directory $btIdx.");
        exit(1);
    }
    $aIdx = $root;
    print "Bowtie2 annotation index root: $aIdx\n";
}
else
{
	if ($gtf ne "")
	{
		mkdir "annotidx";
		$aIdx = "annotidx/annotidx";
	}	
}

my $transOnly = (defined $opts{T} ? " -T " : "");

my @fileNames;
foreach my $name (@fFiles)
{
    my $root = $name;
    $root =~ s/.*\///;
    push @fileNames, $root;
}

for (my $i = 0; $i < scalar(@fFiles); $i++)
{
	my $f = $fFiles[$i];
	my $r = "";
	if (scalar(@rFiles) > 0)
	{
		$r = $rFiles[$i];
	}
	my $outRoot = makeOutputName($f,$r);
	my $outFile = "$outputDir/$outRoot.bam";
	if (-e $outFile) 
	{
		print "$outFile exists; skipping\n";
		next;
	}	
	print "Output file:$outFile\n";
   	my $cmd = "$tophatPath --library-type $libType -p $nThreads  ";
    $cmd .= " $transOnly $tophatParams ";
	if ($gtf ne "")
	{
    	$cmd .= " -G $gtf --transcriptome-index=$aIdx ";
	}
    $cmd .= "  $btIdx $f $r ";
	if (-d "tophat_out")
	{
		system("rm -Rf tophat_out");
	}
    print "$cmd\n";
    system($cmd);
	
	if (-d "tophat_out" and -f "tophat_out/accepted_hits.bam")
	{
		system("cp tophat_out/accepted_hits.bam $outFile");
		parseStats($outRoot);
	}
	else
	{
		failStats(makeOutputName($f,$r), "No alignment generated");
		errorMsg("Alignment failed for:$f,$r\n");
	}
}
generateReport();
close E;
if (-z "Errors.txt")
{
    unlink "Errors.txt";
}

print "Align completed.\nOutput is in $Dir/Results.\nSummary stats:$Dir/Summary.html\n";
print "To view, use File|Open in a web browser, or open from the command line e.g.\n";
print "firefox $Dir/Summary.html\n";

##########################################################
# Left reads:
#                Input:       250
#               Mapped:        23 ( 9.2% of input)
#             of these:         5 (21.7%) have multiple alignments (0 have >20)
# Right reads:
#                Input:       250
#               Mapped:        32 (12.8% of input)
#             of these:         9 (28.1%) have multiple alignments (0 have >20)
# 11.0% overall read alignment rate.
# 
# Aligned pairs:         7
#      of these:         0 ( 0.0%) have multiple alignments
#           	         0 ( 0.0%) are discordant alignments
#  2.8% concordant pair alignment rate.
#
sub parseStats
{
	my $name = shift;
	if (open R, "tophat_out/align_summary.txt" )
	{
		undef $/;
		my $slurp = <R>; 
		$/ = "\n";
		close R;
		$slurp =~ s/\s+/ /gs;
		$slurp =~ s/\([^\(]+\)//g;
		$slurp =~ s/\S+%//g;
		$slurp =~ s/[^\d\s]//g;
		$slurp =~ s/^\s+//;
		$slurp =~ s/\s+$//;
		my @f = split /\s+/,$slurp;
		if ($paired == 1)
		{
			if (scalar(@f) == 9)	
			{
				$stats{$name}{inR} 		= $f[0]; 
				$stats{$name}{mapR} 	= $f[1]; 
				$stats{$name}{multR} 	= $f[2]; 
				$stats{$name}{inL} 		= $f[3]; 
				$stats{$name}{mapL} 	= $f[4]; 
				$stats{$name}{multL} 	= $f[5]; 
				$stats{$name}{mapP}		= $f[6]; 
				$stats{$name}{multP} 	= $f[7]; 
				$stats{$name}{discP} 	= $f[8]; 
			}
			else
			{
				errorMsg("WARN: Could not parse align_summary.txt for paired output: $name");
				failStats($name, "Unable to parse align_summary.txt for paired output");
			}
		}
		else
		{
			if (scalar(@f) == 3)	
			{
				$stats{$name}{inR} 		= $f[0]; 
				$stats{$name}{mapR} 	= $f[1]; 
				$stats{$name}{multR} 	= $f[2]; 
			}
			else
			{
				errorMsg("WARN: Could not parse align_summary.txt for unpaired output: $name");
				failStats($name, "Unable to parse align_summary.txt for unpaired output");
			}

		}
	}
	else
	{
		errorMsg("WARN: Could not read align_summary.txt for $name\n");
		failStats($name, "No align_summary.txt");
	}
}

####################################################################

sub generateReport
{
	if (open R, ">Summary.html")
	{
		print R <<END;
<html>
<body>
<h3>Alignment Summary</h3>
This table was created using data from the align_summary.txt for each tophat2 alignment run. 
END
		if ($paired)
		{
			print R <<END;
<p>
<table rules=all border=true cellpadding=3>
	<tr>
		<td>Sample</td>
		<td>Reads-1</td>
		<td>Mapped-1</td>
		<td>Multi-1</td>
		<td>Reads-2</td>
		<td>Mapped-2</td>
		<td>Multi-2</td>
		<td>Pairs Mapped</td>
		<td>Pairs Multi</td>
		<td>Pairs Discordant</td>
	</tr>
END
			foreach my $name (sort keys %stats)
			{
				print R <<END;
	<tr>
		<td>$name</td>
		<td>$stats{$name}{inR}</td>
		<td>$stats{$name}{mapR}</td>
		<td>$stats{$name}{multR}</td>
		<td>$stats{$name}{inL}</td>
		<td>$stats{$name}{mapL}</td>
		<td>$stats{$name}{multL}</td>
		<td>$stats{$name}{mapP}</td>
		<td>$stats{$name}{multP}</td>
		<td>$stats{$name}{discP}</td>
	</tr>
END

			}
		}
		else
		{
			print R <<END;
<p>
<table rules=all border=true cellpadding=3>
	<tr>
		<td>Sample</td>
		<td>Reads</td>
		<td>Mapped</td>
		<td>Multi</td>
	</tr>
END
			foreach my $name (sort keys %stats)
			{
				print R <<END;
	<tr>
		<td>$name</td>
		<td>$stats{$name}{inR}</td>
		<td>$stats{$name}{mapR}</td>
		<td>$stats{$name}{multR}</td>
	</tr>
END
			}
		}
		if (0 != scalar(keys %fail))
		{
			print R <<END;
<h4>Failed Samples:</h4>
<table>
	<tr>
		<td>Name</td>
		<td>Reason</td>
	</tr>
END
			foreach my $name (sort keys %fail)
			{
				print R <<END;
	<tr>
		<td>$name</td>
		<td>$fail{$name}</td>
	</tr>
END
			}
			print R <<END;
</table>
END
		}

		print R <<END;
</table>
</body>
</html>
END
		close R;
	}
	else
	{
		errorMsg("Could not create Summary.html");
	}
}
sub failStats
{
	my $name = shift;
	my $reason = shift;
	$fail{$name} = $reason;	
}
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

sub usage
{
	my $exit = shift;
	print <<END;

tophat.pl 

Calls tophat2 to align a read file or directory of files (fastq format). 

If bowtie genome and/or annotation indexes were previously built, they can
be re-used. Otherwise they will be built and can be later used elsewhere. 

OPTIONS:

------ Bowtie2-build options -----------------------

    -g  directory with genome fasta files 
    -I  [optional] bowtie index directory, if already built  

------ Tophat2 options -----------------------

    -r 	directory of read files 

    -p 	read files are paired [default: unpaired]
    -t	number of threads (runThreadN) [default: 4]
    -L  library type [default: fr-unstranded]
    -a	[optional] annotation GFF/GTF file 
    -T	[optional] map to transcriptome only
END

if (defined $exit)
{
	exit($exit);
}
exit(1);

}
