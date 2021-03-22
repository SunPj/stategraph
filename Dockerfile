# AlpineLinux with a glibc-2.23, Oracle Java 8, sbt and git
FROM anapsix/alpine-java:8_jdk

# sbt

ENV SBT_URL=https://dl.bintray.com/sbt/native-packages/sbt
ENV SBT_VERSION 1.3.13
ENV INSTALL_DIR /usr/local
ENV SBT_HOME /usr/local/sbt
ENV PATH ${PATH}:${SBT_HOME}/bin

# Install sbt
RUN apk add --no-cache --update bash wget && mkdir -p "$SBT_HOME" && \
    wget -qO - --no-check-certificate "https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz" |  tar xz -C $INSTALL_DIR && \
    echo -ne "- with sbt $SBT_VERSION\n" >> /root/.built

# Copy play project and compile it
# This will download all the ivy2 and sbt dependencies and install them
# in the container /root directory


RUN mkdir /app
COPY . /app
WORKDIR /app
RUN sbt compile

# Command

CMD ["sbt"]

# Expose code volume and play port 9000

EXPOSE 9000

# EOF