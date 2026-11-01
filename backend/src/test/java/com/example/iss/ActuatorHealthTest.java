package com.example.iss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: GET /actuator/health must be reachable and report UP on a bare Spring Boot
 * context, with no custom indicators required for Step 1. This is the first failing
 * test written before the skeleton/config existed (TDD step 1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorHealthTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Nested
    @DisplayName("GET /actuator/health")
    class HealthEndpoint {

        @Test
        @DisplayName("given the application has started, when health is requested, then it returns 200 UP")
        void givenAppStarted_whenHealthRequested_thenReturns200Up() {
            ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"status\":\"UP\"");
        }
    }
}
