package com.example.iss.api;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * SecurityHeadersFilter: applies the fixed header set from Step 6 decision 3 to every
 * response, plus Cache-Control: no-store on /api/** only.
 */
class SecurityHeadersFilterTest {

    private SecurityHeadersFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
    }

    @Nested
    @DisplayName("headers applied to every response")
    class HeadersAppliedToEveryResponse {

        @Test
        @DisplayName("given any request, when filtered, then the response carries all required security headers with the exact expected values")
        void givenAnyRequest_whenFiltered_thenAllSecurityHeadersPresent() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/docs");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
            assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
            assertThat(response.getHeader("Permissions-Policy")).isEqualTo("geolocation=(self)");
            String csp = response.getHeader("Content-Security-Policy");
            assertThat(csp).contains("default-src 'self'");
            assertThat(csp).contains("style-src 'self' 'unsafe-inline'");
            assertThat(csp).contains("img-src 'self' data:");
            assertThat(csp).contains("connect-src 'self'");
            assertThat(csp).contains("frame-ancestors 'none'");
            assertThat(csp).contains("base-uri 'self'");
            assertThat(csp).contains("form-action 'self'");
            verify(chain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Cache-Control on /api/** only")
    class CacheControlOnApiOnly {

        @Test
        @DisplayName("given a request to /api/v1/iss/overview, when filtered, then Cache-Control is no-store")
        void givenApiRequest_whenFiltered_thenCacheControlNoStore() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/iss/overview");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, mock(FilterChain.class));

            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        }

        @Test
        @DisplayName("given a request to a non-api path, when filtered, then Cache-Control is not forced to no-store")
        void givenNonApiRequest_whenFiltered_thenCacheControlNotForced() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/docs");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, mock(FilterChain.class));

            assertThat(response.getHeader("Cache-Control")).isNotEqualTo("no-store");
        }
    }
}
