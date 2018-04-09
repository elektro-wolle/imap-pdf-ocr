#!/bin/bash
# runocr.sh $LANG $IN $OUT

set -e

OCR_LANG="$1"
OCR_INPUT="$2"
OCR_OUTPUT="$3"

ocrmypdf -c -d -l "${OCR_LANG}" "${OCR_INPUT}"  "${OCR_OUTPUT}"
