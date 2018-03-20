FROM debian:sid
LABEL maintainer="docker@elektrowolle.de"

RUN apt-get update && \
    apt-get -y install openjdk-8-jre ocrmypdf tesseract-ocr-deu && \
    apt-get autoclean && \
    apt-get autoremove && \
    apt-get clean

RUN apt-get -y install tesseract-ocr-all

RUN useradd -m java
USER java
ADD home/java/ /home/java/

WORKDIR /home/java/
ENV LC_ALL=C.UTF-8 LANG=C.UTF-8
CMD ["java", "-jar", "imap-pdf-ocr.jar"]