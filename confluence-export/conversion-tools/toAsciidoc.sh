#!/bin/bash

# PoC demonstration of complete migration from Confluence
# script to walk over pagetree of cleaned-up HTML pages from Confluence
# process html to asciidoc
# reconvert asciidoc to html


ASCIIDOC="converted2-asciidoc"

rm -fr build
mkdir build

for x in `find cleaned-export -name "*.html"`
do
    echo $x;
    FNAME=`echo ${x} | sed -e 's#cleaned-export/##'`
    echo "fname: $FNAME";
    DIRNAME=$(dirname ${FNAME})
    echo $DIRNAME;

    # a. convert to .adoc format using pandoc
    rm ${ASCIIDOC}/${FNAME%.*}.adoc
    pandoc cleaned-export/$FNAME -f html -t asciidoc -i --parse-raw --atx-headers --wrap=none -o ${ASCIIDOC}/${FNAME%.*}.adoc
    ls -l ${ASCIIDOC}/${FNAME%.*}.adoc


done
