FROM gradle:8.11.1-jdk17

WORKDIR /workspace

COPY docker/build.sh /usr/local/bin/build-project
RUN chmod +x /usr/local/bin/build-project

ENTRYPOINT ["build-project"]
