# Build environment in which to build the garage door app.
#
# This extends the Dockerfile from https://index.docker.io/u/wasabeef/android/

FROM wasabeef/android
MAINTAINER bradfitz <brad@danga.com>

# Found these from: android list sdk -u -e
RUN android list sdk -u -e | grep build-tools- | perl -npe 's/.+"(.+)"/$1/' > /tmp/build-tools-version
RUN perl -e 'die "No Android build tools version found." unless -s "/tmp/build-tools-version"'
RUN echo y | android update sdk -u -t $(cat /tmp/build-tools-version)
RUN echo y | android update sdk -u -t android-17

ENV ANDROID_HOME /usr/local/android-sdk-linux
ENV ANT_HOME /usr/local/apache-ant-1.9.2
ENV PATH $PATH:$ANDROID_HOME/tools
ENV PATH $PATH:$ANDROID_HOME/platform-tools
ENV PATH $PATH:$ANT_HOME/bin
ENV IN_DOCKER 1
