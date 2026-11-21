FROM eclipse-temurin:21.0.13.1_2-jdk-alpine@sha256:c07bb82e004a166b5d3d63b84ad70e2e822f228505e2e0c0b8dfdc2890d22f13 AS build

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

FROM eclipse-temurin:21.0.13.1_2-jre-alpine@sha256:5d7689f47d670aca766b9503a70bccc2d73b4eb9221ae8f714ae958313c4ce70

WORKDIR /app

RUN addgroup -S plugwerk && adduser -S plugwerk -G plugwerk \
    && mkdir -p /var/plugwerk/artifacts \
    && chown -R plugwerk:plugwerk /var/plugwerk
USER plugwerk

COPY --from=build --chown=plugwerk:plugwerk /app/plugwerk-server/plugwerk-server-backend/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
