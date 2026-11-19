# syntax=docker/dockerfile:1
# Budowany z kontekstem w katalogu głównym repozytorium (potrzebne data/snapshots).

# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src/ src/
RUN ./mvnw -B -q package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --system domradar && useradd --system --gid domradar --home /app domradar
WORKDIR /app
COPY --from=build /workspace/target/domradar.jar app.jar
COPY data/snapshots/ data/snapshots/
RUN chown -R domradar:domradar /app
USER domradar
ENV SPRING_PROFILES_ACTIVE=prod \
    SNAPSHOTS_DIR=/app/data/snapshots \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
