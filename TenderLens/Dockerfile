# ── Build stage ───────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Run stage ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tesseract OCR + English language data
RUN apk add --no-cache \
    curl \
    tesseract-ocr \
    tesseract-ocr-data-eng

# Report output directory
RUN mkdir -p /tmp/tenderlens/reports

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
