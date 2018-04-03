#!/bin/bash
# runocr.sh $LANG $IN $OUT

set -e

TMPFILE_IM_OUT=/tmp/im-$$-output.pdf
TMPFILE_SCAN_OUT=/tmp/scan-$$-output.pdf

_cleanup() {
  rm -f "${TMPFILE_IM_OUT}"
  rm -f "${TMPFILE_SCAN_OUT}"
}

trap _cleanup EXIT

OCR_LANG="$1"
OCR_INPUT="$2"
OCR_OUTPUT="$3"

cat "${OCR_INPUT}" | convert -density 300  - "${TMPFILE_IM_OUT}"
ocrmypdf -c -d -l "${OCR_LANG}" "${TMPFILE_IM_OUT}"  "${TMPFILE_SCAN_OUT}"
ps2pdf14 -dPDFSETTINGS=/ebook "${TMPFILE_SCAN_OUT}" "${OCR_OUTPUT}"
