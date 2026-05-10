# Spring Boot server (`:server`) from the Gradle multi-module root.
#
# Build (from repo root):
#   docker build -t battalion-server .
#
# Run (mount a volume if you want uploaded maps to survive container removal):
#   docker run --rm -p 8080:8080 -v battalion-maps:/app/shared-maps battalion-server

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY protocol ./protocol
COPY game-core ./game-core
COPY server ./server

RUN chmod +x gradlew \
    && ./gradlew :server:bootJar --no-daemon \
    && jar="$(find server/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$jar" \
    && cp "$jar" /app/application.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system --gid 1000 spring \
    && useradd --system --uid 1000 --gid spring --home-dir /app spring

COPY --from=build --chown=spring:spring /app/application.jar /app/application.jar
RUN chown spring:spring /app

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
