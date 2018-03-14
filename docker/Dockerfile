FROM debian:latest
RUN apt-get update && \
    apt-get -y install sudo gpg apt-transport-https software-properties-common ca-certificates tzdata && \
    add-apt-repository -y ppa:webupd8team/java && \
    apt-key adv --keyserver keyserver.ubuntu.com --recv-keys C2518248EEA14886 && \
    echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
    echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections && \
    apt-get update && \
    apt-get -y install oracle-java8-installer oracle-java8-unlimited-jce-policy ocrmypdf tesseract-ocr-deu && \
    echo "Etc/UTC" | tee /etc/timezone && dpkg-reconfigure --frontend noninteractive tzdata && \
    rm -r /var/cache/oracle-jdk8-installer && \
    ln -s /usr/lib/jvm/java-8-oracle/ /opt/jdk && \
    rm -rf /opt/jdk/*src.zip \
           /opt/jdk/lib/missioncontrol \
           /opt/jdk/lib/visualvm \
           /opt/jdk/lib/*javafx* \
           /opt/jdk/jre/lib/plugin.jar \
           /opt/jdk/jre/lib/ext/jfxrt.jar \
           /opt/jdk/jre/bin/javaws \
           /opt/jdk/jre/lib/javaws.jar \
           /opt/jdk/jre/lib/desktop \
           /opt/jdk/jre/plugin \
           /opt/jdk/jre/lib/deploy* \
           /opt/jdk/jre/lib/*javafx* \
           /opt/jdk/jre/lib/*jfx* \
           /opt/jdk/jre/lib/amd64/libdecora_sse.so \
           /opt/jdk/jre/lib/amd64/libprism_*.so \
           /opt/jdk/jre/lib/amd64/libfxplugins.so \
           /opt/jdk/jre/lib/amd64/libglass.so \
           /opt/jdk/jre/lib/amd64/libgstreamer-lite.so \
           /opt/jdk/jre/lib/amd64/libjavafx*.so \
           /opt/jdk/jre/lib/amd64/libjfx*.so && \
    apt-get autoclean && \
    apt-get autoremove && \
    locale-gen en_US.UTF-8

RUN useradd -m java
ADD home/java/ /home/java/

WORKDIR /home/java/
CMD ["java", "-jar", "imap-pdf-ocr.jar"]