package com.example.iss.api;

import com.example.iss.api.dto.ErrorDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiExceptionHandler: one ErrorDto shape for 400 and 500 (Step 6 decision 6); never
 * leaks stack traces or exception class names.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Nested
    @DisplayName("invalid location")
    class InvalidLocation {

        @Test
        @DisplayName("given an InvalidLocationException, when handled, then a 400 ErrorDto is returned with the exception's message")
        void givenInvalidLocationException_whenHandled_then400ErrorDto() {
            InvalidLocationException ex = new InvalidLocationException("lat must be in [-90,90]");

            ResponseEntity<ErrorDto> response = handler.handleInvalidLocation(ex);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody().status()).isEqualTo(400);
            assertThat(response.getBody().error()).isEqualTo("lat must be in [-90,90]");
        }
    }

    @Nested
    @DisplayName("generic uncaught exception")
    class GenericUncaughtException {

        @Test
        @DisplayName("given an arbitrary RuntimeException, when handled, then a 500 ErrorDto is returned with a generic message, no exception details leaked")
        void givenArbitraryRuntimeException_whenHandled_then500GenericErrorDto() {
            RuntimeException ex = new RuntimeException("some internal secret detail");

            ResponseEntity<ErrorDto> response = handler.handleGeneric(ex);

            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(response.getBody().status()).isEqualTo(500);
            assertThat(response.getBody().error()).doesNotContain("some internal secret detail");
            assertThat(response.getBody().error()).isEqualTo("Internal server error");
        }
    }

    @Nested
    @DisplayName("missing required parameter (bug fix)")
    class MissingRequiredParameter {

        @Test
        @DisplayName("given a MissingServletRequestParameterException for lat, when handled, then a 400 ErrorDto names the parameter")
        void givenMissingLatParameter_whenHandled_then400NamesParameter() {
            MissingServletRequestParameterException ex =
                    new MissingServletRequestParameterException("lat", "String");

            ResponseEntity<ErrorDto> response = handler.handleMissingParameter(ex);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody().status()).isEqualTo(400);
            assertThat(response.getBody().error()).contains("lat");
        }
    }

    @Nested
    @DisplayName("parameter type mismatch (bug fix: lat=abc, lat=NaN, lat=Infinity, offline=maybe)")
    class ParameterTypeMismatch {

        @Test
        @DisplayName("given a MethodArgumentTypeMismatchException for lat, when handled, then a 400 ErrorDto names the parameter")
        void givenTypeMismatchForLat_whenHandled_then400NamesParameter() {
            MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                    "abc", String.class, "lat", (MethodParameter) null, new NumberFormatException("abc"));

            ResponseEntity<ErrorDto> response = handler.handleTypeMismatch(ex);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody().status()).isEqualTo(400);
            assertThat(response.getBody().error()).contains("lat");
        }
    }

    @Nested
    @DisplayName("unknown URL (bug fix)")
    class UnknownUrl {

        @Test
        @DisplayName("given a NoResourceFoundException, when handled, then a 404 ErrorDto is returned")
        void givenNoResourceFoundException_whenHandled_then404ErrorDto() {
            NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/v1/iss/nope");

            ResponseEntity<ErrorDto> response = handler.handleNotFound(ex);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(response.getBody().status()).isEqualTo(404);
            assertThat(response.getBody().error()).doesNotContain("NoResourceFoundException");
        }
    }

    @Nested
    @DisplayName("wrong HTTP method (bug fix)")
    class WrongHttpMethod {

        @Test
        @DisplayName("given an HttpRequestMethodNotSupportedException, when handled, then a 405 ErrorDto is returned with an Allow header")
        void givenMethodNotSupported_whenHandled_then405WithAllowHeader() {
            HttpRequestMethodNotSupportedException ex =
                    new HttpRequestMethodNotSupportedException("POST", java.util.List.of("GET"));

            ResponseEntity<ErrorDto> response = handler.handleMethodNotAllowed(ex);

            assertThat(response.getStatusCode().value()).isEqualTo(405);
            assertThat(response.getBody().status()).isEqualTo(405);
            assertThat(response.getHeaders().getAllow()).isNotEmpty();
        }
    }
}
