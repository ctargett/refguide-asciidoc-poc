#!perl -i

use strict;
use warnings;

while (my $line = <>) {
    # fix up relative links (in place edit) -- NOTE: links to anchor in same page get '#' stripped
    $line =~ s{link:REL_LINK//#?(.*?)\[(.*?)\]}{\<\<$1,$2\>\>}g;

    # switch all images from inline to 'block' (double colon) and put on their own line of the file
    # TODO: any attributes we want to add to every image?
    $line =~ s{image:(.*?)\[(.*?)\]}{image::$1\[$2\]\n}g;

    print $line;
}
