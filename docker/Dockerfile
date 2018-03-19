FROM debian:latest
RUN apt-get update && \
    apt-get -y install tzdata && \
    apt-get -y install openjdk-8-jdk ocrmypdf tesseract-ocr-all && \
    echo "Etc/UTC" | tee /etc/timezone && dpkg-reconfigure --frontend noninteractive tzdata && \
    apt-get autoclean && \
    apt-get autoremove && \
    apt-get clean

RUN useradd -m java

USER java
ADD home/java/ /home/java/
WORKDIR /home/java/
CMD ["java", "-jar", "imap-pdf-ocr.jar"]