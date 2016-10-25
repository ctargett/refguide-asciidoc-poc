#!perl -i

use strict;
use warnings;

while (my $line = <>) {
    # pandoc uses '=========...' syntax for doc title, we want shorter "= TITLE" syntax
    if (1 == $.) {
	$line = "= $line";
    } elsif ((2 == $.) && $line =~ /^=+$/) {
	next; # skip this line completley
    }

    # table syntax doesn't need to be so verbose
    $line =~ s{^\|\={3,}+$}{|===};
	
    # fix up relative links (in place edit) -- NOTE: links to anchor in same page get '#' stripped
    $line =~ s{link:REL_LINK//#?(.*?)\[(.*?)\]}{\<\<$1,$2\>\>}g;

    # fix up javadoc links, since pandoc escapes our attribute syntax
    $line =~ s<link:%7B(.*?)%7D><{$1}>g;

    # switch all images from inline to 'block' (double colon) and put on their own line of the file
    # TODO: any attributes we want to add to every image?
    $line =~ s{image:(.*?)\[(.*?)\]}{image::$1\[$2\]\n}g;

    # admonishments...
    if ($line =~ s{^TODO_ADMON_TITLE:}{.}) {
	# next line should be blank, trash it
	my $trash = <>;
	$trash =~ /^$/ or die "not a blank trash line: $trash";
    }
    $line =~ s{^(\[\w+\])====$}{$1\n====};

    # fixup obviously intended quoted code (otherwise "`foo`" just gets curly quoted)
    $line =~ s{"`(\w+)`"}{"```$1```"}g;
    
    print $line;
}
