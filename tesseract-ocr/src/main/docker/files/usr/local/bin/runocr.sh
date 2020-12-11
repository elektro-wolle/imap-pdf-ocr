#!/bin/bash
# runocr.sh $LANG $IN $OUT

set -e

OCR_LANG="$1"
OCR_INPUT="$2"
OCR_OUTPUT="$3"

TMPFILE=/tmp/tmp-$$.pdf
_cleanUp() {
  rm "${TMPFILE}"
}
trap _cleanUp EXIT

ocrmypdf -c -d -l "${OCR_LANG}" --tesseract-oem 1 --tesseract-pagesegmode 3 "${OCR_INPUT}" "${TMPFILE}"
ps2pdf -dPDFSETTINGS=/ebook -dLanguageLevel=4 "${TMPFILE}" "${OCR_OUTPUT}"
