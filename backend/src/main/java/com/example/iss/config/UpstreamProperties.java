package com.example.iss.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the iss.* configuration tree from application.yml (PROMPT.md sections 2, 8).
 * All upstream URLs, TTLs, max-stale windows, circuit-breaker and HTTP settings are
 * environment-overridable; see application.yml for the env var mapping.
 */
@ConfigurationProperties(prefix = "iss")
public class UpstreamProperties {

    public static class Upstream {
        public static class OpenNotify {
            private String url;
            public String getUrl() { return url; }
            public void setUrl(String url) { this.url = url; }
        }
        public static class Pollux {
            private String url;
            private int passCount;
            public String getUrl() { return url; }
            public void setUrl(String url) { this.url = url; }
            public int getPassCount() { return passCount; }
            public void setPassCount(int passCount) { this.passCount = passCount; }
        }
        public static class OpenMeteo {
            private String url;
            public String getUrl() { return url; }
            public void setUrl(String url) { this.url = url; }
        }

        private OpenNotify openNotify = new OpenNotify();
        private Pollux pollux = new Pollux();
        private OpenMeteo openMeteo = new OpenMeteo();

        public OpenNotify getOpenNotify() { return openNotify; }
        public Pollux getPollux() { return pollux; }
        public OpenMeteo getOpenMeteo() { return openMeteo; }
    }

    public static class CacheSettings {
        public static class Window {
            private long ttlSeconds;
            private long maxStaleSeconds;
            public long getTtlSeconds() { return ttlSeconds; }
            public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
            public long getMaxStaleSeconds() { return maxStaleSeconds; }
            public void setMaxStaleSeconds(long maxStaleSeconds) { this.maxStaleSeconds = maxStaleSeconds; }
        }

        private Window position = new Window();
        private Window passes = new Window();
        private Window cloud = new Window();
        private int maxEntries;

        public Window getPosition() { return position; }
        public Window getPasses() { return passes; }
        public Window getCloud() { return cloud; }
        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
    }

    public static class CircuitBreakerSettings {
        private int failureThreshold;
        private int cooldownSeconds;
        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
        public int getCooldownSeconds() { return cooldownSeconds; }
        public void setCooldownSeconds(int cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
    }

    public static class HttpSettings {
        private int timeoutSeconds;
        private int retryCount;
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    }

    public static class SecuritySettings {
        public static class Cors {
            private String allowedOrigins = "";
            public String getAllowedOrigins() { return allowedOrigins; }
            public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
        }
        private Cors cors = new Cors();
        public Cors getCors() { return cors; }
    }

    public static class RateLimitSettings {
        private int perMinute = 120;
        private boolean trustedProxy = false;
        public int getPerMinute() { return perMinute; }
        public void setPerMinute(int perMinute) { this.perMinute = perMinute; }
        public boolean isTrustedProxy() { return trustedProxy; }
        public void setTrustedProxy(boolean trustedProxy) { this.trustedProxy = trustedProxy; }
    }

    private Upstream upstream = new Upstream();
    private CacheSettings cache = new CacheSettings();
    private CircuitBreakerSettings circuitBreaker = new CircuitBreakerSettings();
    private HttpSettings http = new HttpSettings();
    private SecuritySettings security = new SecuritySettings();
    private RateLimitSettings rateLimit = new RateLimitSettings();

    public Upstream getUpstream() { return upstream; }
    public CacheSettings getCache() { return cache; }
    public CircuitBreakerSettings getCircuitBreaker() { return circuitBreaker; }
    public HttpSettings getHttp() { return http; }
    public SecuritySettings getSecurity() { return security; }
    public RateLimitSettings getRateLimit() { return rateLimit; }
}
