# Production image for the PrathibaLanka API (Spring Boot 4, Java 21).
#
#   docker build -t prathibalanka-api .
#   docker run -p 8080:8080 \
#     -e JWT_SECRET=<at least 32 characters> \
#     -e DB_URL=jdbc:postgresql://host:5432/railway \
#     -e DB_USERNAME=... -e DB_PASSWORD=... \
#     -v prathiba-media:/data \
#     prathibalanka-api
#
# The image starts with SPRING_PROFILES_ACTIVE=prod, which removes the default JWT secret and the
# default media directory: the app refuses to boot without JWT_SECRET rather than running on a key
# that is published in the repository.

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# The pom is copied on its own so the dependency download is cached in its own layer: editing a
# source file then rebuilds only the compile step, not the whole Maven repository.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# Tests are skipped here on purpose - they are run by CI, and a build that needs a database cannot
# run during an image build. -DskipTests still compiles them so the image build catches breakage.
RUN mvn -B -ntp clean package -DskipTests

# ---------- run ----------
FROM eclipse-temurin:21-jre-alpine

# busybox wget (used by HEALTHCHECK below) is already in the base image. tzdata makes the log
# timestamps read as Sri Lanka time instead of UTC, which is what the agency reads them in.
RUN apk add --no-cache tzdata

# Run as an unprivileged user: nothing in this container needs root after the copy below.
ARG APP_UID=10001
RUN addgroup -g "${APP_UID}" -S app \
 && adduser -u "${APP_UID}" -S -G app app

WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar

# MEDIA_DIR has to exist and be writable before the volume is mounted over it, otherwise Docker
# creates it owned by root and every upload fails. /data is the mount point Railway volumes use.
RUN mkdir -p /data/uploads && chown -R app:app /data /app

USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    MEDIA_DIR=/data/uploads \
    PORT=8080 \
    TZ=Asia/Colombo \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# The app's own health endpoint, which reports UP only once the datasource answers.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/actuator/health" || exit 1

# exec so the JVM is PID 1 and receives the platform's SIGTERM directly: without it the container
# is killed after the grace period instead of shutting down gracefully.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
