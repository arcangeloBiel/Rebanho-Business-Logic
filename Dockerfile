# Stage 1: Build Fat JAR
FROM gradle:8.11-jdk17 AS build
WORKDIR /app
COPY . .
RUN ./gradlew :server:buildFatJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/*all.jar app.jar
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
