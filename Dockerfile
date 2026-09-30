# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --uid 10001 --create-home loveos
COPY --from=build --chown=loveos:loveos /workspace/target/loveos-api.jar /app/loveos-api.jar

USER loveos
EXPOSE 4000

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Dfile.encoding=UTF-8"

ENTRYPOINT ["java", "-jar", "/app/loveos-api.jar"]