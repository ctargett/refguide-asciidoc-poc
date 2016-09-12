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

    
    # TODO: purge ======= lines
    
    # fix up relative links (in place edit) -- NOTE: links to anchor in same page get '#' stripped
    $line =~ s{link:REL_LINK//#?(.*?)\[(.*?)\]}{\<\<$1,$2\>\>}g;

    # switch all images from inline to 'block' (double colon) and put on their own line of the file
    # TODO: any attributes we want to add to every image?
    $line =~ s{image:(.*?)\[(.*?)\]}{image::$1\[$2\]\n}g;

    print $line;
}
