# Design Patterns in ISS Tracker

This document describes the design patterns implemented in the ISS Tracker application and where to find them in the codebase.

---

## Table of Contents

1. [Circuit Breaker](#1-circuit-breaker)
2. [Cache-Aside](#2-cache-aside)
3. [Fallback Chain](#3-fallback-chain)
4. [Adapter](#4-adapter)
5. [Facade](#5-facade)
6. [Single-Flight](#6-single-flight)
7. [Pattern Interactions](#pattern-interactions)

---

## 1. Circuit Breaker

### Intent

Prevent cascading failures by automatically detecting and handling repeated failures to an external service. When failures exceed a threshold, the circuit "opens" and immediately rejects requests without calling the failing service, giving it time to recover.

### Implementation

**Location**: [`backend/src/main/java/com/example/iss/resilience/CircuitBreaker.java`](../backend/src/main/java/com/example/iss/resilience/CircuitBreaker.java)

**Key Features**:
- **States**: CLOSED (normal), OPEN (rejecting calls), HALF_OPEN (testing recovery)
- **Failure Threshold**: 3 consecutive failures
- **Cooldown Period**: 30 seconds
- **Thread-Safe**: Uses `AtomicInteger` and `AtomicLong` for lock-free concurrency
- **Injectable Clock**: Supports fake timers in tests

### Code Example

```java
public class CircuitBreaker {
    private final int failureThreshold;
    private final long cooldownMs;
    private final Clock clock;
    
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong openedAt = new AtomicLong(0);
    
    public boolean isOpen() {
        long opened = openedAt.get();
        if (opened == 0) return false;
        
        if (clock.millis() - opened >= cooldownMs) {
            openedAt.set(0);  // Close after cooldown
            failureCount.set(0);
            return false;
        }
        return true;
    }
    
    public void recordSuccess() {
        failureCount.set(0);
        openedAt.set(0);
    }
    
    public void recordFailure() {
        int failures = failureCount.incrementAndGet();
        if (failures >= failureThreshold) {
            openedAt.set(clock.millis());
        }
    }
}
```

### Usage

**Instantiation**: One circuit breaker per upstream API (position, passes, cloud)

```java
@Bean
public CircuitBreaker positionCircuitBreaker(Clock clock) {
    return new CircuitBreaker(3, 30000, clock);
}
```

**In ResilientFetcher**:

```java
if (breaker.isOpen()) {
    // Skip upstream call, go straight to cache/mock
    return fallbackFromCache();
}

try {
    T result = upstream.call();
    breaker.recordSuccess();
    return result;
} catch (Exception e) {
    breaker.recordFailure();
    throw e;
}
```

### Benefits

- ✅ Prevents wasted calls to failing services
- ✅ Allows upstream time to recover
- ✅ Fails fast when circuit is open
- ✅ Automatic recovery after cooldown

### Trade-offs

- ⚠️ May reject requests during cooldown even if upstream has recovered
- ⚠️ Simple state machine (no exponential backoff)

---

## 2. Cache-Aside

### Intent

Load data lazily into a cache. The application first checks the cache; if the data is not found (cache miss), it fetches from the source, then writes it to the cache for future requests.

### Implementation

**Location**: [`backend/src/main/java/com/example/iss/resilience/TtlCache.java`](../backend/src/main/java/com/example/iss/resilience/TtlCache.java)

**Key Features**:
- **TTL (Time-To-Live)**: Each entry has a timestamp and TTL
- **Max-Stale**: Expired entries can still be used as STALE fallback (up to max-stale)
- **Bounded**: LRU eviction when capacity (500) is reached
- **Thread-Safe**: Uses `ConcurrentHashMap` and `synchronized` for atomic operations
- **Injectable Clock**: Supports time manipulation in tests

### Code Example

```java
public class TtlCache<V> {
    private final Map<String, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    private final long ttlMs;
    private final long maxStaleMs;
    private final int maxEntries;
    private final Clock clock;
    
    public Result<V> get(String key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry == null) return Result.miss();
        
        long age = clock.millis() - entry.timestamp;
        
        if (age <= ttlMs) {
            // FRESH: within TTL
            return Result.fresh(entry.value, age);
        } else if (age <= maxStaleMs) {
            // STALE: expired but within max-stale
            return Result.stale(entry.value, age);
        } else {
            // EXPIRED: beyond max-stale, remove it
            cache.remove(key);
            return Result.miss();
        }
    }
    
    public void put(String key, V value) {
        synchronized (this) {
            if (cache.size() >= maxEntries && !cache.containsKey(key)) {
                evictOldest();  // LRU eviction
            }
            cache.put(key, new CacheEntry<>(value, clock.millis()));
        }
    }
}
```

### Cache-Aside Pattern Flow

```
1. Application checks cache: cache.get(key)
2. If FRESH or STALE: return cached value
3. If MISS: fetch from upstream
4. Put result in cache: cache.put(key, value)
5. Return value
```

### Usage

**In ResilientFetcher**:

```java
Result<T> cached = cache.get(cacheKey);

if (cached.isFresh()) {
    return new Result<>(cached.data(), Source.FRESH, cached.ageMs());
}

if (upstreamFails) {
    if (cached.isStale()) {
        return new Result<>(cached.data(), Source.STALE, cached.ageMs());
    }
}
```

### Benefits

- ✅ Reduces upstream load (most requests served from cache)
- ✅ Improves latency (memory lookup vs. HTTP call)
- ✅ Supports graceful degradation (STALE fallback)

### Trade-offs

- ⚠️ In-memory only (not shared between instances, resets on restart)
- ⚠️ Bounded size may evict entries early under high load

**Production Alternative**: Replace with Redis for distributed caching.

---

## 3. Fallback Chain

### Intent

Provide multiple levels of fallback when a primary operation fails, ensuring the system always returns a response even when all upstream services are down.

### Implementation

**Location**: [`backend/src/main/java/com/example/iss/resilience/ResilientFetcher.java`](../backend/src/main/java/com/example/iss/resilience/ResilientFetcher.java)

**Fallback Levels**:

1. **FRESH**: Live upstream data (within TTL)
2. **STALE**: Expired cache entry (within max-stale)
3. **MOCK**: Static JSON from `resources/mocks/`
4. **Forced Offline**: Skip upstream, use STALE or MOCK

### Code Example

```java
public Result<T> fetch(String cacheKey, Callable<T> upstream, boolean offline) {
    // Level 0: Forced offline mode
    if (offline) {
        return fetchOffline(cacheKey);
    }
    
    // Level 1: Try cache (FRESH)
    Result<T> cached = cache.get(cacheKey);
    if (cached.isFresh()) {
        return new Result<>(cached.data(), Source.FRESH, cached.ageMs());
    }
    
    // Level 2: Circuit breaker check
    if (breaker.isOpen()) {
        return fallbackFromCache(cacheKey);
    }
    
    // Level 3: Try upstream (with single-flight)
    try {
        T data = singleFlight.execute(cacheKey, upstream);
        breaker.recordSuccess();
        cache.put(cacheKey, data);
        return new Result<>(data, Source.FRESH, 0);
    } catch (Exception e) {
        breaker.recordFailure();
        return fallbackFromCache(cacheKey);
    }
}

private Result<T> fallbackFromCache(String cacheKey) {
    Result<T> cached = cache.get(cacheKey);
    
    // Level 4: STALE cache
    if (cached.isStale()) {
        return new Result<>(cached.data(), Source.STALE, cached.ageMs());
    }
    
    // Level 5: MOCK data
    T mock = mockSupplier.get();
    return new Result<>(mock, Source.MOCK, 0);
}
```

### Flow Diagram

```
┌─────────────────┐
│  offline=true?  │
└───────┬────────┘
        │
     Yes│   No
        │    │
        │    └────────────────────────┐
        │                             │
        │                    ┌────────┴─────────┐
        │                    │  Cache FRESH?   │
        │                    └───────┬─────────┘
        │                            │
        │                         Yes│   No
        │                            │    │
        │                    Return  │    └───────────────────┐
        │                     FRESH  │                        │
        │                            │               ┌────────┴────────┐
        └────────────────────────────────│ Breaker open? │
                                       └─────┬────────┘
        ┌──────────────────────────────────────┘    │
        │                                     Yes│   No
        │                                        │    │
        │                                        │    └─────────────┐
        │                                        │                │
        │                                        │       ┌────────┴────────┐
        │                                        │       │ Call upstream │
        │                                        │       └───────┬────────┘
        │                                        │               │
        │                                        │         Success│Fail
        │                                        │               │  │
        │                                        │      Return   │  │
        │                                        │       FRESH   │  │
        │                                        │               │  │
        │                                        └───────────────┘  │
        │                                                          │
        └──────────────────────────────────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │  Cache STALE?  │
                    └───────┬────────┘
                            │
                         Yes│   No
                            │    │
                    Return  │    └───────────────┐
                     STALE  │                    │
                            │           Return MOCK
```

### Benefits

- ✅ Always returns data (never fails completely)
- ✅ Transparent degradation (meta shows source)
- ✅ User-controlled offline mode

### Trade-offs

- ⚠️ MOCK data may be very stale (time-shifted for demos)
- ⚠️ Offline mode never refreshes even if cache is within TTL

---

## 4. Adapter

### Intent

Convert the interface of an external API into an interface expected by the application. Isolates upstream API details from the rest of the codebase.

### Implementation

**Locations**:
- [`OpenNotifyClient.java`](../backend/src/main/java/com/example/iss/client/OpenNotifyClient.java)
- [`PolluxClient.java`](../backend/src/main/java/com/example/iss/client/PolluxClient.java)
- [`OpenMeteoClient.java`](../backend/src/main/java/com/example/iss/client/OpenMeteoClient.java)

**Common Interface**: Each implements a domain-specific interface:

```java
public interface PositionClient {
    IssPosition fetch();  // No parameters; endpoint is fixed
}

public interface PassesClient {
    PassFeed fetch(double lat, double lon);
}

public interface CloudClient {
    CloudForecast fetch(double lat, double lon);
}
```

### Code Example: OpenNotifyClient

```java
public class OpenNotifyClient implements PositionClient {
    private final HttpJson http;
    private final String url;
    private final VelocityEstimator velocityEstimator;
    
    @Override
    public IssPosition fetch() {
        JsonNode root = http.get(url);  // Adapter uses HttpJson
        return parse(root);             // Converts JSON to domain model
    }
    
    static IssPosition parse(JsonNode root) {
        // Validate "message": "success"
        if (!"success".equals(root.path("message").asText())) {
            throw new HttpJsonException("Open-Notify API did not return success");
        }
        
        // Parse STRING coordinates to double
        JsonNode pos = root.path("iss_position");
        double lat = Double.parseDouble(pos.path("latitude").asText());
        double lon = Double.parseDouble(pos.path("longitude").asText());
        
        // Constant altitude (Open-Notify doesn't provide it)
        double altitude = 408.0;
        
        // Derive velocity from position delta (inside upstream callable)
        Instant observedAt = Instant.ofEpochSecond(root.path("timestamp").asLong());
        double velocity = velocityEstimator.estimate(lat, lon, observedAt);
        
        return new IssPosition(lat, lon, altitude, velocity, observedAt, NOTE);
    }
}
```

### Benefits

- ✅ **Dependency Inversion**: Domain layer depends on `PositionClient` interface, not concrete Open-Notify details
- ✅ **Testability**: Easy to mock adapters in tests
- ✅ **Separation of Concerns**: Parsing logic isolated in adapter
- ✅ **Swappable**: Can replace upstream API without changing domain code

### Real-World Example

If Open-Notify shuts down, we replace `OpenNotifyClient` with a different implementation (e.g., `WhereTheISSAtClient`) without touching `IssFacade` or domain logic.

---

## 5. Facade

### Intent

Provide a simplified, unified interface to a complex subsystem. Hides the complexity of orchestrating multiple clients and enriching data.

### Implementation

**Location**: [`backend/src/main/java/com/example/iss/service/IssFacade.java`](../backend/src/main/java/com/example/iss/service/IssFacade.java)

**Responsibilities**:

1. **Orchestrate 3 parallel upstream fetches** (position, passes, cloud) using virtual threads
2. **Enrich passes** with magnitude and cloud cover
3. **Filter passes** (remove past passes, compute observability)
4. **Generate alerts** for the next observable pass
5. **Return unified response** (`IssOverview`)

### Code Example

```java
public class IssFacade {
    private final ResilientFetcher<IssPosition> positionFetcher;
    private final ResilientFetcher<PassFeed> passesFetcher;
    private final ResilientFetcher<CloudForecast> cloudFetcher;
    private final AlertService alertService;
    private final MagnitudeCalculator magnitudeCalculator;
    private final Clock clock;
    
    public IssOverview getOverview(double lat, double lon, boolean offline) {
        Instant now = clock.instant();
        
        // Parallel fetch using virtual threads (JDK 21)
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var positionTask = scope.fork(() -> 
                positionFetcher.fetch("position", positionCallable, offline)
            );
            var passesTask = scope.fork(() -> 
                passesFetcher.fetch(cacheKey(lat, lon), () -> passesClient.fetch(lat, lon), offline)
            );
            var cloudTask = scope.fork(() -> 
                cloudFetcher.fetch(cacheKey(lat, lon), () -> cloudClient.fetch(lat, lon), offline)
            );
            
            scope.join();
            
            Result<IssPosition> posResult = positionTask.get();
            Result<PassFeed> passResult = passesTask.get();
            Result<CloudForecast> cloudResult = cloudTask.get();
            
            // Enrich passes with magnitude and cloud
            List<EnrichedPass> enriched = enrichPasses(
                passResult.data().passes(), 
                cloudResult.data(), 
                now
            );
            
            // Find next observable pass
            EnrichedPass nextVisible = findNextObservable(enriched, now);
            
            // Generate alerts
            List<String> alerts = nextVisible != null 
                ? alertService.build(nextVisible.pass(), nextVisible.cloudCoverPct(), 
                                     nextVisible.magnitude(), now)
                : List.of();
            
            return new IssOverview(
                posResult.data(),
                enriched,
                nextVisible,
                nextVisible == null ? "No visible pass in the returned predictions" : null,
                alerts,
                toMeta(posResult),
                toMeta(passResult, passResult.data()),
                toMeta(cloudResult)
            );
        }
    }
}
```

### Benefits

- ✅ **Simplified Controller**: `IssController` just calls `facade.getOverview()`
- ✅ **Centralized Orchestration**: All complex logic in one place
- ✅ **Parallel Execution**: Fetches run concurrently (faster response)
- ✅ **Testable**: Can mock all three fetchers independently

### Without Facade

If we didn't have a Facade, `IssController` would need to:

- Call 3 different fetchers
- Wait for results
- Enrich passes with cloud and magnitude
- Filter and sort passes
- Generate alerts
- Map everything to DTOs

**Result**: 200+ line controller method, hard to test.

**With Facade**: Controller is < 50 lines, facade is unit-testable.

---

## 6. Single-Flight

### Intent

Deduplicate concurrent identical requests to an expensive resource. If multiple threads request the same key simultaneously, only one actually calls the upstream; the others wait and share the result.

### Implementation

**Location**: [`backend/src/main/java/com/example/iss/resilience/SingleFlight.java`](../backend/src/main/java/com/example/iss/resilience/SingleFlight.java)

**Key Features**:
- **Thread-Safe**: Uses `ConcurrentHashMap` for in-flight tracking
- **Future-Based**: Returns `CompletableFuture` for waiting threads
- **Automatic Cleanup**: Removes in-flight entry in `finally` block
- **Exception Propagation**: All waiters receive the same exception if call fails

### Code Example

```java
public class SingleFlight {
    private final ConcurrentMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();
    
    @SuppressWarnings("unchecked")
    public <T> T execute(String key, Callable<T> callable) throws Exception {
        CompletableFuture<Object> future = new CompletableFuture<>();
        CompletableFuture<Object> existing = inFlight.putIfAbsent(key, future);
        
        if (existing != null) {
            // Another thread is already fetching this key; wait for it
            return (T) existing.get();
        }
        
        try {
            T result = callable.call();
            future.complete(result);  // Wake up waiting threads
            return result;
        } catch (Exception e) {
            future.completeExceptionally(e);  // All waiters get the exception
            throw e;
        } finally {
            inFlight.remove(key);  // Clean up (NOT a cache!)
        }
    }
}
```

### Scenario: 100 Concurrent Requests

**Without Single-Flight**:

```
Thread 1: Call upstream for lat=28.61,lon=77.21 → 200ms
Thread 2: Call upstream for lat=28.61,lon=77.21 → 200ms
Thread 3: Call upstream for lat=28.61,lon=77.21 → 200ms
...
Thread 100: Call upstream for lat=28.61,lon=77.21 → 200ms

Total upstream calls: 100
Upstream load: 100 requests in 200ms
```

**With Single-Flight**:

```
Thread 1: Call upstream for lat=28.61,lon=77.21 → 200ms
Thread 2-100: Wait for Thread 1's result

Total upstream calls: 1
Upstream load: 1 request
All threads get result after 200ms
```

### Important: Not a Cache

Single-Flight is **not a cache**:

- In-flight map is cleared in `finally`
- The next sequential request (after the first completes) hits upstream again
- Only deduplicates **concurrent** requests

**Cache** (TtlCache) handles sequential requests.

### Benefits

- ✅ Reduces upstream load during traffic spikes
- ✅ Prevents "thundering herd" problem
- ✅ Simple implementation (< 50 lines)

### Trade-offs

- ⚠️ All waiting threads are blocked until call completes (or times out)
- ⚠️ If upstream is slow, many threads may accumulate

---

## Pattern Interactions

The patterns work together to provide resilient data fetching:

```
Request → ResilientFetcher (Fallback Chain)
              │
              ├─────── Check TtlCache (Cache-Aside)
              │          │
              │          ├─── FRESH? → Return immediately
              │          └─── MISS/EXPIRED? → Continue
              │
              ├─────── Check CircuitBreaker
              │          │
              │          ├─── OPEN? → Skip upstream, go to fallback
              │          └─── CLOSED? → Continue
              │
              ├─────── SingleFlight.execute()
              │          │
              │          ├─── In-flight? → Wait for other thread
              │          └─── Not in-flight? → Call upstream
              │                              │
              │                              ├─── Success? → Record success, cache result, return FRESH
              │                              └─── Failure? → Record failure, go to fallback
              │
              └─────── Fallback
                         │
                         ├─── Cache STALE? → Return STALE
                         └─── No cache? → Return MOCK (from MockData via Adapter)
```

### Execution Flow Example

**Scenario**: 3 concurrent requests for lat=28.61, lon=77.21; upstream is slow (500ms)

1. **Thread 1**: Checks cache → MISS → Breaker CLOSED → Starts upstream call, registers in SingleFlight
2. **Thread 2**: Checks cache → MISS → Breaker CLOSED → Sees Thread 1 in-flight, waits
3. **Thread 3**: Checks cache → MISS → Breaker CLOSED → Sees Thread 1 in-flight, waits
4. **500ms later**: Thread 1's call completes → Writes to cache → Returns FRESH → Wakes Thread 2 & 3
5. **Thread 2 & 3**: Receive result from SingleFlight, return FRESH

**Result**:
- ✅ Only 1 upstream call
- ✅ All threads get FRESH data
- ✅ Total time: ~500ms (not 1500ms)

### Pattern Summary Table

| Pattern | Purpose | Location | Benefit |
|---------|---------|----------|----------|
| **Circuit Breaker** | Fail fast when upstream is down | `CircuitBreaker.java` | Prevents cascading failures |
| **Cache-Aside** | Reduce upstream load | `TtlCache.java` | Improves latency & resilience |
| **Fallback Chain** | Always return data | `ResilientFetcher.java` | Graceful degradation |
| **Adapter** | Isolate upstream APIs | `*Client.java` | Dependency inversion, testability |
| **Facade** | Simplify orchestration | `IssFacade.java` | Clean API, parallel execution |
| **Single-Flight** | Deduplicate concurrent calls | `SingleFlight.java` | Reduces upstream load spikes |

---

## Testing Patterns

Each pattern has dedicated tests:

- **CircuitBreaker**: [`CircuitBreakerTest.java`](../backend/src/test/java/com/example/iss/resilience/CircuitBreakerTest.java)
- **TtlCache**: [`TtlCacheTest.java`](../backend/src/test/java/com/example/iss/resilience/TtlCacheTest.java)
- **ResilientFetcher**: [`ResilientFetcherTest.java`](../backend/src/test/java/com/example/iss/resilience/ResilientFetcherTest.java)
- **SingleFlight**: [`SingleFlightTest.java`](../backend/src/test/java/com/example/iss/resilience/SingleFlightTest.java)
- **IssFacade**: [`IssFacadeTest.java`](../backend/src/test/java/com/example/iss/service/IssFacadeTest.java)

See test files for concrete examples of each pattern in action.

---

**Built with SOLID principles and proven patterns for resilient distributed systems.**
