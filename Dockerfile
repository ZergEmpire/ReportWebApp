# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY seed-payloads ./seed-payloads
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --uid 1000 --create-home appuser \
    && mkdir -p /app/data /app/backups \
    && chown -R appuser:appuser /app

COPY --from=build /app/target/report-web-app-*.jar /app/app.jar
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
