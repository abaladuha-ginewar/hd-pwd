# 多阶段构建：Gradle 产出 Wasm 发行物，nginx 静态托管供本机浏览器访问。
# Kotlin/Wasm 发行目录随插件可能变化，构建后按 index.html + .wasm 定位再拷贝。
FROM gradle:8.11.1-jdk17 AS build

USER root
WORKDIR /workspace
COPY . /workspace
RUN chown -R gradle:gradle /workspace

USER gradle

ARG HTTP_PROXY=http://http.docker.internal:3128
ARG HTTPS_PROXY=http://http.docker.internal:3128
ARG NO_PROXY=localhost,127.0.0.1
ARG GRADLE_OPTS=-Dhttp.proxyHost=http.docker.internal -Dhttp.proxyPort=3128 -Dhttps.proxyHost=http.docker.internal -Dhttps.proxyPort=3128

ENV HTTP_PROXY=${HTTP_PROXY}
ENV HTTPS_PROXY=${HTTPS_PROXY}
ENV NO_PROXY=${NO_PROXY}
ENV GRADLE_OPTS=${GRADLE_OPTS}

RUN gradle --no-daemon --stacktrace :webApp:wasmJsBrowserDistribution \
    && DIST_DIR="$(find /workspace/webApp/build -type f -name index.html \
        | while read -r html; do \
            dir=$(dirname "$html"); \
            if ls "$dir"/*.wasm >/dev/null 2>&1; then echo "$dir"; break; fi; \
          done)" \
    && test -n "$DIST_DIR" \
    && mkdir -p /workspace/web-dist \
    && cp -a "$DIST_DIR"/. /workspace/web-dist/

FROM nginx:alpine

COPY docker/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/web-dist/ /usr/share/nginx/html/

EXPOSE 8080
