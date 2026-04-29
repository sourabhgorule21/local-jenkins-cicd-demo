# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/target/*.jar /app/app.jar

EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
