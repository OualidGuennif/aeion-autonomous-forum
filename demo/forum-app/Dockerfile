# syntax=docker/dockerfile:1
# Local-only forum image. The build context is the product repository root:
#   docker build -f demo/forum-app/Dockerfile -t aeion-autonomous-forum:local .
FROM gradle:9.8.2-jdk25 AS build
WORKDIR /workspace
COPY demo/forum-app/build.gradle demo/forum-app/settings.gradle demo/forum-app/
COPY demo/forum-app/src demo/forum-app/src
RUN gradle -p demo/forum-app --no-daemon clean build -x test

FROM eclipse-temurin:25-jre
RUN groupadd --system --gid 10001 forum \
    && useradd --system --uid 10001 --gid forum --home-dir /app --shell /usr/sbin/nologin forum \
    && mkdir -p /data && chown forum:forum /data
WORKDIR /app
COPY --from=build --chown=forum:forum /workspace/demo/forum-app/build/quarkus-app/ /app/
USER 10001:10001
ENV QUARKUS_HTTP_HOST=0.0.0.0 \
    QUARKUS_HTTP_PORT=8081 \
    AEION_SQLITE_PATH=/data/agora.db \
    AEION_RELEASE=local-docker
EXPOSE 8081
# The JRE image has no curl; a bash /dev/tcp probe keeps the runtime small and still checks HTTP.
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=12 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8081; printf 'GET /healthz HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3; head -n 1 <&3 | grep -q ' 200 '"]
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
