FROM gradle:8.11.1-jdk17

USER root
RUN apt-get update \
    && apt-get install --no-install-recommends -y fakeroot dpkg-dev binutils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY docker/build.sh /usr/local/bin/build-project
RUN chmod +x /usr/local/bin/build-project

ENTRYPOINT ["build-project"]
