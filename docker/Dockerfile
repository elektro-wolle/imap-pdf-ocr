FROM debian:latest
LABEL maintainer="docker@elektrowolle.de"

RUN apt-get update && \
    apt-get -y install openjdk-8-jdk ocrmypdf tesseract-ocr-all && \
    apt-get autoclean && \
    apt-get autoremove && \
    apt-get clean

RUN useradd -m java

USER java
ADD home/java/ /home/java/
WORKDIR /home/java/
CMD ["java", "-jar", "imap-pdf-ocr.jar"]