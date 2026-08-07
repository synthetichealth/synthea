FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

ARG SYNTHEA_VERSION=docker

COPY gradlew gradlew.bat build.gradle settings.gradle ./
COPY gradle gradle
COPY config config
COPY lib lib
COPY src src

RUN printf '%s\n' "$SYNTHEA_VERSION" > src/main/resources/version.txt \
    && chmod +x gradlew \
    && ./gradlew --no-daemon shadowJar

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

ENV SYNTHEA_OUTPUT_DIR=/synthea-output

COPY --from=build /workspace/build/libs/*-with-dependencies.jar /app/synthea.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

RUN chmod +x /app/docker-entrypoint.sh \
    && mkdir -p "$SYNTHEA_OUTPUT_DIR"

VOLUME ["$SYNTHEA_OUTPUT_DIR"]

ENTRYPOINT ["/app/docker-entrypoint.sh"]
