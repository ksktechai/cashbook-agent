# Build stage: package the Quarkus CLI with the Gradle wrapper.
FROM amazoncorretto:25 AS build
RUN yum install -y findutils && yum clean all
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon :cashbook-app:quarkusBuild -x test

# Runtime stage: JVM only, no network needed when using the Ollama classifier.
FROM amazoncorretto:25-headless
RUN yum install -y shadow-utils && useradd --system --uid 1001 cashbook && yum clean all
WORKDIR /app
COPY --from=build --chown=cashbook /src/cashbook-app/build/quarkus-app/ /app/
USER cashbook
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
