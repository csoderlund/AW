use strict;
use warnings;
$| = 1;

# Break up a multi-sequence fasta file, e.g. genome file
# into its chromosomes for loading to AW. 
#
# Usage:  perl GSsplit.pl input_file.fa
#
# Output:
# One file for each sequence in the fasta, with name
# <sequence name>.fa
#

if (0 == scalar(@ARGV))
{
	usage();
	exit(0);
}

my $file = shift @ARGV;
my $extension = ".fa";

open FILE, $file or die "can't open $file";

while (my $line = <FILE>)
{
    if (not($line =~ /\S/))
    {
        next;
    }

    if ($line =~ /^>(\S+)/)
    {
        my $name = $1;
        close OUT;
        open OUT, ">$name$extension" or die "$name$extension\n";
    }
    print OUT $line;
}

sub usage
{
	print <<END;
GSsplit.pl <input_fasta_file>

Splits the given fasta file into its separate sequences, with file names
<sequence_name>.fa
END

}
