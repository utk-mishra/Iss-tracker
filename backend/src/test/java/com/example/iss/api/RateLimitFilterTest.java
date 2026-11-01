package com.example.iss.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * RateLimitFilter: hand-written per-IP token bucket (PROMPT.md section 8, Step 6
 * decisions 2/7 and additions a). Injectable Clock, no Thread.sleep. Applies to
 * /api/** only. X-Forwarded-For honoured only when TRUSTED_PROXY=true.
 */
class RateLimitFilterTest {

    private static final int LIMIT_PER_MINUTE = 120;

    private MutableTestClock clock;
    private RateLimitFilter filter;

    /** Minimal mutable Clock for deterministic refill testing, mirroring the
     * project's existing MutableClock pattern used in resilience/domain tests. */
    static final class MutableTestClock extends Clock {
        private Instant instant;
        MutableTestClock(Instant start) { this.instant = start; }
        void advance(Duration d) { this.instant = this.instant.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    @BeforeEach
    void setUp() {
        clock = new MutableTestClock(Instant.parse("2026-11-01T00:00:00Z"));
        filter = new RateLimitFilter(clock, LIMIT_PER_MINUTE, false);
    }

    private MockHttpServletRequest apiRequest(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/iss/overview");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Nested
    @DisplayName("within the limit")
    class WithinLimit {

        @Test
        @DisplayName("given requests under the per-minute limit, when filtered, then the chain is invoked and no 429 is returned")
        void givenUnderLimit_whenFiltered_thenChainInvokedNo429() throws ServletException, IOException {
            MockHttpServletRequest request = apiRequest("1.2.3.4");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    @Nested
    @DisplayName("over the limit")
    class OverLimit {

        @Test
        @DisplayName("given more requests than the per-minute limit from the same IP, when filtered, then the extra request gets 429 with Retry-After")
        void givenOverLimit_whenFiltered_then429WithRetryAfter() throws ServletException, IOException {
            String ip = "5.6.7.8";
            FilterChain chain = mock(FilterChain.class);
            for (int i = 0; i < LIMIT_PER_MINUTE; i++) {
                filter.doFilter(apiRequest(ip), new MockHttpServletResponse(), chain);
            }

            MockHttpServletRequest overLimitRequest = apiRequest(ip);
            MockHttpServletResponse overLimitResponse = new MockHttpServletResponse();
            filter.doFilter(overLimitRequest, overLimitResponse, chain);

            assertThat(overLimitResponse.getStatus()).isEqualTo(429);
            assertThat(overLimitResponse.getHeader("Retry-After")).isNotNull();
        }

        @Test
        @DisplayName("given the bucket is exhausted, when the chain is checked, then it was never invoked for the 429 response")
        void givenBucketExhausted_whenChecked_thenChainNeverInvokedForThatRequest() throws ServletException, IOException {
            String ip = "9.9.9.9";
            for (int i = 0; i < LIMIT_PER_MINUTE; i++) {
                filter.doFilter(apiRequest(ip), new MockHttpServletResponse(), mock(FilterChain.class));
            }

            FilterChain lastChain = mock(FilterChain.class);
            filter.doFilter(apiRequest(ip), new MockHttpServletResponse(), lastChain);

            verify(lastChain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("refill after the configured window")
    class RefillAfterWindow {

        @Test
        @DisplayName("given the bucket is exhausted, when a full minute elapses on the injected clock, then requests are allowed again")
        void givenExhausted_whenOneMinuteElapses_thenAllowedAgain() throws ServletException, IOException {
            String ip = "10.10.10.10";
            for (int i = 0; i < LIMIT_PER_MINUTE; i++) {
                filter.doFilter(apiRequest(ip), new MockHttpServletResponse(), mock(FilterChain.class));
            }
            MockHttpServletResponse exhaustedResponse = new MockHttpServletResponse();
            filter.doFilter(apiRequest(ip), exhaustedResponse, mock(FilterChain.class));
            assertThat(exhaustedResponse.getStatus()).isEqualTo(429);

            clock.advance(Duration.ofMinutes(1));

            MockHttpServletResponse refilledResponse = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(apiRequest(ip), refilledResponse, chain);

            verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            assertThat(refilledResponse.getStatus()).isNotEqualTo(429);
        }
    }

    @Nested
    @DisplayName("independent buckets per IP")
    class IndependentBucketsPerIp {

        @Test
        @DisplayName("given one IP exhausts its bucket, when a different IP requests, then it is still allowed")
        void givenOneIpExhausted_whenDifferentIpRequests_thenStillAllowed() throws ServletException, IOException {
            String exhaustedIp = "11.11.11.11";
            for (int i = 0; i < LIMIT_PER_MINUTE; i++) {
                filter.doFilter(apiRequest(exhaustedIp), new MockHttpServletResponse(), mock(FilterChain.class));
            }

            MockHttpServletResponse otherIpResponse = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(apiRequest("22.22.22.22"), otherIpResponse, chain);

            verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            assertThat(otherIpResponse.getStatus()).isNotEqualTo(429);
        }
    }

    @Nested
    @DisplayName("scope: /api/** only")
    class ScopeApiOnly {

        @Test
        @DisplayName("given a request to /actuator/health, when filtered, then it is never rate-limited")
        void givenActuatorHealthRequest_whenFiltered_thenNeverRateLimited() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            request.setRemoteAddr("33.33.33.33");
            FilterChain chain = mock(FilterChain.class);

            for (int i = 0; i < LIMIT_PER_MINUTE + 10; i++) {
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(request, response, chain);
                assertThat(response.getStatus()).isNotEqualTo(429);
            }
        }
    }

    @Nested
    @DisplayName("X-Forwarded-For trust")
    class XForwardedForTrust {

        @Test
        @DisplayName("given TRUSTED_PROXY=false and a spoofed X-Forwarded-For header, when different fake IPs are sent from the same remote address, then they all share one bucket (no spoofing)")
        void givenUntrustedProxyAndSpoofedHeader_whenDifferentFakeIps_thenShareOneBucket() throws ServletException, IOException {
            RateLimitFilter untrustedFilter = new RateLimitFilter(clock, 5, false);
            FilterChain chain = mock(FilterChain.class);
            String realRemoteAddr = "44.44.44.44";

            for (int i = 0; i < 5; i++) {
                MockHttpServletRequest request = apiRequest(realRemoteAddr);
                request.addHeader("X-Forwarded-For", "1.1.1." + i);
                untrustedFilter.doFilter(request, new MockHttpServletResponse(), chain);
            }

            MockHttpServletRequest sixthRequest = apiRequest(realRemoteAddr);
            sixthRequest.addHeader("X-Forwarded-For", "9.9.9.9");
            MockHttpServletResponse sixthResponse = new MockHttpServletResponse();
            untrustedFilter.doFilter(sixthRequest, sixthResponse, chain);

            assertThat(sixthResponse.getStatus()).isEqualTo(429);
        }

        @Test
        @DisplayName("given TRUSTED_PROXY=true, when X-Forwarded-For has multiple entries, then the right-most entry is used as the bucket key")
        void givenTrustedProxy_whenMultipleForwardedEntries_thenRightMostEntryUsed() throws ServletException, IOException {
            RateLimitFilter trustedFilter = new RateLimitFilter(clock, 3, true);
            FilterChain chain = mock(FilterChain.class);

            for (int i = 0; i < 3; i++) {
                MockHttpServletRequest request = apiRequest("127.0.0.1");
                request.addHeader("X-Forwarded-For", "203.0.113.1, 70.41.3.18");
                trustedFilter.doFilter(request, new MockHttpServletResponse(), chain);
            }

            MockHttpServletRequest fourthRequest = apiRequest("127.0.0.1");
            fourthRequest.addHeader("X-Forwarded-For", "203.0.113.1, 70.41.3.18");
            MockHttpServletResponse fourthResponse = new MockHttpServletResponse();
            trustedFilter.doFilter(fourthRequest, fourthResponse, chain);

            assertThat(fourthResponse.getStatus()).isEqualTo(429);

            MockHttpServletRequest differentRightmost = apiRequest("127.0.0.1");
            differentRightmost.addHeader("X-Forwarded-For", "203.0.113.1, 99.99.99.99");
            MockHttpServletResponse differentResponse = new MockHttpServletResponse();
            trustedFilter.doFilter(differentRightmost, differentResponse, chain);

            assertThat(differentResponse.getStatus()).isNotEqualTo(429);
        }
    }
}
