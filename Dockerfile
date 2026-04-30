FROM eclipse-temurin:25.0.2_10-jdk-alpine@sha256:d3f9f60ad2040582239e2977ee753d598787d8b064ca39a8e131860165dd81fb AS build

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

FROM eclipse-temurin:25.0.2_10-jre-alpine@sha256:5fcc27581b238efbfda93da3a103f59e0b5691fe522a7ac03fe8057b0819c888

WORKDIR /app

RUN addgroup -S plugwerk && adduser -S plugwerk -G plugwerk \
    && mkdir -p /var/plugwerk/artifacts \
    && chown -R plugwerk:plugwerk /var/plugwerk
USER plugwerk

COPY --from=build --chown=plugwerk:plugwerk /app/plugwerk-server/plugwerk-server-backend/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
