package com.example.iss.config;

import com.example.iss.api.RateLimitFilter;
import com.example.iss.api.SecurityHeadersFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Registers the hand-written servlet filters (PROMPT.md section 8). SecurityHeadersFilter
 * runs on every request; RateLimitFilter scopes itself to /api/** internally but is
 * registered broadly so it always sees the request path.
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilterRegistration() {
        FilterRegistrationBean<SecurityHeadersFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SecurityHeadersFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(Clock clock, UpstreamProperties props) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(clock, props.getRateLimit().getPerMinute(),
                props.getRateLimit().isTrustedProxy()));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);
        return registration;
    }
}
