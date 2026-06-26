# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN for attempt in 1 2 3 4 5; do \
      mvn -B dependency:go-offline -DskipTests && break; \
      echo "Maven dependency:go-offline attempt ${attempt} failed, retry in 15s..."; \
      sleep 15; \
    done

COPY src ./src
COPY seed-payloads ./seed-payloads

ARG CACHEBUST=1
RUN JS_SIZE=$(wc -c < src/main/resources/static/js/report-detail.js) && \
    echo "report-detail.js bytes=${JS_SIZE}, cachebust=${CACHEBUST}" && \
    test "${JS_SIZE}" -gt 5000 || (echo "ERROR: report-detail.js looks stale (${JS_SIZE} bytes)" && exit 1) && \
    printf 'build=%s\nfeatures=screenshot\n' "${CACHEBUST}" > src/main/resources/static/build-id.txt && \
    for attempt in 1 2 3 4 5; do \
      mvn -B clean package -DskipTests && break; \
      echo "Maven package attempt ${attempt} failed, retry in 15s..."; \
      sleep 15; \
    done && \
    JAR_JS_SIZE=$(unzip -p target/report-web-app-*.jar BOOT-INF/classes/static/js/report-detail.js | wc -c) && \
    echo "report-detail.js in jar bytes=${JAR_JS_SIZE}" && \
    test "${JAR_JS_SIZE}" -gt 5000 || (echo "ERROR: stale report-detail.js in jar (${JAR_JS_SIZE} bytes)" && exit 1)

FROM eclipse-temurin:21-jre-jammy
ENV TZ=Europe/Moscow
WORKDIR /app

RUN useradd --system --uid 1000 --create-home appuser \
    && mkdir -p /app/data /app/backups \
    && chown -R appuser:appuser /app

COPY --from=build /app/target/report-web-app-*.jar /app/app.jar
USER appuser

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["java", "-Duser.timezone=Europe/Moscow", "-jar", "/app/app.jar"]
