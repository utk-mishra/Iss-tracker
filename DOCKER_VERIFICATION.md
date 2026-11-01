# Docker Build Verification Checklist

**Status**: Docker daemon not running during Step 8 implementation.

This document lists the verification steps that must be completed when Docker is available.

---

## ✅ Verification Steps

### 1. Build the Docker Image

```bash
cd /Users/utkarshmishra/Desktop/Iss-tracker
docker build -t iss-tracker .
```

**Expected**:
- ✅ Frontend stage completes (npm ci, npm run build)
- ✅ Backend stage completes (mvn package)
- ✅ Runtime stage creates final image
- ✅ Image size ~300-350 MB
- ✅ No build errors

### 2. Run the Container

```bash
docker run -d --name iss-tracker-test -p 8080:8080 iss-tracker
```

**Expected**:
- ✅ Container starts successfully
- ✅ No immediate crashes (check with `docker ps`)

### 3. Wait for Startup

```bash
sleep 20
```

The healthcheck starts after 20 seconds (configured in Dockerfile).

### 4. Test Health Endpoint

```bash
curl http://localhost:8080/actuator/health
```

**Expected**:
```json
{"status":"UP"}
```

### 5. Test API (Offline Mode)

```bash
curl "http://localhost:8080/api/v1/iss/overview?lat=0&lon=0&offline=true"
```

**Expected**:
- ✅ Returns JSON with:
  - `position` object
  - `passes` array
  - `nextVisiblePass` object (if any)
  - `alerts` array
  - `meta` object with `source: "MOCK"` for all three upstreams
  - `_links` object

### 6. Test Frontend

```bash
# Test index.html is served
curl http://localhost:8080 | grep -q "ISS Location"

# Test in browser
open http://localhost:8080
```

**Expected**:
- ✅ HTML page loads
- ✅ React app renders
- ✅ No console errors (check browser DevTools)
- ✅ Location form is visible
- ✅ Offline toggle is visible
- ✅ "Use these coordinates" button works
- ✅ Offline mode shows MOCK data with red "Fallback data" badges

### 7. Test API Docs

```bash
curl http://localhost:8080/docs | grep -q "ISS Tracker API"
open http://localhost:8080/docs
```

**Expected**:
- ✅ Documentation page loads
- ✅ OpenAPI spec is visible

### 8. Test Healthcheck

```bash
docker inspect iss-tracker-test --format='{{.State.Health.Status}}'
```

**Expected**: `healthy` (after ~30 seconds)

### 9. Check Logs

```bash
docker logs iss-tracker-test
```

**Expected**:
- ✅ No ERROR level messages
- ✅ "Started IssTrackerApplication" message present
- ✅ Tomcat started on port 8080

### 10. Test with Live APIs (Online Mode)

```bash
curl "http://localhost:8080/api/v1/iss/overview?lat=28.6139&lon=77.209&offline=false"
```

**Expected**:
- ✅ Returns JSON with `source: "FRESH"` for all three upstreams (if APIs are up)
- ✅ Real ISS position data
- ✅ Real pass predictions
- ✅ Cloud cover data

### 11. Test Error Handling

```bash
# Invalid latitude
curl "http://localhost:8080/api/v1/iss/overview?lat=999&lon=0&offline=false"
```

**Expected**: 400 Bad Request with error message

### 12. Test Rate Limiting

```bash
# Send 121+ requests rapidly
for i in {1..125}; do
  curl -w "\n" "http://localhost:8080/api/v1/iss/overview?lat=0&lon=0&offline=true"
done
```

**Expected**: 429 Too Many Requests after 120 requests, with `Retry-After` header

### 13. Clean Up

```bash
docker stop iss-tracker-test
docker rm iss-tracker-test
```

---

## 🧪 Docker Compose Verification

```bash
# Copy environment template
cp .env.example .env

# Start with docker-compose
docker-compose up -d

# Check status
docker-compose ps

# Follow logs
docker-compose logs -f

# Test (same as above)
curl http://localhost:8080/actuator/health

# Stop
docker-compose down
```

**Expected**:
- ✅ Service starts successfully
- ✅ Healthcheck passes
- ✅ All endpoints work

---

## 🔍 Dockerfile Inspection

### Verify Multi-Stage Build Artifacts

```bash
# Check frontend dist was copied
docker run --rm iss-tracker ls -la /app/app.jar

# Verify non-root user
docker run --rm iss-tracker whoami
# Expected: iss

# Verify Java version
docker run --rm iss-tracker java -version
# Expected: openjdk version "21.x.x"

# Verify exposed port
docker inspect iss-tracker | grep ExposedPorts
# Expected: "8080/tcp"
```

---

## 📊 Performance Check

### Image Size

```bash
docker images iss-tracker
```

**Expected**: ~300-350 MB

### Startup Time

```bash
time docker run --rm -p 8080:8080 iss-tracker &
sleep 1
curl --retry 30 --retry-delay 1 http://localhost:8080/actuator/health
```

**Expected**: Ready within 20 seconds

### Memory Usage

```bash
docker stats iss-tracker-test --no-stream
```

**Expected**: <512 MB RAM under normal load

---

## ✅ Definition of Done (from PROMPT.md)

- [ ] `docker build -t iss-tracker .` succeeds
- [ ] `docker run -p 8080:8080 iss-tracker` starts successfully
- [ ] Container serves UI at http://localhost:8080
- [ ] Container serves API at http://localhost:8080/api/v1/iss/overview?lat=0&lon=0&offline=true
- [ ] Healthcheck returns `{"status":"UP"}`
- [ ] Frontend loads without console errors
- [ ] Offline toggle works
- [ ] MOCK data is labeled correctly (red "Fallback data" badges)
- [ ] Fresh clone works: `git clone ... && cd ... && docker build -t iss-tracker . && docker run -p 8080:8080 iss-tracker`

---

## 🐛 Known Issues to Watch For

1. **Frontend build not copied**: Verify `COPY --from=frontend-build /app/frontend/dist ./src/main/resources/static` in Dockerfile
2. **Port conflict**: If port 8080 is already in use, the container will fail to start
3. **Healthcheck failing**: Ensure `wget` is available in the alpine image (it is)
4. **Maven cache**: First build will be slow; subsequent builds use layer caching
5. **npm ci failures**: Ensure `frontend/package-lock.json` exists and is up to date

---

**Next Step**: Run these verification steps once Docker is available, then proceed to Step 9 (Documentation).
