FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN chmod +x gradlew

COPY src src

RUN ./gradlew buildFatJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl

COPY --from=build /app/build/libs/*-all.jar /app/app.jar
COPY docker/application.yaml /app/config/application.yaml

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=40s \
  CMD curl -fsS http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar", "-config=/app/config/application.yaml"]
