FROM eclipse-temurin:27-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:27-jre
WORKDIR /app
RUN useradd --system --no-create-home sinai
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
USER sinai
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
