# Multi-stage Dockerfile for scheduler-service
# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Cache dependencies
COPY pom.xml ./
COPY proto-module/pom.xml proto-module/pom.xml
COPY user-auth-service/pom.xml user-auth-service/pom.xml
COPY target-management-service/pom.xml target-management-service/pom.xml
COPY scheduler-service/pom.xml scheduler-service/pom.xml
COPY polling-worker-service/pom.xml polling-worker-service/pom.xml
COPY data-ingestion-service/pom.xml data-ingestion-service/pom.xml
COPY analytics-reporting-service/pom.xml analytics-reporting-service/pom.xml
RUN mvn -q -e -B -DskipTests dependency:go-offline

# Build only required modules
COPY . .
RUN mvn -q -B -DskipTests -pl scheduler-service -am package

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy built jar
COPY --from=builder /build/scheduler-service/target/*.jar /app/app.jar

ENV JAVA_OPTS=""
EXPOSE 8082
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
