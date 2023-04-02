FROM docker.wolle.dev/ubuntu:bionic
LABEL maintainer="docker@elektrowolle.de"

RUN apt-get update && \
    apt-get -y upgrade && \
    apt-get install -y openjdk-17-jre-headless ca-certificates-java && \
    apt-get --no-install-recommends -y install ocrmypdf tesseract-ocr-deu imagemagick ghostscript unpaper wget  && \
    apt-get autoclean && \
    apt-get autoremove && \
    apt-get clean

ARG LANG="deu eng"
RUN for x in $LANG; do \
       wget -q https://github.com/tesseract-ocr/tessdata_best/blob/main/${x}.traineddata\?raw=true \
            -O /usr/share/tesseract-ocr/4.00/tessdata/${x}.traineddata; \
    done

ADD files/ /
RUN useradd -m -d /app java && \
    chown java:java /app && \
    chmod 755 /usr/local/bin/*.sh
ENV LC_ALL=C.UTF-8 LANG=C.UTF-8