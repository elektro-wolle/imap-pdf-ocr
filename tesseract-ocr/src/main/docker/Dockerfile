FROM docker.wolle.dev/ubuntu:mantic
LABEL maintainer="docker@elektrowolle.de"

RUN export DEBIAN_FRONTEND=noninteractive && \
    export TZ=Etc/UTC && \
    apt-get update && \
    apt-get -y upgrade && \
    apt-get -y install tzdata openjdk-17-jre-headless ca-certificates-java && \
    apt-get --no-install-recommends -y install ocrmypdf tesseract-ocr-deu tesseract-ocr-eng imagemagick ghostscript unpaper wget  && \
    apt-get autoclean && \
    apt-get autoremove && \
    apt-get clean

ADD files/ /
RUN useradd -m -d /app java && \
    chown java:java /app && \
    chmod 755 /usr/local/bin/*.sh
ENV LC_ALL=C.UTF-8 LANG=C.UTF-8
