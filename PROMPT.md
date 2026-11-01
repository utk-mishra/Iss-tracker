# ISS Location & Pass Predictor: Option A (Resilience & Offline Mode)
Stack: Spring Boot 3 (Java 21, Maven) backend + React (Vite) frontend, one Docker image on port 8080.

## STATUS
- Step 0 (contract check): DONE. Fixtures and mocks already exist (see section 3). Do NOT redo them.
- Step 1 (skeleton, config, health): DONE.
- Step 2 (TtlCache, CircuitBreaker): DONE.
- Step 3 (ResilientFetcher, SingleFlight): DONE. Do NOT modify these classes unless a later step finds a real bug; if so, tell me first.
- Step 4 (Compass, MagnitudeCalculator, VelocityEstimator, AlertService): DONE.
- Step 5 (clients, parsers, mocks, 16-point compass, port fix): DONE. Do NOT modify these classes unless a later step finds a real bug; if so, tell me first.
- Step 6 (IssFacade, controller, error handler, RateLimitFilter, SecurityHeadersFilter, openapi.yaml): DONE. Do NOT modify the backend unless a later step finds a real bug; if so, tell me first.
- Step 7 (React UI with tests): DONE.
- Step 8 (Dockerfile, Jenkinsfile, GitHub Actions, docker-compose): DONE. Docker image builds, runs healthy as a non-root user on port 8080 (verified by me).
- Step 9 (README, patterns.md, sequence.drawio, benchmarks.md): DONE.
- Next: FINAL VERIFICATION (section 13). No new features. Only fix real bugs or inaccurate documentation, one fix at a time, and tell me before changing anything.

## 0. How to work
You are a senior full-stack engineer using TDD. Follow the numbered steps in section 12 exactly, one at a time.
- After each step: list files created/changed, run the tests, summarise assumptions to add to the README, then STOP and wait for me to say "continue".
- When a step is done, do NOT start the next step, do NOT "prepare" it, and do NOT edit files outside the current step's scope.
- At the start of each step, restate in two lines which step you are on and which rules apply.
- For steps 3, 5, 6 and 7, show me a short plan first and wait for approval before writing code.
- If you cannot run a terminal command (curl, mvn, npm, docker), say so explicitly and give me the exact command to run myself. Never claim a command ran or tests passed unless you actually ran it and can show the output.
- Ask me before adding any dependency beyond those listed in section 4.
- Never invent upstream behaviour. Use only the payloads and fixtures in section 3.

## 1. Product requirements
Input: latitude/longitude (user sets location in the UI, or calls the REST API from Postman).
Output:
- Current ISS position: latitude, longitude, altitude, velocity
- Pass predictions: rise, culmination, set times
- Visibility window and duration
- Rise/set compass direction and maximum elevation
- Brightness magnitude (calculated from elevation)
- Cloud cover at pass time

Conditional alerts (use these exact strings):
1. ISS visible within 2 hours -> "ISS visible soon - Check sky in {direction}"
2. Visible window AND cloud cover < 20% -> "Clear skies predicted - Excellent viewing opportunity"
3. Pass duration > 5 minutes -> "Extended viewing opportunity"
4. Magnitude < -2 -> "Exceptionally bright pass - Easily visible"

Also: offline mode with a toggle; cached or mock results when an API is unavailable; no 3rd-party libraries for core logic; no database.

## 2. Option chosen: A (Resilience & Offline Mode) only
Implement only Option A. README must contain a "Why not B / C" section:
- Not B: logs/metrics/health describe failures but do not survive them. Only the basic actuator health endpoint is included; richer observability is listed as future work.
- Not C: pass scoring and orbital trust analysis are calculation-heavy and add little resilience here. The brief already requires a magnitude calculation, which is a documented approximation.

### Degradation strategy (the heart of the project)
Every upstream call goes through one fallback chain, and every response says which level it used:
| Level | Meaning | When |
|---|---|---|
| FRESH | live data, or cache within TTL | upstream OK, or unexpired cache |
| STALE | expired cache entry | upstream failed/timeout/circuit open AND cache entry is younger than max-stale |
| MOCK | static fallback | nothing usable in cache |
| Forced offline | user toggle offline=true | never touch the network: STALE if cached, else MOCK |

