# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Слой зависимостей кэшируется, пока не меняется pom.xml.
COPY pom.xml .
RUN for attempt in 1 2 3 4 5; do \
      mvn -B dependency:go-offline -DskipTests && break; \
      echo "Maven dependency:go-offline attempt ${attempt} failed, retry in 15s..."; \
      sleep 15; \
    done

COPY src ./src
COPY seed-payloads ./seed-payloads

# CACHEBUST: сбрасывает только финальную сборку (без повторной загрузки pom-зависимостей).
# Увеличьте CACHEBUST в env Timeweb или задеплойте новый коммит.
ARG CACHEBUST=1
RUN echo "cachebust=${CACHEBUST}" && \
    for attempt in 1 2 3 4 5; do \
      mvn -B package -DskipTests && break; \
      echo "Maven package attempt ${attempt} failed, retry in 15s..."; \
      sleep 15; \
    done

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
