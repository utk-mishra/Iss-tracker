package com.example.iss;

import com.example.iss.client.CloudClient;
import com.example.iss.client.PassesClient;
import com.example.iss.client.PositionClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Step 6 addition g: a context-load smoke test that makes NO network calls. All three
 * upstream clients are mocked (but never stubbed/invoked), so if the context starts
 * successfully it proves every bean (ResilienceConfig, FilterConfig, WebConfig,
 * IssController, ApiExceptionHandler, etc.) wires correctly without ever reaching
 * the real Open-Notify/Pollux/Open-Meteo endpoints.
 */
@SpringBootTest
class ApplicationContextSmokeTest {

    @MockBean
    private PositionClient positionClient;
    @MockBean
    private PassesClient passesClient;
    @MockBean
    private CloudClient cloudClient;

    @Test
    @DisplayName("given the full Spring context, when it starts, then it loads successfully with zero network calls")
    void givenFullContext_whenStarted_thenLoadsSuccessfully() {
        // No assertions needed beyond the context loading: @SpringBootTest already
        // fails the test if the ApplicationContext cannot be created. Mocked clients
        // guarantee no real HTTP call is ever attempted during context startup.
    }
}
