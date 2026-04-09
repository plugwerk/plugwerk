FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY gradle/ gradle/
COPY buildSrc/ buildSrc/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY plugwerk-api/ plugwerk-api/
COPY plugwerk-spi/ plugwerk-spi/
COPY plugwerk-descriptor/ plugwerk-descriptor/
COPY plugwerk-server/ plugwerk-server/
COPY plugwerk-client-plugin/ plugwerk-client-plugin/

RUN apk add --no-cache bash nodejs npm && \
    chmod +x gradlew && \
    ./gradlew :plugwerk-server:plugwerk-server-backend:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S plugwerk && adduser -S plugwerk -G plugwerk
USER plugwerk

COPY --from=build --chown=plugwerk:plugwerk /app/plugwerk-server/plugwerk-server-backend/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
