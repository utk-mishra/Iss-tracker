# syntax=docker/dockerfile:1

# ---- Stage 1: build frontend (React/Vite) ----
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
# Frontend is added in Step 7; keep this stage resilient if it doesn't exist yet.
COPY frontend/package*.json ./
RUN if [ -f package.json ]; then npm ci; fi
COPY frontend/ ./
RUN if [ -f package.json ]; then npm run build; else mkdir -p dist; fi

# ---- Stage 2: build backend (Maven) ----
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend-build /app/frontend/dist ./src/main/resources/static
RUN mvn -q -B clean package -DskipTests

# ---- Stage 3: runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S iss && adduser -S iss -G iss
WORKDIR /app
COPY --from=backend-build /app/backend/target/iss-tracker.jar ./app.jar
USER iss
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
