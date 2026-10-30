FROM eclipse-temurin:21.0.13_9-jdk-alpine@sha256:b78a1d385da875588e21207f07dddf9a1418849971ae857fb24c8f3a7d441a72 AS build

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

FROM eclipse-temurin:21.0.13_9-jre-alpine@sha256:82cf3d9ccbe1ed46252008d51b2f2329e6b9e8a2f4394c4bebefc3ea7f4b1372

WORKDIR /app

RUN addgroup -S plugwerk && adduser -S plugwerk -G plugwerk \
    && mkdir -p /var/plugwerk/artifacts \
    && chown -R plugwerk:plugwerk /var/plugwerk
USER plugwerk

COPY --from=build --chown=plugwerk:plugwerk /app/plugwerk-server/plugwerk-server-backend/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
