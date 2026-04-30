FROM eclipse-temurin:25.0.2_10-jre-alpine@sha256:5fcc27581b238efbfda93da3a103f59e0b5691fe522a7ac03fe8057b0819c888

WORKDIR /app

# Create the plugwerk user and the artifact storage directory with correct ownership
# so the non-root user can write uploads. Matches the default PLUGWERK_STORAGE_ROOT
# in application.yml (/var/plugwerk/artifacts).
RUN addgroup -S plugwerk && adduser -S plugwerk -G plugwerk \
    && mkdir -p /var/plugwerk/artifacts \
    && chown -R plugwerk:plugwerk /var/plugwerk
USER plugwerk

COPY plugwerk-server-backend-*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
