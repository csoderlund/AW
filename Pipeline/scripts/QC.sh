#! /bin/bash
# Script courtesy of R. Barthelson
# Adapted by W. Nelson as follows:
# Pipeline changes: directory names; no manifest; ability to run outside DE;
# take fastqc path as parameter.
# Write out embed.pl on the fly and remove File::Slurp dependence.

##################   EDITABLE VARIABLES ##########################

fqcPath="ext/FastQC"

#################################################################

function usage {
	cat << EOF

QC.sh 

Uses the FastQC library to compute quality statistics for NGS read
files, and produces a unified HTML summary report. 

OPTIONS:
    
    -d	directory of read files

EOF
exit 1
}

DE=0
while getopts d:EP:h option
do
	case "${option}"
    in
   		 d) Dir1=$OPTARG;;
   		 E) DE=1;;
   		 P) fqcPath=$OPTARG;;
		 h) usage;;
     esac
done

if [[ ! -n "$Dir1" ]];
then
	echo "Please provide a directory of read files"
	usage
fi

curDir=$(pwd)
if [[ "$Dir1" = /* ]]; 
then
	echo ""
else
	Dir1="$curDir/$Dir1"
fi

if [[ ! -d $Dir1 ]];
then
	echo "$Dir1 is not a directory"
	usage
fi

if [[ -n "$fqcPath" && ! -d "$fqcPath" ]];
then
	echo "For -P, give the directory where FastQC is located";
	usage
	exit 1
fi
if [[ -n "$fqcPath" ]];
then

	if [[ "$fqcPath" = /* ]]; 
	then
		echo ""
	else
		fqcPath="$curDir/$fqcPath"
	fi
	fqcPath="$fqcPath/fastqc"
	if [[ ! -x "$fqcPath" ]];
	then
		echo "Unable to run FastQC command: $fpcPath"
		echo "Check that the file exists and has the correct permissions"
		exit 1
	fi
else
	fqcPath="fastqc"
fi
testOut=`$fqcPath -h | grep SYNOPSIS`
if [[ $? -ne 0 ]];
then
	echo "Unable to run FastQC test command: $fqcPath -h"
	echo "Check installation of FastQC"
	exit 1
fi

if [[  -d "QC" ]];
then
	while true; do
		read -p "Directory QC exists and will be overwritten. Continue? (y/n)" yn
		case $yn in
			[Yy]* ) rm -Rf QC; break;;
			[Nn]* ) exit;;
			* ) echo "Please answer yes or no.";;
		esac
	done
fi
mkdir "QC"
cd "QC"

mkdir zipped_reports
mkdir outputs
CURDIR=`pwd`

for x in $Dir1/*
do
	echo $x
	if [[ -d $x ]];
	then
		continue
	fi
	if [[ -d tmp ]];
	then
		rm -Rf tmp/*
	else
		mkdir tmp;
	fi
	X=$(basename $x)
	$fqcPath -t 4 $x -o ./tmp
	wait
	mv tmp/*.zip zipped_reports
	for x in tmp/*
	do
		XXN=$(basename $x);
		mv $x outputs
	done
done

sumFile="$CURDIR/Summary.html"

echo '<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Strict//EN">' > $sumFile
echo '<html' >> $sumFile
echo '<head><title>Summary of Fastqc Reports</title>' >> $sumFile
echo '<style type="text/css">' >> $sumFile
echo '	body { font-family: sans-serif; color: #0098aa; background-color: #FFF; font-size: 100%; border: 0; margin: 0; padding: 0; }' >> $sumFile
echo '	h1 { font-family: sans-serif; color: #0098aa; background-color: #FFF; font-size: 300%; font-weight: bold; border: 0; margin: 0; padding: 0; }' >> $sumFile
echo '	h2 { font-family: sans-serif; color: #0098aa; background-color: #FFF; font-size: 200%; font-weight: bold; border: 0; margin: 0; padding: 0; }' >> $sumFile	
echo '	h3 { font-family: sans-serif; color: #0098aa; background-color: #FFF; font-size: 40%; font-weight: bold; border: 0; margin: 0; padding: 0; }' >> $sumFile
echo '	.TFtable tr:nth-child(even){ background: #D2DADC; }'	>> $sumFile	
echo '	</style>' >> $sumFile
echo '	</head>' >> $sumFile
echo '	<h1> Summary of Fastqc Reports' >> $sumFile
echo '	<br/>' >> $sumFile
echo '	<br/> </h1>' >> $sumFile
echo '	<body> ' >> $sumFile
echo '	<table border="1" cellpadding="10" bgcolor="white" class="TFtable">' >> $sumFile
echo '	<tr>' >> $sumFile
echo '	<td><b>Fastq File Name</b></td>' >> $sumFile
echo '    <td><b>Basic Statistics</b></td>' >> $sumFile
echo '    <td><b>Per base sequence quality</b></td>' >> $sumFile
echo '    <td><b>Per sequence quality scores</b></td>' >> $sumFile
echo '    <td><b>Per base sequence content</b></td>' >> $sumFile
echo '    <td><b>Per base GC content</b></td>' >> $sumFile
echo '    <td><b>Per sequence GC content</b></td>' >> $sumFile
echo '    <td><b>Per base N content</b></td>' >> $sumFile
echo '    <td><b>Sequence Length Distribution</b></td>' >> $sumFile
echo '    <td><b>Sequence Duplication Levels</b></td>' >> $sumFile
echo '    <td><b>Overrepresented sequences</b></td>' >> $sumFile
echo '    <td><b>Kmer Content</b></td>' >> $sumFile
echo ' </tr>' >> $sumFile

if [[ -f embed.pl ]];
then
	rm embed.pl
fi

cat << ENDEMBED > embed.pl
#!/usr/bin/env perl -w
#use File::Slurp;
use warnings;

# embed png files in fastqc output, Roger Barthelson

\$webfile = \$ARGV[0];
\$out_file = \$ARGV[1];
if ( !defined \$webfile || !defined \$out_file  ) {
    die "Usage:  \$0  webfile outfile \n";
}
open( WFIL, "\$webfile" ) or die "Cannot open webfile\n";
open( OFIL, ">>\$out_file" ) or die "Cannot open outfile\n";
while ( \$lin = <WFIL> ) {
    chomp(\$lin);
    \$lin =~ tr/\t/ /;
    @col = split( / /, \$lin );
    my \$col_count = scalar(@col);
    \$max = \$col_count - 1 ;
    for ( \$i = 0 ; \$i < \$col_count ; \$i++ ) {
    if ( \$col[\$i] =~ m/tick.png/ ) {
                my \$translate_command = "openssl base64 -in Icons/tick.png -out tick.b64";
		print "\$translate_command\n";
                system("\$translate_command > /dev/null 2>&1");
                \$base64image = read_file('./tick.b64');
                print OFIL 'src="data:image/png;base64,';
                print OFIL "\$base64image";
                print OFIL '"';
} elsif ( \$col[\$i] =~ m/warning.png/ ) {
                my \$translate_command = "openssl base64 -in Icons/warning.png -out warning.b64";
		print "\$translate_command\n";
                system("\$translate_command > /dev/null 2>&1");
                \$base64image = read_file('./warning.b64');
                print OFIL 'src="data:image/png;base64,';
                print OFIL "\$base64image";
                print OFIL '"';
} elsif ( \$col[\$i] =~ m/error.png/ ) {
        my \$translate_command = "openssl base64 -in Icons/error.png -out error.b64";
		print "\$translate_command\n";
        system("\$translate_command > /dev/null 2>&1");
        \$base64image = read_file('./error.b64');
        print OFIL 'src="data:image/png;base64,';
        print OFIL "\$base64image";
        print OFIL '"';
} elsif ( \$col[\$i] =~ m/fastqc_icon.png/ ) {
                my \$translate_command = "openssl base64 -in Icons/fastqc_icon.png -out fastqc_icon.b64";
		print "\$translate_command\n";
                system("\$translate_command > /dev/null 2>&1");
                \$base64image = read_file('./fastqc_icon.b64');
                print OFIL 'src="data:image/png;base64,';
                print OFIL "\$base64image";
                print OFIL '"';
} elsif ( \$col[\$i] =~ m/Images/ ) {
#\$col[\$i] =~ tr/src=//;
        @text = split (/=/, \$col[\$i] );
                my \$translate_command = "openssl base64 -in ".\$text[1]." -out imagefile.b64";
		print "\$translate_command\n";
                system("\$translate_command > /dev/null 2>&1");
                \$base64image = read_file('./imagefile.b64');
                print OFIL 'src="data:image/png;base64,';
                print OFIL "\$base64image";
                print OFIL '"';
} elsif (\$i == \$max) {
print OFIL "\$col[\$i]\n";
} else {
print OFIL "\$col[\$i] ";
}
}
}

sub read_file
{
        my \$f = shift;
        undef \$/;
        open F, \$f or die "Can't open \$f\n";
        my \$slurp = <F>;
        close F;
        \$/ = "\n";
        return \$slurp;
}
ENDEMBED

WW=0
for w in outputs/*
do
	WW=`expr $WW + 1`
	echo ' <tr>' >> $sumFile
	echo '	<div>' >> $sumFile
	echo '	<ul>' >> $sumFile
	cd $w
	wbase=$(basename $w)
	echo "Embed images for $wbase"
	ww="$CURDIR/$wbase.report.html"
	perl $CURDIR/embed.pl fastqc_report.html $ww 
	csplit -s -f sum $ww /\<li\>/ {10} /class=\"main\"\>/
	csplit -s $ww /'<div class="module">'/ {10} /'alt="Kmer graph"'/
	seq_file=`grep title\> xx00 | sed 's/<head><title>//'  | sed 's/FastQC\ Report<\/title>//'`
	echo "	<td><b>$seq_file</b></td>" >> $sumFile
	sumRe=( sum01 sum02 sum03 sum04 sum05 sum06 sum07 sum08 sum09 sum10 sum11 )
	for ((RR=0; RR < 11; RR += 1))
	do
		cellcontent=`cat "${sumRe[$RR]}" | sed s/#M/\#\$WW-M/`
		echo "	<td><b>$cellcontent</b></td>" >> $sumFile
	done		
	cd $CURDIR
	echo ' </tr>' >> $sumFile
done
echo '</table>' >> $sumFile
echo '	<br/>' >> $sumFile
echo '	<br/>' >> $sumFile
echo '	<br/>' >> $sumFile
echo '	<br/>' >> $sumFile
echo '<table border="1" cellpadding="10" bgcolor="white" class="TFtable">' >> $sumFile
echo '	<tr>' >> $sumFile
echo '    <td><b>Basic Statistics</b></td>' >> $sumFile
echo '    <td><b>Per base sequence quality</b></td>' >> $sumFile
echo '    <td><b>Per sequence quality scores</b></td>' >> $sumFile
echo '    <td><b>Per base sequence content</b></td>' >> $sumFile
echo '    <td><b>Per base GC content</b></td>' >> $sumFile
echo '    <td><b>Per sequence GC content</b></td>' >> $sumFile
echo '    <td><b>Per base N content</b></td>' >> $sumFile
echo '    <td><b>Sequence Length Distribution</b></td>' >> $sumFile
echo '    <td><b>Sequence Duplication Levels</b></td>' >> $sumFile
echo '    <td><b>Overrepresented sequences</b></td>' >> $sumFile
echo '    <td><b>Kmer Content</b></td>' >> $sumFile
echo ' </tr>' >> $sumFile
WW=0
for w in outputs/*
do
	WW=`expr $WW + 1`
	echo ' <tr>' >> $sumFile	
	cd $w

	seq_file=`grep title\> xx00 | sed 's/<head><title>//'  | sed 's/FastQC\ Report<\/title>//'`

	xxRe=( xx01 xx02 xx03 xx04 xx05 xx06 xx07 xx08 xx09 xx10 xx11 )	
	for ((SS=0; SS < 10; SS += 1))
	do
		graphstuff=`cat "${xxRe[$SS]}" | sed 's/"indented" src=/"indented" height="320" width="400" src=/g'`
		graphcontent="<h3 id=$WW-M$SS > $seq_file $graphstuff </h3>"

		echo "	<td><b>$graphcontent</b></td>" >> $sumFile
	done
	graphstuff11=`cat "xx11" | sed 's/"indented" src=/"indented" height="320" width="400" src=/g'`
	graphcontent11="<h3 id=$WW-M10 > $seq_file $graphstuff11 "'"alt="[OK]">kmer graph</h2></p></div></h3>'
	echo "	<td><b>$graphcontent11</b></td>" >> $sumFile
	cd $CURDIR
	echo ' </tr>' >> $sumFile
done
echo '</table>' >> $sumFile					
echo '</body>' >> $sumFile
echo '</html' >> $sumFile

echo "The summary file for all the FASTQC reports has been created."
mkdir individual_reports
mv *.html individual_reports
mv individual_reports/Summary.html .
rm embed.pl
rm -Rf zipped_reports
rm -Rf outputs
rm -Rf tmp

cat << EOF

QC completed.
Unified output file is:QC/Summary.html
To view, use File|Open in a web browser, or open from the command line e.g.
firefox QC/Summary.html

EOF
