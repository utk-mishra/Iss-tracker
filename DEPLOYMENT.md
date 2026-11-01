# ISS Tracker Deployment Guide

This document covers deployment options for the ISS Tracker application.

---

## 📋 Prerequisites

- **Docker** 20.10 or higher
- **Docker Compose** 2.0 or higher (for local development)
- **Java 21** (for local backend development)
- **Node 20** (for local frontend development)
- **Maven 3.9** (for local backend builds)

---

## 🚀 Quick Start (Docker)

The fastest way to run the application:

```bash
# Clone the repository
git clone https://github.com/your-org/iss-tracker.git
cd iss-tracker

# Build and run with Docker
docker build -t iss-tracker .
docker run -p 8080:8080 iss-tracker
```

Access the application at:
- **Frontend**: http://localhost:8080
- **API**: http://localhost:8080/api/v1/iss/overview?lat=28.6139&lon=77.209&offline=true
- **Health**: http://localhost:8080/actuator/health
- **API Docs**: http://localhost:8080/docs

---

## 🛠️ Local Development

### Using Docker Compose

Recommended for local development:

```bash
# Copy environment template
cp .env.example .env

# Edit .env with your configuration (optional, defaults work)
nano .env

# Start the application
docker-compose up

# Stop the application
docker-compose down
```

### Without Docker

#### Backend (Spring Boot)

```bash
cd backend

# Run tests
mvn clean verify

# Run the application
mvn spring-boot:run

# Or with a specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

#### Frontend (React + Vite)

```bash
cd frontend

# Install dependencies
npm ci

# Run tests
npm test

# Start dev server (proxies /api to backend)
npm run dev

# Build for production
npm run build
```

The frontend dev server runs on http://localhost:5173 and proxies `/api` requests to the backend at http://localhost:8080.

---

## 🌍 Environment Configuration

### Spring Boot Profiles

The application supports three profiles:

- **`dev`** (default): Development settings, detailed logging
- **`staging`**: Pre-production environment
- **`prod`**: Production settings, minimal logging

Set via environment variable:

```bash
export SPRING_PROFILES_ACTIVE=prod
```

or in `docker-compose.yml`:

```yaml
environment:
  - SPRING_PROFILES_ACTIVE=prod
```

### Configuration Files

Environment-specific configuration is in:

- `backend/src/main/resources/application.yml` (base)
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/resources/application-staging.yml`
- `backend/src/main/resources/application-prod.yml`

### Environment Variables

All configuration can be overridden via environment variables. See [`.env.example`](.env.example) for the complete list.

