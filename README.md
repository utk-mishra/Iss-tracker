# ISS Location & Pass Predictor

**Option A: Resilience & Offline Mode**

A full-stack application that tracks the International Space Station's current position and predicts visible passes over any location on Earth, with comprehensive resilience and offline capabilities.

[![CI](https://github.com/your-org/iss-tracker/workflows/CI/badge.svg)](https://github.com/your-org/iss-tracker/actions)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

---

## 📋 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Degradation Strategy](#-degradation-strategy-the-heart-of-the-project)
- [Technology Stack](#-technology-stack)
- [Quick Start](#-quick-start)
- [Configuration](#%EF%B8%8F-configuration)
- [API Documentation](#-api-documentation)
- [Assumptions and Trade-offs](#-assumptions-and-trade-offs)
- [Trust and Accuracy](#-trust-and-accuracy)
- [Why Option A (and not B or C)](#-why-option-a-and-not-b-or-c)
- [Testing](#-testing)
- [Development](#-development)
- [Deployment](#-deployment)
- [License](#-license)

---

## 🚀 Features

### Core Functionality
- **Real-time ISS Position**: Current latitude, longitude, altitude, and velocity
- **Pass Predictions**: Rise, culmination, and set times with compass directions
- **Visibility Analysis**: Identifies visible passes based on sun angle and ISS illumination
- **Cloud Cover Forecast**: 10-day hourly cloud coverage at pass times
- **Brightness Calculation**: Magnitude estimates for observable passes
- **Smart Alerts**: Contextual notifications for optimal viewing opportunities

### Resilience Features (Option A)
- **4-Level Degradation**: FRESH → STALE → MOCK → Forced Offline
- **Per-Upstream Circuit Breakers**: Independent failure handling for each API
- **Bounded In-Memory Cache**: TTL-based with max-stale fallback
- **Single-Flight Pattern**: Deduplicates concurrent identical requests
- **Offline Mode**: User-controlled toggle for complete network independence
- **Graceful Degradation**: Always returns data; UI shows source badges

### User Experience
- **Dark Theme**: Night-vision friendly interface
- **Responsive Design**: Works on desktop, tablet, and mobile
- **Accessible**: WCAG-compliant with keyboard navigation and ARIA labels
- **Real-time Updates**: Position polls every 5s, overview every 15s
- **Smart Pausing**: Stops polling when browser tab is hidden
- **Error Recovery**: Preserves last good data on network failures

---

## 🏗️ Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────────┐
│                     React Frontend (Vite)                   │
│  ┌──────────┐  ┌───────────┐  ┌──────────┐  ┌───────────┐ │
│  │ Position │  │   Pass    │  │  Alerts  │  │  Offline  │ │
│  │   Card   │  │   Table   │  │   List   │  │  Toggle   │ │
│  └──────────┘  └───────────┘  └──────────┘  └───────────┘ │
│         │              │              │              │      │
│         └──────────────┴──────────────┴──────────────┘      │
│                          │                                  │
│                    API Client (fetch)                       │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP/JSON
                           ↓
┌─────────────────────────────────────────────────────────────┐
│              Spring Boot Backend (Java 21)                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              IssController (REST API)                │  │
│  └────────────────────┬─────────────────────────────────┘  │
│                       ↓                                     │
│  ┌──────────────────────────────────────────────────────┐  │
│  │   IssFacade (orchestrates 3 parallel upstream calls) │  │
│  └──┬───────────────┬───────────────┬───────────────────┘  │
│     ↓               ↓               ↓                       │
│  ┌─────────┐   ┌─────────┐   ┌──────────┐                 │
│  │Position │   │ Passes  │   │  Cloud   │                 │
│  │ Client  │   │ Client  │   │  Client  │                 │
│  └────┬────┘   └────┬────┘   └────┬─────┘                 │
│       │             │             │                         │
│       └─────────────┴─────────────┘                         │
│                     │                                       │
│           ResilientFetcher (per upstream)                  │
│       ┌──────────┬──────────┬────────────┐                │
│       ↓          ↓          ↓            ↓                │
│  SingleFlight TtlCache CircuitBreaker MockData            │
└───────┬────────────────────────────────────────────────────┘
        │
        ↓ (FRESH only)
┌─────────────────────────────────────────────────────────────┐
│                    External APIs                            │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐  │
│  │ Open-Notify  │  │ Pollux Labs  │  │  Open-Meteo     │  │
│  │ (ISS pos)    │  │ (Passes)     │  │  (Cloud)        │  │
│  └──────────────┘  └──────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Layer | Component | Responsibility |
|-------|-----------|----------------|
| **API** | `IssController` | REST endpoints, validation, DTO mapping, HATEOAS links |
| **Service** | `IssFacade` | Orchestrates 3 parallel fetches, enriches passes (magnitude, cloud) |
| **Client** | `OpenNotifyClient`, `PolluxClient`, `OpenMeteoClient` | Adapters to upstream APIs, parsing, velocity estimation |
| **Resilience** | `ResilientFetcher` | Fallback chain: upstream → cache → mock; offline mode |
| **Resilience** | `CircuitBreaker` | Tracks failures per upstream, opens after 3 failures |
| **Resilience** | `TtlCache` | Bounded in-memory cache with TTL and max-stale |
| **Resilience** | `SingleFlight` | Deduplicates concurrent identical requests |
| **Domain** | `AlertService`, `MagnitudeCalculator`, `Compass` | Business logic (pure functions) |
| **Domain** | `VelocityEstimator` | Derives ISS velocity from position deltas |

---

## 🛡️ Degradation Strategy (The Heart of the Project)

Every upstream call goes through **one fallback chain**. Each response includes a `meta` object showing which level was used:

### Degradation Levels

| Level | Source | When | Age | Degraded? |
|-------|--------|------|-----|----------|
| **FRESH** | Live upstream API | Successful call within TTL | 0–TTL | ❌ No |
| **STALE** | Expired cache entry | Upstream failed/timeout/circuit open AND cache entry < max-stale | TTL–max-stale | ✅ Yes |
| **MOCK** | Static fallback JSON | No usable cache AND upstream unavailable | N/A | ✅ Yes |
| **Forced Offline** | STALE or MOCK | User toggled `offline=true` | Varies | ✅ Yes |

### TTL and Max-Stale Policies

| Upstream | TTL | Max Stale | Reasoning |
|----------|-----|-----------|------------|
| **Position** | 5 seconds | 1 hour | ISS moves fast; 1h stale still useful for demos |
| **Passes** | 15 minutes | 24 hours | TLE updates are infrequent; day-old predictions acceptable |
| **Cloud** | 30 minutes | 12 hours | Weather forecasts degrade slowly |

### Circuit Breaker Settings

- **Failure Threshold**: 3 consecutive failures
- **Cooldown**: 30 seconds
- **Timeout**: 3 seconds per request
- **Retry**: 1 immediate retry (counts as 1 breaker failure)

### Forced Offline Behavior

When `offline=true`:
- Upstream callables are **never invoked** (network fully disabled)
- Returns STALE if cache entry exists (even within TTL)
- Falls back to MOCK if no cache
- Never reports FRESH (per PROMPT.md design)

### Cache Eviction

- **Capacity**: 500 entries (LRU)
- **Key Rounding**: Lat/lon rounded to 2 decimals (e.g., 28.61, 77.21)
- **Eviction**: Oldest entries evicted when full; entries older than max-stale are removed

### Trade-Offs Documented

**In-Memory Cache**:
- ✅ **Pro**: Fast, simple, no external dependencies
- ❌ **Con**: Resets on restart, not shared between instances
- **Mitigation**: Acceptable for this use case; production could use Redis

---

## 🛠️ Technology Stack

### Backend
- **Runtime**: Java 21 (Eclipse Temurin)
- **Framework**: Spring Boot 3.2
- **Build**: Maven 3.9
- **HTTP Client**: `java.net.http.HttpClient` (no external deps)
- **JSON**: Jackson (bundled with Spring)
- **Testing**: JUnit 5, Mockito, Spring MockMvc
- **Coverage**: JaCoCo (80%+ on resilience/ and domain/)

### Frontend
- **Framework**: React 18
- **Build Tool**: Vite 5
- **Styling**: Plain CSS (no framework, CSP-compliant)
- **Testing**: Vitest 2 + React Testing Library
- **HTTP**: Native `fetch` API

### Infrastructure
- **Container**: Docker (multi-stage build)
- **Orchestration**: Docker Compose
- **CI/CD**: GitHub Actions + Jenkins
- **Security**: Trivy vulnerability scanning

### No External Libraries for Core Logic

As per requirements:
- ✅ Hand-written: Cache, Circuit Breaker, Single-Flight, Fallback Chain
- ✅ Hand-written: Rate Limiter, Magnitude Calculator, Velocity Estimator, Alert Service
- ❌ Not used: Resilience4j, Caffeine, Guava, Lombok

---

## 🚀 Quick Start

### Using Docker (Recommended)

```bash
# Clone the repository
git clone https://github.com/your-org/iss-tracker.git
cd iss-tracker

# Build and run
docker build -t iss-tracker .
docker run -p 8080:8080 iss-tracker
```

**Or with Docker Compose:**

```bash
docker-compose up
```

Access the app at **http://localhost:8080**

### Local Development

**Backend:**

```bash
cd backend
mvn spring-boot:run
```

**Frontend:**

```bash
cd frontend
npm ci
npm run dev  # Runs on http://localhost:5173, proxies /api to backend
```

See [`DEPLOYMENT.md`](DEPLOYMENT.md) for detailed instructions.

---

## ⚙️ Configuration

### Environment Variables

All configuration is 12-factor compliant (environment variables):

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8080` | **MUST be 8080 by default** (requirement) |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active profile: `dev`, `staging`, or `prod` |
| `OPEN_NOTIFY_URL` | `http://api.open-notify.org/iss-now.json` | ISS position API |
| `POLLUX_URL` | `https://iss-api.polluxlabs.io/iss-pass` | Pass predictions API |
| `POLLUX_PASS_COUNT` | `20` | Number of passes to fetch |
| `OPEN_METEO_URL` | `https://api.open-meteo.com/v1/forecast` | Cloud cover API |
| `RATE_LIMIT_PER_MINUTE` | `120` | Token bucket rate limit (per IP) |
| `CORS_ALLOWED_ORIGINS` | (empty) | Comma-separated allowed origins |
| `TRUSTED_PROXY` | `false` | Trust `X-Forwarded-For` header |
| `UPSTREAM_API_KEY` | (empty) | API key (currently unused; see Security) |

### Spring Profiles

- **`dev`**: Detailed logging, relaxed CORS, actuator details enabled
- **`staging`**: Production-like, limited actuator exposure
- **`prod`**: Minimal logging, strict CORS, actuator hidden

Profile-specific configs: `application-{profile}.yml`

### Running with Custom Port

```bash
# CORRECT: override via env var (keeps default 8080)
export PORT=8081
mvn spring-boot:run

# WRONG: changing application.yml (breaks requirement)
```

---

## 📚 API Documentation

### OpenAPI Specification

**OpenAPI YAML**: [`backend/src/main/resources/openapi.yaml`](backend/src/main/resources/openapi.yaml)

**Interactive Docs**: http://localhost:8080/docs (self-contained, works offline)

### Endpoints

#### `GET /api/v1/iss/overview`

Complete overview: position + passes + alerts + metadata

**Query Params**:
- `lat` (required): Latitude in [-90, 90]
- `lon` (required): Longitude in [-180, 180]
- `offline` (optional): `"true"` or `"false"` (default: `"false"`)

**Example**:
```bash
curl "http://localhost:8080/api/v1/iss/overview?lat=28.6139&lon=77.209&offline=false"
```

**Response**:
```json
{
  "position": { "latitude": 20.5, "longitude": -156.2, ... },
  "passes": [ ... ],
  "nextVisiblePass": { ... },
  "message": null,
  "alerts": [ "ISS visible soon - Check sky in NW", ... ],
  "meta": {
    "position": { "source": "FRESH", "degraded": false, "ageMs": 0 },
    "passes": { "meta": { ... }, "tleAgeHours": 5.2, "tleStale": false },
    "cloud": { "source": "STALE", "degraded": true, "ageMs": 1200000 }
  },
  "_links": { ... }
}
```

#### `GET /api/v1/iss/position`

ISS position only (polled every 5 seconds by frontend)

**Query Params**:
- `offline` (optional): `"true"` or `"false"`

#### `GET /api/v1/iss/passes`

Pass predictions only

**Query Params**:
- `lat`, `lon` (required)
- `offline` (optional)

#### `GET /actuator/health`

Health check (returns `{"status":"UP"}`)

### Error Responses

All errors return consistent JSON:

```json
{
  "error": "lat must be in [-90,90]",
  "status": 400,
  "timestamp": "2026-11-01T12:34:56.789Z"
}
```

**Status Codes**:
- `400`: Invalid input (lat/lon out of range, NaN, Infinity, invalid offline flag)
- `404`: Not found
- `405`: Method not allowed
- `429`: Rate limit exceeded (includes `Retry-After` header)
- `500`: Internal server error (never leaks stack traces)

### Rate Limiting

**Token Bucket** per client IP:
- Default: 120 requests/minute
- Configurable via `RATE_LIMIT_PER_MINUTE`
- Returns `429` with `Retry-After` header
- Applied to `/api/**` only (not actuator)

### Security Headers

All responses include:

```
Content-Security-Policy: default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Permissions-Policy: geolocation=(self)
Cache-Control: no-store  (on /api/** only)
```

Frontend works under this CSP: no inline scripts, no CDN, no external resources.

---

## 📐 Assumptions and Trade-offs

As per PROMPT.md requirements, the following assumptions are documented:

### ISS Physical Properties

1. **Constant Altitude: 408 km**
   - Open-Notify does not provide altitude
   - Actual ISS orbit varies 400–420 km; 408 km is an approximation
   - **Impact**: Negligible for pass predictions (< 1 degree error)

2. **Derived Velocity**
   - Calculated from position deltas using haversine distance
   - Formula: `velocity = (arc_length * (R+408)/R) / time_delta`
   - Default: 27,600 km/h when no previous sample exists
   - **Thread-safe**: Uses `AtomicReference` for previous sample
   - **Limitation**: Only accurate when position is FRESH (velocity not updated from cache hits)

### Brightness Calculation

3. **Approximate Magnitude**
   - Based on **elevation bands**, not photometry:
     - ≥80°: -3.5 (exceptionally bright)
     - ≥40°: -2.5 (very bright)
     - ≥20°: -1.5 (bright)
     - <20°: -0.5 (visible)
   - **Not**: Distance-based, phase angle, or atmospheric extinction
   - **Limitation**: Real magnitude varies with geometry and ISS orientation
   - **Acceptable**: Good enough for "bright pass" alerts

### Pass and Alert Logic

4. **Observable Pass Rule**
   - A pass is **observable** if:
     - `visible == true` (from Pollux API)
     - AND `visibleDurationSec > 0`
   - **Edge Case**: Pollux can return `visible=true` with `visibleDurationSec=0` (instant visibility at set time)
   - Such passes are shown as "visible (instant)" but never trigger alerts

5. **Rise-to-Set Duration**
   - Alert 3 ("Extended viewing opportunity") uses `durationSec` (rise to set)
   - NOT `visibleDurationSec` (which can be shorter)
   - **Reasoning**: Total pass duration is more intuitive for users

6. **Alert 1 Interpretation**
   - "ISS visible soon" triggers for observable passes with `visibleStart` in [now, now+2h]
   - **Does NOT trigger** for passes already in progress (start in past, end in future)
   - **Reasoning**: Users don't need a "soon" alert for something already visible
   - Alerts 3 and 4 still apply to in-progress passes

7. **Default Pass Count: 20**
   - Pollux API defaults to 5 if `n` parameter is omitted
   - We always send `n=${POLLUX_PASS_COUNT}` (default 20)
   - Configurable via environment variable

### Compass Directions

8. **16-Point Compass**
   - Pollux API returns compass strings ("NNW", "SSW", etc.)
   - Stored exactly as received
   - **Fallback**: If compass string is missing/blank, compute from azimuth:
     - `dirs[round(azimuth / 22.5) % 16]`
     - Normalizes negative and ≥360° azimuths first
   - **Edge Case**: NaN azimuth resolves to "N" (never crashes)

---

## 🔒 Trust and Accuracy

### TLE Age and Orbital Accuracy

**Two-Line Element (TLE)** sets describe the ISS orbit. The Pollux API includes:

```json
{
  "tle_epoch": "2026-10-31T18:10:08Z",
  "tle_age_hours": 17.4,
  "tle_stale": false
}
```

**Accuracy Degradation**:
- **0–24 hours**: Highly accurate (< 1 km error)
- **1–3 days**: Good accuracy (< 10 km error)
- **3–7 days**: Predictions degrade (reboosts, drag)
- **> 7 days**: `tleStale: true` → warning shown to user

**User Notification**: Frontend displays a warning banner when `tleStale == true`.

### Upstream API Reliability

All three APIs are **free, third-party services with no SLA**:

| API | Protocol | Auth | Known Issues |
|-----|----------|------|-------------|
| **Open-Notify** | ⚠️ Plain HTTP | None | Occasional downtime; HTTP risks (MITM) |
| **Pollux Labs** | ✅ HTTPS | None | Stable; maintained by community |
| **Open-Meteo** | ✅ HTTPS | None | Excellent uptime; snaps coords to grid |

**Mitigation**:
- Circuit breakers per upstream
- STALE/MOCK fallbacks
- Open-Notify risk documented; no sensitive data sent

### No Database

Per requirements, there is **no persistent storage**:
- Cache is in-memory (resets on restart)
- Not shared between instances
- **Production Alternative**: Redis for shared cache (future work)

---

## 🎯 Why Option A (and not B or C)

### Options Evaluated

| Option | Focus | Pros | Cons |
|--------|-------|------|------|
| **A: Resilience & Offline** | Fallback chain, offline mode | ✅ Survives upstream failures<br>✅ Works without network | ⚠️ In-memory cache<br>⚠️ Approximate magnitude |
| **B: Observability** | Logs, metrics, health checks | ✅ Rich debugging<br>✅ Prometheus metrics | ❌ Doesn't survive failures<br>❌ Needs observability stack |
| **C: Advanced Analytics** | Pass scoring, orbital trust | ✅ Data-driven insights<br>✅ Accuracy scoring | ❌ Calculation-heavy<br>❌ Little resilience gain |

### Why Not Option B?

**Observability** (logs, metrics, health checks) describes failures but **does not survive them**.

- Basic health endpoint is included (`/actuator/health`)
- Richer observability (Prometheus, Grafana, distributed tracing) is listed as **future work**
- **Design Principle**: Resilience > Observability for this use case

### Why Not Option C?

**Advanced analytics** (pass scoring, orbital trust analysis) are calculation-heavy and **add little resilience**.

- The brief already requires magnitude calculation (elevation-based approximation)
- Full photometry and orbital mechanics are out of scope
- **Design Principle**: Simple, reliable, offline-capable > complex analytics

### Option A Delivers

✅ **Always returns data** (FRESH → STALE → MOCK)
✅ **Offline mode** works without network
✅ **Per-section degradation** (position FRESH, passes STALE, cloud MOCK)
✅ **User transparency** (source badges show degradation)
✅ **Production-ready** resilience (circuit breakers, rate limiting, security headers)

---

## 🧪 Testing

### Backend Tests

**Framework**: JUnit 5 + Mockito + Spring MockMvc

**Coverage**: 80%+ on `resilience/` and `domain/` (JaCoCo)

**Key Test Scenarios**:

- **TtlCache**: Fresh, stale, expired beyond max-stale, eviction
- **CircuitBreaker**: Opens after 3 failures, skips upstream while open, retries after cooldown
- **ResilientFetcher**:
  - Success → FRESH
  - Failure + cache → STALE
  - Failure + no cache → MOCK
  - Offline → upstream NEVER invoked
  - Stale older than max-stale → MOCK
  - Single-flight deduplicates concurrent calls
- **Parsers**: Against REAL upstream fixtures (no hand-invented JSON)
- **AlertService**: Boundary tests (exactly 2h, cloud exactly 20%, duration exactly 300s, magnitude exactly -2.0)
- **IssFacade**: Fully offline test (all three sources MOCK, zero network calls, alerts present)
- **Controller**: Input validation (lat=91 → 400, offline=true → 200)
- **Mock Data**: Time-shifted to show alerts in demos

**Run Tests**:

```bash
cd backend
mvn verify
```

**Coverage Report**: `backend/target/site/jacoco/index.html`

### Frontend Tests

**Framework**: Vitest 2 + React Testing Library

**Test Count**: 79 tests, all passing

**Key Test Scenarios**:

- **Badge Logic**: FRESH → green, STALE → amber, MOCK → red
- **Offline Toggle**: Persists to localStorage, sends `offline=true`
- **Form Validation**: Boundaries (-90/90, -180/180), NaN, Infinity
- **Polling Pause**: Verified with fake timers (tab hidden → no fetch)
- **Error Keeps Data**: Fetch fails → last good data preserved
- **Null Fields**: magnitude, cloudCoverPct, visibleStart can be null (no crash)
- **Zero-Duration Pass**: Labeled "visible (instant)", not highlighted
- **No Observable Pass**: Shows message from backend
- **TLE Stale Warning**: Displayed when `tleStale: true`
- **429 Handling**: Shows "Try again in X seconds"

**Run Tests**:

```bash
cd frontend
npm test
```

### Integration Tests

**Docker Smoke Test** (in CI):

```bash
docker build -t iss-tracker .
docker run -d --name test -p 8080:8080 iss-tracker
sleep 20
curl -f http://localhost:8080/actuator/health
curl -f "http://localhost:8080/api/v1/iss/overview?lat=0&lon=0&offline=true"
docker stop test && docker rm test
```

---

## 🛠️ Development

### Project Structure

```
iss-tracker/
├── backend/
│   ├── src/main/java/com/example/iss/
│   │   ├── api/           # REST controllers, DTOs, filters, exception handler
│   │   ├── client/        # Upstream API adapters, parsers
│   │   ├── config/        # Spring configuration, properties
│   │   ├── domain/        # Business logic (pure functions)
│   │   ├── resilience/    # Circuit breaker, cache, fallback chain
│   │   └── service/       # Facade, orchestration
│   ├── src/main/resources/
│   │   ├── application*.yml
│   │   ├── mocks/         # Static fallback JSON (time-shifted at load)
│   │   ├── openapi.yaml
│   │   └── static/        # React build (copied by Dockerfile)
│   └── src/test/
│       ├── java/          # JUnit 5 tests
│       └── resources/fixtures/  # Real upstream responses
├── frontend/
│   ├── src/
│   │   ├── api/           # Fetch wrapper (429 handling)
│   │   ├── components/    # React components
│   │   ├── hooks/         # Custom hooks (polling, visibility, offline)
│   │   ├── styles/        # Plain CSS (dark theme)
│   │   └── utils/         # Validation, time formatting, localStorage
│   ├── tests/             # Vitest + RTL tests
│   └── vite.config.js     # Dev proxy, test config
├── docs/
│   ├── patterns.md        # Design patterns reference
│   ├── sequence.drawio    # Sequence diagrams (draw.io XML)
│   └── benchmarks.md      # Performance analysis
├── jenkins/
│   ├── job-config.xml     # Jenkins job (XML)
│   └── jcasc.yml          # Jenkins Configuration as Code
├── .github/workflows/ci.yml
├── Dockerfile
├── docker-compose.yml
├── Jenkinsfile
├── README.md              # This file
├── DEPLOYMENT.md          # Deployment guide
└── PROMPT.md              # Original requirements
```

### Design Patterns

See [`docs/patterns.md`](docs/patterns.md) for detailed explanations:

- **Circuit Breaker**: `CircuitBreaker.java`
- **Cache-Aside**: `TtlCache.java`
- **Fallback Chain**: `ResilientFetcher.java`
- **Adapter**: `OpenNotifyClient.java`, etc.
- **Facade**: `IssFacade.java`
- **Single-Flight**: `SingleFlight.java`

### Sequence Diagrams

See [`docs/sequence.drawio`](docs/sequence.drawio) for flows:

- Happy path (all FRESH)
- Cache hit (STALE fallback)
- Mock fallback
- Offline mode
- Circuit breaker open

### Performance Benchmarks

See [`docs/benchmarks.md`](docs/benchmarks.md) for analysis:

- Cache effect on latency
- Single-flight deduplication
- Upstream call count reduction

---

## 🚢 Deployment

See [`DEPLOYMENT.md`](DEPLOYMENT.md) for comprehensive deployment guide.

### Quick Deploy

```bash
# Production build
docker build -t iss-tracker .

# Run with production profile
docker run -d \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e CORS_ALLOWED_ORIGINS=https://your-domain.com \
  --name iss-tracker \
  iss-tracker
```

### CI/CD

**GitHub Actions** (`.github/workflows/ci.yml`): Runs automatically on every push and pull request

✅ **What's Included**:
- Backend tests (Maven + JUnit)
- Frontend tests (Vitest + React Testing Library)
- Docker image build
- Trivy security scanning (uploads results to GitHub Security tab)
- Container smoke tests (health check + API validation)

**Jenkins** (`Jenkinsfile`): Production deployment pipeline

✅ **What's Included**:
- Full test suite with coverage reports
- Docker image build and tagging
- Trivy security scanning
- Container smoke tests
- **Optional**: Push to container registry (requires credentials)
- **Optional**: Deploy to dev/staging/prod environments (requires credentials)

📚 **Setup Guides**:
- [`SECRETS_SETUP.md`](SECRETS_SETUP.md) - Configure registry and deployment credentials
- [`docs/CI_CD_SETUP.md`](docs/CI_CD_SETUP.md) - Quick reference for both pipelines

**Note**: Container registry push is an **optional enhancement**. The core CI workflow (tests + build + scan) works without any secrets configured.

---

## 📄 License

MIT License - see [LICENSE](LICENSE) file.

---

## 🙏 Acknowledgments

- **Open-Notify**: ISS position API
- **Pollux Labs**: Pass prediction API
- **Open-Meteo**: Weather forecast API
- **NASA**: ISS data and imagery

---

**Built with ❤️ for ISS enthusiasts and resilient system design.**
