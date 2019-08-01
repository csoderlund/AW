#!/bin/env perl
use strict;
use warnings;
$| = 1;
use Getopt::Std;

##################   EDITABLE VARIABLES ##########################

my $fSuffix = "_R1";
my $rSuffix = "_R2";

my $trimmoPath =  "ext/Trimmomatic-0.32/trimmomatic-0.32.jar";
my $JavaMemory = "1024m";

#################################################################

my $cmdline = join " ", $0, @ARGV;

my %opts;
my $ok = getopts('shpJ:d:I:A:M:L:T:C:H:N:a:S:36P:t:E', \%opts);
if (0==scalar(keys %opts) or  $ok != 1)
{
	usage();
}
elsif (defined $opts{"h"})
{
	usage(0);
}

my $fReads = "";
getParam("d",\$fReads,1,"");

my $Dir = "TRIM";
my $topDir = `pwd`;
$topDir =~ s/\s+$//;
print "topdir:$topDir\n";

if (not $trimmoPath =~ /^\//)
{
	$trimmoPath = "$topDir/$trimmoPath";
}
my $out = `java  -jar $trimmoPath 2>&1`;
if (not defined $out or not $out =~ /Usage/)
{
	errorMsg("Can't run Trimmomatic command: $trimmoPath\n");
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
			print "The directory $Dir already exists. Do you want to keep the previous results? (y/n)\n";
			$choice = <STDIN>;
		}
		chomp($choice);
		if ($choice eq  "n")
		{
			system("rm -Rf $Dir");
			mkdir $Dir;
		}
		else
		{
			# get rid of the partially-completed
			foreach my $file (<$Dir/Results/*>)
			{
				next if $file =~ /\.gz$/;
				unlink $file;
			}
		}
	}
	else
	{
		mkdir $Dir;
	}
}
chdir $Dir;

open C, ">cmdline.txt" or die "Unable to create cmdline.txt\n";
print C "$cmdline\n";
close C;

open E, ">Errors.txt" or die "Unable to create Errors.txt\n";

mkdir "Results";

my $nThreads;
getParam("t",\$nThreads,0,4);

my $params;
getParam("P",\$params,1,"");
if (not $params =~ /HEADCROP|SLIDINGWINDOW|ILLUMINACLIP|CROP|LEADING|TRAILING|MAXINFO|MINLEN/)
{
	errorMsg("Please specify one or more Trimmomatic functions to apply");
	usage();
}

if ($fReads eq "")
{
	errorMsg("Please specify a directory of read files with -f\n");
	usage();
}
if (not $fReads =~ /^\//)
{
	$fReads = "$topDir/$fReads";
}

my @fFiles;
my @rFiles;

my $paired = (defined $opts{p} ? 1 : 0);
my $keepSing  = (defined $opts{s} ? 1 : 0);

checkAddReads($fReads,\@fFiles,$fSuffix);
@fFiles = sort @fFiles;
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
        #print "check pairing of $f : $r\n";
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

# Fork off the specified threads.
# Each one will work through the same list, skipping 
# bams for which the output already exists. 

if (scalar(@fFiles) < $nThreads)
{
	$nThreads = scalar(@fFiles);
}
my $pid;
my @pids;
my $thread;
my %stats1;
my $nSeqs;
for ($thread = 1; $thread <= $nThreads; $thread++)
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

	my @logs = (<trim*.log>);
	for (my $i = 0; $i < scalar(@logs); $i++)
	{
		my $log = $logs[$i];
		parseTrimLog($log,$i);
		unlink $log;
	}
	makeTrimReport();
	close E;
	if (-z "Errors.txt")
	{
		unlink "Errors.txt";
	}
	if (-d "Results/Sing" and not $keepSing)
	{
		system("rmdir Results/Sing");
	}
	print "Trim completed.\nOutput is in $Dir/Results.\nSummary stats:$Dir/Summary.html\n";
	print "To view, use File|Open in a web browser, or open from the command line e.g.\n";
	print "firefox $Dir/Summary.html\n";
	exit(0);
}

# Work routine, reached if $pid==0

for (my $i = 0; $i < scalar(@fFiles); $i++)
{
	my $f = $fFiles[$i];
	my $r = "";
	if (scalar(@rFiles) > 0)
	{
		$r = $rFiles[$i];
	}
	
	my $PE = ($paired==1 ? " PE " : " SE ");

	my $fout = $f;
	my $rout;
	$fout =~ s/.*\///;
	$fout = "Results/$fout";
	next if -f "$fout.started" or -f $fout or -f "$fout.gz";
	system("touch $fout.started");

	my $fsing = $fout;
	$fsing =~ s/Results/Results\/Sing/;
	my $rsing;
	
	my $fileLine = " ";
	if ($paired==1)
	{
		mkdir "Results/Sing";
		$rout = $r;
		$rout =~ s/.*\///;
		$rout = "Results/$rout";
		$rsing = $rout;
		$rsing =~ s/Results/Results\/Sing/;
		$fileLine = " $f $r $fout $fsing $rout $rsing ";
	}
	else
	{
		$fileLine = " $f $fout ";
	}
	my $log = "trim$i.log";
	my $cmd = "java -Xmx$JavaMemory -jar $trimmoPath $PE -threads 1  -trimlog $log $fileLine $params";

	print "$cmd\n";
	system($cmd);
	if (not $fout =~ /\.gz$/)
	{
		if ($paired == 1)
		{
			$cmd = "gzip $fout";
			print "$cmd\n";
			system($cmd);
			$cmd = "gzip $rout";
			print "$cmd\n";
			system($cmd);
			if (not $keepSing)
			{
				$cmd = "rm $fsing";
				system($cmd);
				$cmd = "rm $rsing";
				system($cmd);
			}
		}
		else
		{
			$cmd = "gzip $fout";
			print "$cmd\n";
			system($cmd);
		}	
	}
	system("rm $fout.started");
}


##########################################################
sub parseTrimLog
{
	my $log = shift;
	my $thread = shift;
	print "parse $log\n";
	my %paired;
	my $msgShown = 0;
	
	if (open F, "$log")
	{
		while (my $line = <F>)
		{
			$nSeqs++;
			my @f = split /\s+/, $line;
			my $len = $f[1];
			next if $len == 0; # looks like these are not present in output
			my $name = $f[0];
			if ($name =~ /(\S+)\/(\d)/)
			{
				$name = $1;
				my $num = $2;
				$paired{$name}{$num} = 1;
			}
			else
			{
				if ($msgShown == 0)
				{
					print "Can't parse read name $name\nPaired output numbers may be inaccurate\n";
					$msgShown = 1;
				}
			}
			my $starttrim = $f[2];
			my $endtrim = $f[4];
			my $lenBin = int(($len/10));
			my $startBin = int(($starttrim/5));
			my $endBin = int(($endtrim/5));
			$stats1{$thread}{len}{$lenBin}++;
			$stats1{$thread}{end}{$endBin}++;
			$stats1{$thread}{start}{$startBin}++;
			$stats1{$thread}{totallen} += $len;
			$stats1{$thread}{totalcount}++;
		}
		close F;
		$stats1{$thread}{paired} = 0;
		$stats1{$thread}{single} = 0;
		foreach my $name (keys %paired)
		{
			if (2 == (scalar keys %{$paired{$name}}))
			{
				$stats1{$thread}{paired}++;
			}
			else	
			{
				$stats1{$thread}{single}++;
			}
		}
	}
	else
	{
		errorMsg("Unable to open trim log\n");
	}
}
sub makeTrimReport
{
	my %stats;
	foreach my $t (keys %stats1)
	{
		$stats{totallen} += $stats1{$t}{totallen};
		$stats{totalcount} += $stats1{$t}{totalcount};
		$stats{paired} += $stats1{$t}{paired};
		$stats{single} += $stats1{$t}{single};
		foreach my $bin (keys %{$stats1{$t}{len}})
		{
			$stats{len}{$bin} += $stats1{$t}{len}{$bin};
		}	
		foreach my $bin (keys %{$stats1{$t}{end}})
		{
			$stats{end}{$bin} += $stats1{$t}{end}{$bin};
		}	
		foreach my $bin (keys %{$stats1{$t}{start}})
		{
			$stats{start}{$bin} += $stats1{$t}{start}{$bin};
		}	
	}
	my $barStyle = "background-color:#4682b4;color:white;text-align:right;padding:1px 2px 0px 0px;";
	my $widthFactor = 8;
	open G, ">Summary.html" or die;
	print G <<END;
<html>
<head>
<style>
</style>
</head>
<body>
END

my $nFiles = scalar(@fFiles);
print G "<b>Files trimmed:</b> $nFiles<br>\n";
my $wc = `wc -l $fReads/*`;
if ($wc =~ /(\d+)\s+total/i)
{
	my $nSeqs = $1/4;
	print G "<b>Sequences trimmed:</b> $nSeqs<br>\n";
}

my $finalCount = $stats{totalcount};
my $singCount = $stats{single};
my $pairedCount = $stats{paired};
my $disc = ($keepSing ? "" : " (discarded) ");
print G "<b>Final paired sequences:</b> $pairedCount<br>\n";
print G "<b>Final single sequences:</b> $singCount $disc<br>\n";

my $avgLen = int(0.5 + $stats{totallen}/$finalCount);
print G "<b>Avg trimmed length:</b> $avgLen<br>\n";

if (0)  # broken by gzip
{
if ($paired==1)
{
	$wc = `wc -l Results/*`;
	if ($wc =~ /(\d+)\s+total/i)
	{
		my $nSeqs = $1/4;
		print G "<b>Paired Output Sequences:</b> $nSeqs<br>\n";
	}
	$wc = `wc -l Results/Sing/*`;
	if ($wc =~ /(\d+)\s+total/i)
	{
		my $nSeqs = $1/4;
		print G "<b>Unpaired Output Sequences:</b> $nSeqs<br>\n";
	}
}
else
{

	$wc = `wc -l Results/*`;
	if ($wc =~ /(\d+)\s+total/i)
	{
		my $nSeqs = $1/4;
		print G "<b>Output Sequences:</b> $nSeqs<br>\n";
	}
}
}
print G "<p>\n";
print G "<b>parameters:</b>$params<br>\n";
print G <<END;
<h4>Trimmed Lengths:</h4>
<table>
	<tr>
		<td align=left width=150>Trimmed Length</td>
		<td align=left>Percent of Reads</td>
	</tr>
END

my $maxBin = 0;
foreach my $bin (keys %{$stats{len}})
{
	if ($bin > $maxBin)
	{
		$maxBin = $bin;
	}
}
for (my $b = 0; $b <= $maxBin; $b++)
{
	my $pct = 0;
	if (defined $stats{len}{$b})
	{
		my $num = $stats{len}{$b};
		$pct = int((100*$num/$nSeqs));
		my $width = $widthFactor*$pct;
		if ($width < 10)
		{
			$width = 10;
		}
		my $range = (10*$b)."-".(10*($b+1)-1);
print G <<END;
<tr>
	<td>$range</td>
	<td><div style="$barStyle;width: ${width}px;">$pct</div></td>
</tr>
END

	}	
}
print G "</table>\n";
print G <<END;

<h4>Start Trim Amounts:</h4>
<table>
	<tr>
		<td align=left width=150>Amount Trimmed</td>
		<td align=left>Percent of Reads</td>
	</tr>
END

$maxBin = 0;
foreach my $bin (keys %{$stats{start}})
{
	if ($bin > $maxBin)
	{
		$maxBin = $bin;
	}
}
for (my $b = 0; $b <= $maxBin; $b++)
{
	my $pct = 0;
	my $range = (5*$b)."-".(5*($b+1)-1);
	if (defined $stats{start}{$b})
	{
		my $num = $stats{start}{$b};
		$pct = int((100*$num/$nSeqs));
		my $width = $widthFactor*$pct;
		if ($width < 10)
		{
			$width = 10;
		}
print G <<END;
<tr>
	<td>$range</td>
	<td><div style="$barStyle;width: ${width}px;">$pct</div></td>
</tr>
END

	}	
}
print G "</table>\n";
print G <<END;

<h4>End Trim Amounts:</h4>
<table>
	<tr>
		<td align=left width=150>Amount Trimmed</td>
		<td align=left>Percent of Reads</td>
	</tr>
END

$maxBin = 0;
foreach my $bin (keys %{$stats{end}})
{
	if ($bin > $maxBin)
	{
		$maxBin = $bin;
	}
}
for (my $b = 0; $b <= $maxBin; $b++)
{
	my $pct = 0;
	my $range = (5*$b)."-".(5*($b+1)-1);
	if (defined $stats{end}{$b})
	{
		my $num = $stats{end}{$b};
		$pct = int((100*$num/$nSeqs));
		my $width = $widthFactor*$pct;
		if ($width < 10)
		{
			$width = 10;
		}
print G <<END;
<tr>
	<td>$range</td>
	<td><div style="$barStyle;width: ${width}px;">$pct</div></td>
</tr>
END

	}	
}
print G "</table>\n";
print G <<END;
</body>
</html>
END
close G;
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
		errorMsg("-d parameter:$fReads is not a file or directory\n");
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

Trim.pl

Lightweight wrapper for NGS trimming program Trimmomatic.

OPTIONS:

    -d 	directory of read files to trim
    -P 	Trimmomatic parameters (example below)

    -t	[optional] number of simultaneous processes to run [default: 4]
    -p  [optional] read files are paired
    -s  [optional] keep singletons output (otherwise deleted)

Example:

perl Trim.pl -d readDir -p -P "LEADING:30 TRAILING:30 HEADCROP:5 MINLEN:30"

END
if (defined $exit)
{
	exit($exit);
}
exit(1);

}