Key variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8080` | Server port (MUST be 8080 by default) |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile |
| `POLLUX_PASS_COUNT` | `20` | Number of pass predictions to fetch |
| `RATE_LIMIT_PER_MINUTE` | `120` | API rate limit per client IP |
| `CORS_ALLOWED_ORIGINS` | (empty) | Comma-separated allowed origins |
| `TRUSTED_PROXY` | `false` | Trust X-Forwarded-For header |
| `UPSTREAM_API_KEY` | (empty) | API key (currently unused) |

---

## 🏗️ CI/CD

### GitHub Actions

The repository includes a complete CI/CD pipeline in [`.github/workflows/ci.yml`](.github/workflows/ci.yml):

**On every push/PR:**
1. Backend tests (Maven)
2. Frontend tests (Vitest)
3. Docker build
4. Trivy security scan
5. Container smoke test

**On push to `main` only:**
6. Push image to container registry

**Required GitHub Secrets:**

- `DOCKER_REGISTRY`: Registry URL (e.g., `docker.io`, `ghcr.io`)
- `DOCKER_USERNAME`: Registry username
- `DOCKER_PASSWORD`: Registry password or token

### Jenkins

The repository includes a Jenkinsfile with the following stages:

1. **Checkout**: Clone the repository
2. **Backend Tests**: Run Maven tests, publish JaCoCo coverage
3. **Frontend Tests**: Run Vitest tests
4. **Build Docker Image**: Multi-stage build
5. **Security Scan**: Trivy vulnerability scan
6. **Push to Registry**: Push to Docker registry (main branch only)
7. **Deploy**: Deploy to dev/staging/prod (configurable)

**Required Jenkins Credentials:**

- `docker-registry-url`: Docker registry URL (secret text)
- `docker-registry-credentials`: Username/password
- `github-credentials`: GitHub username/token
- `deploy-dev-credentials`: Dev deployment SSH credentials
- `deploy-staging-credentials`: Staging deployment SSH credentials
- `deploy-prod-credentials`: Production deployment SSH credentials

**Jenkins Setup Options:**

1. **XML Import**: Import [`jenkins/job-config.xml`](jenkins/job-config.xml) via Jenkins UI
2. **JCasC**: Use [`jenkins/jcasc.yml`](jenkins/jcasc.yml) with the Configuration as Code plugin

---

## 🔒 Security Notes

### API Keys

Currently, none of the three upstream APIs (Open-Notify, Pollux Labs, Open-Meteo) require authentication. However, the application is designed to support API keys:

- Set `UPSTREAM_API_KEY` environment variable
- The key is sent as a header to upstream APIs
- Never logged or exposed in error messages
- Redacted in logs

See `UpstreamProperties.java` for implementation details.

### Open-Notify HTTP Risk

The Open-Notify API uses plain HTTP (not HTTPS). This is a known limitation of the free service. The application:

- Does NOT send any sensitive data to this API
- Falls back to cached or mock data if the API is unavailable
- Validates and sanitizes all responses

See README.md section "Trust and Accuracy" for details.

### Secrets Management

**Never commit secrets to the repository:**

- `.env` files are in `.gitignore`
- Use Docker secrets, Kubernetes secrets, or environment variables in production
- Jenkins credentials via `withCredentials`
- GitHub secrets for Actions

---

## 🐳 Docker Details

### Multi-Stage Build

The Dockerfile uses three stages:

1. **Frontend Build** (`node:20-alpine`): Builds React app with Vite
2. **Backend Build** (`maven:3.9-eclipse-temurin-21`): Builds Spring Boot JAR, includes frontend build in `static/`
3. **Runtime** (`eclipse-temurin:21-jre-alpine`): Minimal JRE image, non-root user

### Image Size

Expected image size: ~300-350 MB (JRE + compiled JAR)

### Healthcheck

The container includes a built-in healthcheck:

```bash
wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"'
```

Runs every 30 seconds, 3 retries, 20-second startup grace period.

---

## 🧪 Testing the Deployment

### Smoke Test

```bash
# Start container
docker run -d --name iss-test -p 8080:8080 iss-tracker

# Wait for startup
sleep 20

# Test health endpoint
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# Test API (offline mode, returns mock data)
curl "http://localhost:8080/api/v1/iss/overview?lat=0&lon=0&offline=true"
# Expected: JSON with position, passes, alerts, meta

# Test frontend
curl http://localhost:8080
# Expected: HTML with React app

# Clean up
docker stop iss-test && docker rm iss-test
```

### Full Integration Test

```bash
# Run all tests
cd backend && mvn verify && cd ..
cd frontend && npm test && cd ..

# Build and test Docker image
docker build -t iss-tracker .
docker run -d --name iss-tracker -p 8080:8080 iss-tracker
sleep 20
curl -f http://localhost:8080/actuator/health || exit 1
docker stop iss-tracker && docker rm iss-tracker
```

---

## 🚨 Troubleshooting

### Container won't start

1. Check logs: `docker logs iss-tracker`
2. Verify port 8080 is not already in use: `lsof -i :8080`
3. Check Java version in container: `docker run iss-tracker java -version` (should be 21)

### Frontend not loading

1. Verify build copied to `backend/src/main/resources/static/`:
   ```bash
   docker run --rm iss-tracker ls -la /app/app.jar
   ```
2. Check Spring Boot serves static resources (it should by default)

### API returns 5xx errors

1. Check logs for stack traces
2. Verify all three upstream APIs are reachable
3. Test with `offline=true` to bypass upstream calls

### Rate limiting (429)

Default is 120 requests/minute per IP. Increase with:

```bash
docker run -e RATE_LIMIT_PER_MINUTE=300 -p 8080:8080 iss-tracker
```

---

## 📚 Additional Resources

- [PROMPT.md](PROMPT.md): Full project requirements
- [README.md](README.md): Architecture and design decisions (Step 9)
- [openapi.yaml](backend/src/main/resources/openapi.yaml): API specification
- [docs/patterns.md](docs/patterns.md): Design patterns used (Step 9)
- [docs/sequence.drawio](docs/sequence.drawio): Sequence diagrams (Step 9)

---

## 📄 License

See [LICENSE](LICENSE) file.