- TTL: position 5 s, passes 15 min, cloud 30 min.
- Max stale: position 1 h, passes 24 h, cloud 12 h (older entries are evicted).
- One circuit breaker PER upstream: 3 failures, 30 s cooldown, half-open after cooldown.
- Timeout 3 s, 1 retry. Single-flight: concurrent requests for the same key share one upstream call.
- Cache bounded to 500 entries (evict oldest). Cache keys round lat/lon to 2 decimals.
- Each response has a per-upstream `meta`: { source, degraded, ageMs } so the UI can badge each section separately.
- Known trade-off (document in README): the cache is in memory, so it resets on restart and is not shared between instances.
- Behaviours implemented in Step 3 (document in README, do not change):
  - Forced offline NEVER reports FRESH, even if the cached entry is within TTL: STALE if cached (within max-stale), else MOCK. The upstream callable is never invoked.
  - MOCK results are never written to the cache.
  - Retry is immediate (no backoff), catches all exceptions (a parse error is retried like a timeout), and counts as ONE breaker failure per fetch.
  - Single-flight is not a cache: the in-flight entry is removed in a finally block, so the next sequential call hits upstream again.
  - The 3 s timeout is enforced by the HttpClient (Step 5), not by ResilientFetcher.

## 3. Real upstream contracts (verified live in Step 0)
None of the three APIs needs an account or API key.

### Saved fixtures (real responses) in backend/src/test/resources/fixtures/
- open-notify-position.json
- pollux-passes-default.json (5 passes)
- pollux-passes-n20.json (20 passes; contains a real zero-duration visible pass)
- open-meteo-cloud.json (240 hourly entries)
Use these in all parser tests. Do not hand-write replacement payloads.

### Static MOCK fallbacks in backend/src/main/resources/mocks/
position.json, passes.json, cloud.json. They are schema-identical to the real responses so the same parsers consume them. Their timestamps are placeholders that MUST be time-shifted at load (Step 5). Mock data is always labelled MOCK in meta.

### 3.1 Open-Notify: ISS position
GET http://api.open-notify.org/iss-now.json   (plain HTTP)
{
  "iss_position": { "longitude": "-156.7653", "latitude": "35.5243" },
  "timestamp": 1793437392,
  "message": "success"
}
Rules: latitude/longitude are STRINGS, so parse to double. timestamp is unix seconds. Reject unless message == "success" and values are in range. There is NO altitude or velocity in this response.

