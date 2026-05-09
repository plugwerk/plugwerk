FROM eclipse-temurin:21.0.11_10-jdk-alpine@sha256:4fb80de7aeb277ad949cfbe89b4f504e50bb34c57fd908c5825236473d71e986 AS build

WORKDIR /app

COPY gradle/ gradle/
COPY buildSrc/ buildSrc/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties* VERSION ./
COPY plugwerk-api/ plugwerk-api/
COPY plugwerk-spi/ plugwerk-spi/
COPY plugwerk-descriptor/ plugwerk-descriptor/
COPY plugwerk-server/ plugwerk-server/
COPY plugwerk-client-plugin/ plugwerk-client-plugin/

RUN apk add --no-cache bash nodejs npm && \
    chmod +x gradlew && \
    ./gradlew :plugwerk-server:plugwerk-server-backend:bootJar --no-daemon

FROM eclipse-temurin:21.0.11_10-jre-alpine@sha256:704db3c40204a44f471191446ddd9cda5d60dab40f0e15c6507b815ed897238b

WORKDIR /app

RUN addgroup -S plugwerk && adduser -S plugwerk -G plugwerk \
    && mkdir -p /var/plugwerk/artifacts \
    && chown -R plugwerk:plugwerk /var/plugwerk
USER plugwerk

COPY --from=build --chown=plugwerk:plugwerk /app/plugwerk-server/plugwerk-server-backend/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
