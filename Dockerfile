# Build stage: package the Quarkus CLI with the Gradle wrapper.
FROM amazoncorretto:25 AS build
RUN yum install -y findutils && yum clean all
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon :cashbook-app:quarkusBuild -x test

# Runtime stage: JVM plus Tesseract for OCR of scanned documents. Alpine because Amazon Linux 2023 does
# not package Tesseract. No network is needed at runtime when using the Ollama classifier.
FROM amazoncorretto:25-alpine
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng gcompat \
    && adduser -S -u 1001 cashbook
WORKDIR /app
COPY --from=build --chown=cashbook /src/cashbook-app/build/quarkus-app/ /app/
USER cashbook
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/quarkus-run.jar"]
