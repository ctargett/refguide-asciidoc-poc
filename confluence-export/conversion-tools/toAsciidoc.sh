#!/bin/bash

# PoC demonstration of complete migration from Confluence
# script to walk over pagetree of cleaned-up HTML pages from Confluence
# process html to asciidoc
# reconvert asciidoc to html


# parent dir of script until/unless we move it
WORK_DIR=$(realpath -L "$(dirname $0)/../")

if [ ! -d $WORK_DIR ]
then
    echo "$WORK_DIR does not exist (as a directory)"
    exit -1
fi

# check that we have the expected version of pandoc
PANDOC_VER=`pandoc --version | head -1 | cut -d' ' -f 2 | cut -d'.' -f 1-2`
if [ $PANDOC_VER != "1.17" ]
then
    echo "Only tested with pandoc 1.17, you are using $PANDOC_VER"
    exit -1
fi

PANDOC_TEMPLATE="$WORK_DIR/conversion-tools/custom.pandoc.template"
if [ ! -e $PANDOC_TEMPLATE ]
then
    echo "$PANDOC_TEMPLATE does not exist"
    exit -1
fi

HTML_DIR="$WORK_DIR/cleaned-export"
ASCII_DIR="$WORK_DIR/../solr-ref-guide/src"

rm $ASCII_DIR/*.adoc

echo "Coping images..."
rm -rf $ASCII_DIR/images
cp -r $HTML_DIR/images $ASCII_DIR/images

for x in `find $HTML_DIR -name "*.html"`
do
    echo $x;
    FNAME=`echo ${x} | sed -e "s#${HTML_DIR}/##"`
    DIRNAME=$(dirname ${FNAME})
    mkdir -p "$ASCII_DIR/$DIRNAME"
    
    # convert to .asciidoc format using pandoc
    pandoc $HTML_DIR/$FNAME -f html -t asciidoc -i --parse-raw --wrap=none --standalone --atx-headers --template=$PANDOC_TEMPLATE -o ${ASCII_DIR}/${FNAME%.*}.adoc

    perl "$WORK_DIR/conversion-tools/post-process-adocs.pl" ${ASCII_DIR}/${FNAME%.*}.adoc
done;
