#!/bin/bash

rm -rf pdf/*.pdf
rm -rf pdf/*.pdfmarks

asciidoctor-pdf -a source-highlighter=coderay -a icons=font -a imagesDir=images -a pdf-stylesDir=pdf/themes -a pdf-style=refguide -a pdf-fontsDir=pdf/fonts -a pagenums -a figure-caption! -D ./pdf -o SolrRefGuide.pdf asciidoc/book.asc
