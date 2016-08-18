#!/bin/bash

# PoC demonstration of complete migration from Confluence
# script to walk over pagetree of cleaned-up HTML pages from Confluence
# process html to asciidoc
# reconvert asciidoc to html

set -e
if [ -z $1 ]
then
  echo "Must specify a work dir on the command line"
  exit -1
fi

WORK_DIR=$1

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

PANDOC_TEMPLATE="$(dirname $0)/custom.pandoc.template"
if [ ! -e $PANDOC_TEMPLATE ]
then
    echo "$PANDOC_TEMPLATE does not exist"
    exit -1
fi
   
# function to use multiple times
convert_dir() {
    if [ -z "$1" ]
    then
	echo "convert_dir called w/o html dir"
	exit -1
    fi
    if [ -z "$2" ]
    then
	echo "convert_dir called w/o ascii dir"
	exit -1
    fi
    HTML_DIR=$1
    ASCII_DIR=$2

    rm -rf $ASCII_DIR
    
    for x in `find $HTML_DIR -name "*.html"`
    do
	echo $x;
	FNAME=`echo ${x} | sed -e "s#${HTML_DIR}/##"`
	DIRNAME=$(dirname ${FNAME})
	mkdir -p "$ASCII_DIR/$DIRNAME"
	
	# convert to .asciidoc format using pandoc
	pandoc $HTML_DIR/$FNAME -f html -t asciidoc -i --parse-raw --wrap=none --standalone --atx-headers --template=$PANDOC_TEMPLATE -o ${ASCII_DIR}/${FNAME%.*}.asciidoc
    done;
}


convert_dir "$WORK_DIR/cleaned-flat-export" "$WORK_DIR/cleaned-flat-asciidoc"
