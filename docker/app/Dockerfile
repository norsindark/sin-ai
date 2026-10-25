FROM eclipse-temurin:27_35-jdk AS jdk27

FROM eclipse-temurin:21.0.10_7-jdk AS build
COPY --from=jdk27 /opt/java/openjdk /opt/jdk27
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY build-logic build-logic
COPY app app
COPY modules modules
RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew :app:bootJar --no-daemon --console=plain \
        -Dorg.gradle.java.installations.paths=/opt/jdk27 \
        -Dorg.gradle.java.installations.auto-download=false \
    && find app/build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:27_35-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 10001 --create-home sinai
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER sinai
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