### 3.2 Pollux Labs: pass predictions
GET https://iss-api.polluxlabs.io/iss-pass?lat={lat}&lon={lon}&n={n}
Verified in Step 0:
- The short URL (lat and lon only) works; observer.elevation_m defaults to 0.
- `n` is honoured: default (no n) returns 5 passes; n=20 returns 20 passes.
- Make the count configurable: env POLLUX_PASS_COUNT, DEFAULT 20, always sent as `n`.
- Optional `alt` (metres) exists but is not used.
Real response shape (lat=28.6139, lon=77.2090, default count):
{"_support":{"message":"Support this API with a tip","url":"https://ko-fi.com/polluxlabs"},"satellite":"ISS (ZARYA)","tle_epoch":"2026-10-31T18:10:08Z","tle_age_hours":17.4,"tle_stale":false,"observer":{"lat":28.6139,"lon":77.209,"elevation_m":0.0},"generated_at":"2026-11-01T11:36:21Z","params":{"min_elevation_deg":10.0,"visible_only":false,"sun_alt_max_deg":-6.0,"days_ahead":10,"days_ahead_capped":false},"passes":[{"rise":{"time":"2026-11-01T15:56:08Z","azimuth_deg":197.1,"compass":"SSW"},"culmination":{"time":"2026-11-01T15:59:13Z","elevation_deg":35.7},"set":{"time":"2026-11-01T16:02:19Z","azimuth_deg":60.9,"compass":"ENE"},"duration_sec":371,"above_horizon":true,"visible":false},{"rise":{"time":"2026-11-02T00:07:04Z","azimuth_deg":343.4,"compass":"NNW"},"culmination":{"time":"2026-11-02T00:09:56Z","elevation_deg":24.8},"set":{"time":"2026-11-02T00:12:48Z","azimuth_deg":98.9,"compass":"E"},"duration_sec":343,"above_horizon":true,"visible":true,"visible_start":"2026-11-02T00:07:04Z","visible_end":"2026-11-02T00:12:48Z","visible_duration_sec":343}]}
(The sample above is trimmed to 2 of 5 passes. Read the saved fixtures for the full payloads.)
Rules:
- Times are ISO-8601 UTC with "Z" (Instant.parse). rise/set have time, azimuth_deg, compass; culmination has time and elevation_deg (= max elevation).
- above_horizon = clears the 10 degree minimum elevation. `visible` = above horizon AND dark sky (sun at -6 degrees or lower) AND the ISS is sunlit (not in Earth's shadow). Passes with visible == false can never be seen.
- visible_start, visible_end, visible_duration_sec exist ONLY when visible == true; treat them as optional.
- EDGE CASE (seen live): a pass can have visible == true and visible_duration_sec == 0 (visible_start == visible_end == set time). See pollux-passes-n20.json. Such a pass is NOT observable (see section 5).
- tle_age_hours / tle_stale describe orbital-data freshness; surface them in meta.passes.
- One malformed pass must be skipped, not fail the parse. The API returns 16-point compass strings (e.g. NNW, SSW, WNW, ESE). Store them as plain Strings exactly as received (Pass.riseDirection / Pass.setDirection); never use Compass.valueOf. Compute a fallback with Compass only if the string is missing or blank. A visible pass with missing window fields is kept as NON-observable (not dropped).
- Most passes are not visible, so empty alerts are normal.

### 3.3 Open-Meteo: hourly cloud cover
GET https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&hourly=cloud_cover&timezone=UTC&forecast_days=10
Verified in Step 0: exactly 1-hour step; time and cloud_cover arrays equal length (240 entries for forecast_days=10); series starts at 00:00 UTC of the current day (past hours included); coordinates are snapped to Open-Meteo's grid (asked 28.6139/77.209, got 28.576/77.187; this is normal).
Shape (arrays trimmed; do not assume a length):
{"latitude":28.576448,"longitude":77.18678,"timezone":"GMT","hourly_units":{"time":"iso8601","cloud_cover":"%"},"hourly":{"time":["2026-10-31T00:00","2026-10-31T01:00"],"cloud_cover":[0,0]}}
Rules: times have NO "Z", so parse with LocalDateTime.parse(...).toInstant(ZoneOffset.UTC) (valid because timezone=UTC is requested). Verify the step is exactly 1 hour and both arrays have equal length; individual values may be null even though none were seen. Cloud cover for a pass = value at the hourly slot nearest visible_start (or rise if not visible); null if outside the forecast.

## 4. Backend stack and structure
Dependencies: spring-boot-starter-web, spring-boot-starter-actuator, spring-boot-starter-test only (plus JaCoCo plugin). HTTP via java.net.http.HttpClient, JSON via bundled Jackson, logging via SLF4J. Hand-write: cache, circuit breaker, single-flight, fallback chain, rate limiter, alerts, magnitude, velocity. No Resilience4j, Caffeine, Guava, Lombok.

com.example.iss
- resilience/: Source, Result<T>(data, source, ageMs), TtlCache<V> (injectable Clock, max-stale, bounded), CircuitBreaker (injectable Clock, thread-safe), ResilientFetcher (fallback chain; takes a Callable upstream and a Supplier mock so it is unit-testable without HTTP), SingleFlight
- client/ (Adapters; nothing else sees upstream JSON): HttpJson (connect and request timeouts are constructor parameters, defaults 2 s and 3 s, short values in tests), interfaces PositionClient { IssPosition fetch(); } (no parameters), PassesClient { PassFeed fetch(double lat, double lon); }, CloudClient { CloudForecast fetch(double lat, double lon); }, and OpenNotifyClient, PolluxClient, OpenMeteoClient implementing them, each with a static pure parse(JsonNode). OpenNotifyClient.fetch() calls VelocityEstimator inside the upstream callable, never in parse().
- domain/: IssPosition, Pass, PassFeed, CloudForecast, Compass, MagnitudeCalculator, VelocityEstimator, AlertService
- service/: IssFacade (Facade)
- api/: IssController, ApiExceptionHandler, RateLimitFilter (token bucket per IP), SecurityHeadersFilter
- resources/: application.yml (+ application-dev/staging/prod.yml), mocks/{position,passes,cloud}.json (exist already), static/ (React build)
Follow SOLID: small single-purpose classes, interfaces for clients (dependency inversion), constructor injection. Use an injectable java.time.Clock everywhere; never Thread.sleep in tests.

## 5. Domain rules
- Altitude: constant 408 km (Open-Notify gives none); say so in the response and README.
- Velocity: derived (VelocityEstimator must be thread-safe; use an AtomicReference of the previous sample). Keep the previous sample; velocity = haversine arc length scaled by (R+408)/R divided by the time delta. Default 27,600 km/h when there is no previous sample or the timestamp did not advance. Compute INSIDE the upstream callable so cache hits do not corrupt estimator state.
- Magnitude: elevation bands on max elevation (>=80: -3.5; >=40: -2.5; >=20: -1.5; else -0.5). Document as an approximation, not photometry. Show only for observable passes. Test each boundary (80, 40, 20) and the value just below it.
- Compass fallback: 16-point names N, NNE, NE, ENE, E, ESE, SE, SSE, S, SSW, SW, WSW, W, WNW, NW, NNW via dirs[round(az/22.5) % 16]. Normalise negative and >=360 azimuths first (360 -> N, -10 -> N, 370 -> N). NaN azimuth resolves to N and must not crash. Test boundaries around 11.25 degrees.
- OBSERVABLE PASS = visible == true AND visible_duration_sec > 0. Only observable passes can be nextVisiblePass or trigger alerts. A visible pass with a zero-length window is shown muted as "visible (instant)" and never alerts. Add a test using the real zero-duration pass from pollux-passes-n20.json.
- Next visible pass = first OBSERVABLE pass whose visible_end is in the future. Alerts are computed for that pass only.
- Alert 1: observable AND visible_start in [now, now+2h] inclusive; uses the RISE compass. Interpretation (record in README): only observable passes trigger it, so users are not told to look at a pass they cannot see.
- A pass already in progress (visible_start in the past, visible_end in the future) does NOT trigger alert 1, but alerts 3 and 4 still apply to it. Record this in the README.
- Alert 2: alert-1 condition AND cloud cover known AND strictly < 20. Null cloud never triggers it.
- Alert 3: duration_sec strictly > 300 (rise-to-set; record this choice in README).
- Alert 4: magnitude strictly < -2. Note: with the current bands only elevation >= 80 gives -3.5, and no band gives exactly -2, so the boundary test must call AlertService directly with magnitude -2.0 (no alert) and -2.1 (alert).
- AlertService is a pure function: build(Pass, Double cloudPct, double magnitude, Instant now) -> List<String>, with one if per rule, alert text character-for-character as in section 1, in the order 1,2,3,4.
- Drop passes whose set time is already past (important for STALE data).
- No observable pass: nextVisiblePass = null and message "No visible pass in the returned predictions".
- Defaults: POLLUX_PASS_COUNT = 20.

## 6. REST API (HATEOAS-style links)
- GET /api/v1/iss/overview?lat=&lon=&offline=false -> { position, passes[], nextVisiblePass, message, alerts[], meta{position,passes,cloud}, _links }
  - position: latitude, longitude, altitudeKm, velocityKmh, observedAt, note
  - pass: rise, culmination, set, durationSec, riseDirection, riseAzimuthDeg, setDirection, setAzimuthDeg, maxElevationDeg, visible, observable, visibleStart, visibleEnd, visibleDurationSec, magnitude, cloudCoverPct
  - meta.passes also carries tleAgeHours, tleStale
- GET /api/v1/iss/position and GET /api/v1/iss/passes?lat=&lon= (so the UI can poll position every 5 s)
- GET /actuator/health (details hidden by default; only {"status":"UP"}).
- Docs (implemented in Step 6): openapi.yaml served at /openapi.yaml and a small self-contained static /docs page. No CDN and no new dependency, so docs work offline.
- _links: self, position, passes, health, docs
- Validate lat in [-90,90], lon in [-180,180], reject NaN and Infinity; missing or non-numeric lat/lon and offline values other than "true"/"false" give a clean 400. One ErrorDto shape for 400, 404, 405, 429 and 500; never leak stack traces or internal messages.
- Rate limit (implemented in Step 6): token bucket per client IP on /api/** only, 120 requests/minute by default (RATE_LIMIT_PER_MINUTE), 429 with Retry-After. Client IP is request.getRemoteAddr(); X-Forwarded-For is honoured only when TRUSTED_PROXY=true (right-most entry). Bucket map is bounded and idle buckets are evicted.
- Security headers (implemented in Step 6): Content-Security-Policy "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'", X-Content-Type-Options nosniff, X-Frame-Options DENY, Referrer-Policy no-referrer, Permissions-Policy geolocation=(self), Cache-Control no-store on /api/**. The frontend must work under this CSP: no inline scripts, no external fonts/images/scripts, no CDN.
- The three upstream fetches run concurrently (virtual threads). When a next observable pass exists, `message` is null/omitted. Cloud cover is returned for every pass; magnitude only for observable passes.
- Hand-write openapi.yaml with full schemas, examples and error responses.

## 7. Frontend (React + Vite)
- Location form (manual lat/lon with validation) + "Use my location" (navigator.geolocation).
- Offline toggle persisted in localStorage, sends offline=true.
- Per-section source badges: green "Live", amber "Cached N min ago", red "Fallback data"; a global banner when anything is degraded; a warning when tleStale is true.
- Position card (polls every 5-10 s), alert cards, pass table (non-observable passes visually muted, observable highlighted, zero-length visible passes labelled "visible (instant)"), compass/sky-dial SVG for rise->set direction, countdown to the next observable pass.
- On fetch error keep the last good data on screen with an error banner.
- Excellent UX: responsive, accessible (labels, contrast, keyboard), dark theme suitable for night viewing, loading skeletons.
- Dev proxy /api -> http://localhost:8080 by default, overridable with the VITE_API_TARGET environment variable. Production build output goes to frontend/dist (Step 8 copies it into the Spring static folder; do not touch the Dockerfile in Step 7).
- Plain CSS only: no UI framework, no CDN, no external fonts or images (the CSP and offline mode forbid them). Tell me every dependency before adding it.
- Wrap every localStorage access in try/catch. Pause polling when the tab is hidden. Never crash on null fields (visibleStart, magnitude, cloudCoverPct can be null). Show Retry-After on 429. Show times in the user's local time with UTC on hover/tooltip, using Intl.DateTimeFormat.
- Never show MOCK data as live: badges and banners must follow meta exactly.
- Tests with Vitest + React Testing Library: badge logic for all three sources, offline toggle persists and sends offline=true, form validation including boundaries -90/90/-180/180 and NaN, polling pauses on a hidden tab (fake timers), an error keeps the last good data, null-field rendering, "visible (instant)" label, no-observable-pass message, tleStale warning. Use a REAL backend /overview response saved as a test fixture (run the backend, save the JSON); do not hand-invent the shape.

## 8. Security and 12-factor
- Default port MUST be 8080 (server.port: ${PORT:8080}); to avoid a local clash run with PORT=8081 instead of changing the default. All config via env vars (PORT, POLLUX_URL, POLLUX_PASS_COUNT, OPEN_NOTIFY_URL, OPEN_METEO_URL, TTLs, RATE_LIMIT_PER_MINUTE, TRUSTED_PROXY, CORS_ALLOWED_ORIGINS, spring profile). No secrets in the repo.
- None of the APIs needs a key. Document this, and show how a key WOULD be supported: read UPSTREAM_API_KEY from env (Docker secret / Jenkins credentials), send it as a header, never log it, redact it in error messages.
- Input validation, rate limiting, security headers (nosniff, frame deny, CSP, referrer-policy), CORS allowlist default none (same-origin), bounded cache.
- Open-Notify is plain HTTP: document the risk and the fallback.

## 9. Testing (JUnit 5 + Mockito; BDD given/when/then names, @Nested per scenario)
- TtlCache: fresh, stale, expired beyond max-stale, eviction.
- CircuitBreaker: opens after 3 failures, skips upstream while open, retries after cooldown.
- ResilientFetcher: success->FRESH; failure+cache->STALE; failure+no cache->MOCK; offline->upstream NEVER invoked; stale older than max-stale->MOCK; single-flight makes one upstream call under concurrency.
- Parsers against the REAL fixtures in section 3: string coordinates, "success" check, no-Z Open-Meteo times, nearest-hour rounding (including just past the last hour -> null), null cloud values, malformed pass skipped, visible_* optional, zero-duration visible pass parsed but not observable, n=20 fixture parses to 20 passes.
- AlertService boundaries: exactly 2 h, cloud exactly 20, duration exactly 300, magnitude exactly -2; zero-duration visible pass never alerts.
- IssFacade fully offline: all three sources MOCK, "ISS visible soon" alert present, zero network calls.
- Controller (MockMvc): lat=91->400, offline=true->200.
- Mock passes are TIME-SHIFTED at load, anchored on the first OBSERVABLE pass, so it starts ~90 minutes from now; mock cloud is re-anchored to today's 00:00 UTC and its 24-hour curve is repeated to cover 240 hourly entries (mocks/cloud.json itself is not edited), with a test at a fixed clock of 23:30 UTC proving the shifted mock pass still gets cloud below 20. Offline demos therefore always show the alerts. Mock data is always labelled MOCK.
Target 80%+ coverage on resilience/ and domain/ (JaCoCo).

## 10. Documentation deliverables
- README.md: design approach, architecture, degradation table, config table, how to run (local, Docker), assumptions (constant altitude, derived velocity, approximate magnitude, alert interpretations, observable-pass rule, rise-to-set duration, default pass count 20), trust note (TLE age, accuracy drops further ahead, free third-party APIs have no SLA), in-memory cache trade-off, Why Option A and not B/C.
- openapi.yaml.
- docs/sequence.drawio: draw.io XML covering the happy path, cache hit, stale fallback, mock fallback, offline mode and circuit-open flows.
- docs/patterns.md: Circuit Breaker, Cache-Aside, Fallback Chain, Adapter, Facade, Single-Flight, with where each lives in the code.
- docs/benchmarks.md (bonus): cache and single-flight effect on latency/upstream call count.

## 11. Build and deploy
- Dockerfile at repo root: multi-stage (node build -> maven build -> eclipse-temurin JRE), non-root user, React build copied into static/, EXPOSE 8080, HEALTHCHECK.
- Jenkinsfile: Checkout, backend tests, frontend tests, build image, Trivy scan, push (credentials via withCredentials), deploy; per-environment config via Spring profiles dev/staging/prod. Also a Jenkins job config file (jenkins/job-config.xml or JCasC).
- Also .github/workflows/ci.yml with the equivalent stages.
- .dockerignore, .gitignore, docker-compose.yml for local use.
- A fresh clone must work with: docker build -t iss-tracker . && docker run -p 8080:8080 iss-tracker

## 12. Steps (stop after each and wait for "continue")
0. DONE: contract check, fixtures, mocks.
1. DONE: Maven skeleton, application.yml + profiles, actuator health, Dockerfile, .gitignore, CI placeholder.
2. DONE: TtlCache and CircuitBreaker.
3. DONE: ResilientFetcher + SingleFlight.
4. DONE: Compass, MagnitudeCalculator, VelocityEstimator, AlertService.
5. DONE: clients, parsers, mocks, 16-point compass, port fix.
6. DONE: IssFacade, controller, error handler, RateLimitFilter, SecurityHeadersFilter, openapi.yaml.
7. DONE: React UI with tests.
8. DONE: Dockerfile verification, Jenkinsfile, GitHub Actions, docker-compose.
9. DONE: README, docs/patterns.md, docs/sequence.drawio, docs/benchmarks.md.
10. FINAL VERIFICATION (human-led): see the checklist at the end of section 13.

## 13. Definition of done
All backend and frontend tests green; `docker build` succeeds; the container serves UI and API on port 8080; the offline toggle works with the network disabled; killing one upstream flips only that section's badge to Cached or Fallback; every alert rule has a boundary test; the README states every assumption; no secrets in the repo.

### Final verification checklist (tick each only after running it)
- [ ] mvn -q verify (backend) and npm ci && npm test -- --run && npm run build (frontend) pass on a fresh clone.
- [ ] docker build -t iss-tracker . and docker run -p 8080:8080 iss-tracker serve the UI and API; container runs as a non-root user and reports healthy.
- [ ] All upstream URLs dead (OPEN_NOTIFY_URL, POLLUX_URL, OPEN_METEO_URL pointed at 127.0.0.1:9): every badge shows Fallback data and the "ISS visible soon" alert appears.
- [ ] Only POLLUX_URL dead: only the passes badge changes.
- [ ] Offline toggle: no upstream calls (check container logs), badges show Cached or Fallback.
- [ ] docs/benchmarks.md numbers come from a real benchmark that is in the repo and reproducible with one documented command; otherwise the file says they are illustrative.
- [ ] README facts (test counts, coverage, env var count, commands) match reality; README says what was verified and how.
- [ ] docs/sequence.drawio opens in app.diagrams.net with 5 readable pages.
- [ ] GitHub Actions run is green (or the failures are fixed); Jenkinsfile and the Jenkins job config file are present.
- [ ] git grep -iE "password|secret|api[_-]?key|token" shows only documentation mentions and .env.example placeholders.
- [ ] target/, node_modules/, dist/ and .env are not tracked.