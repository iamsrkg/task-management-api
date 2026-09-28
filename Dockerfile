# Stage 1: build with the full JDK + Maven
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Dependencies first, so this layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: small runtime image with just the JRE and the jar
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Don't run as root
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /app/target/task-management-api-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# Respect the container's memory limit
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
