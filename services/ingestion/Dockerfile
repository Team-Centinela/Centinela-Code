# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY services/pom.xml services/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -e -ntp -f services/pom.xml install -N

COPY services/shared-messaging/pom.xml services/shared-messaging/pom.xml
COPY services/shared-messaging/src services/shared-messaging/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -e -ntp -f services/shared-messaging/pom.xml install -DskipTests

COPY services/ingestion/pom.xml services/ingestion/pom.xml
COPY services/ingestion/src services/ingestion/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -e -ntp -f services/ingestion/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /build/services/ingestion/target/*.jar app.jar
EXPOSE 8081
ENV SPRING_PROFILES_ACTIVE=local-emulator
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
