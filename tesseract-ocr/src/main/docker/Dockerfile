FROM ubuntu:latest
LABEL maintainer="docker@elektrowolle.de"

RUN apt-get update && \
    apt-get -y install openjdk-11-jre ocrmypdf tesseract-ocr-deu imagemagick ghostscript wget && \
    apt-get autoclean && \
    apt-get autoremove && \
    apt-get clean

ARG LANG="deu eng"
RUN for x in $LANG; do \
       wget -q https://github.com/tesseract-ocr/tessdata_best/blob/master/${x}.traineddata\?raw=true \
            -O /usr/share/tesseract-ocr/4.00/tessdata/${x}.traineddata; \
    done

ADD files/ /
RUN useradd -m -d /app java && \
    chown java:java /app && \
    chmod 755 /usr/local/bin/*.sh
ENV LC_ALL=C.UTF-8 LANG=C.UTF-8
