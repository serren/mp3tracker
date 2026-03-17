# --- Stage 1: Build ---
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy POM first — cached as a separate layer
# Dependencies are only re-downloaded when pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source separately to preserve the dependency cache layer
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Wildcard avoids hardcoding the artifact version from pom.xml
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8082

CMD ["java", "-jar", "app.jar"]
