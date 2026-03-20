FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY plugwerk-api/ plugwerk-api/
COPY plugwerk-common/ plugwerk-common/
COPY plugwerk-descriptor/ plugwerk-descriptor/
COPY plugwerk-server/ plugwerk-server/
COPY plugwerk-client-sdk/ plugwerk-client-sdk/

RUN apk add --no-cache nodejs npm && \
    cd plugwerk-server/plugwerk-server-frontend && npm ci && cd ../.. && \
    chmod +x gradlew && \
    ./gradlew :plugwerk-server:plugwerk-server-backend:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S plugwerk && adduser -S plugwerk -G plugwerk
USER plugwerk

COPY --from=build --chown=plugwerk:plugwerk /app/plugwerk-server/plugwerk-server-backend/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
