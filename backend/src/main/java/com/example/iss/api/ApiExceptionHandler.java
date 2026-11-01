package com.example.iss.api;

import com.example.iss.api.dto.ErrorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * One {@link ErrorDto} shape for every error status (400/404/405/429/500 - Step 6
 * decision 6). Never echoes exception messages/class names/stack traces for unexpected
 * errors; only {@link InvalidLocationException}'s own message (which this app controls
 * and never includes lat/lon values verbatim per the no-lat/lon-logging rule, addition e)
 * is shown verbatim.
 *
 * <p>BUG FIX: the original catch-all {@code @ExceptionHandler(Exception.class)} was
 * swallowing Spring MVC's own exceptions (missing parameter, type mismatch, unknown
 * route, wrong HTTP method) into a generic 500. Each of those now has its own specific
 * handler, registered BEFORE the generic fallback, so only genuinely unexpected failures
 * reach {@link #handleGeneric(Exception)}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidLocationException.class)
    public ResponseEntity<ErrorDto> handleInvalidLocation(InvalidLocationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto(ex.getMessage(), 400));
    }

    /** A required query parameter (e.g. lat/lon) was not supplied at all. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorDto> handleMissingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto("Missing required parameter: " + ex.getParameterName(), 400));
    }

    /** A query parameter could not be converted to its target type by Spring's binder
     *  (e.g. lat=abc, lat=NaN, lat=Infinity when bound to a numeric type, offline=maybe
     *  when bound to a boolean/enum-like type elsewhere in the app). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDto> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto("Invalid value for parameter: " + ex.getName(), 400));
    }

    /** A malformed request body (not used by this GET-only API today, but handled
     *  defensively so a future body-accepting endpoint fails cleanly as 400, not 500). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> handleMalformedBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto("Malformed request body", 400));
    }

    /** Unknown URL under our context (Spring 6 / Boot 3's NoResourceFoundException,
     *  raised when no handler and no static resource matches). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorDto> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorDto("Resource not found", 404));
    }

    /** A handler exists for the path but not for the HTTP method used; always sends
     *  Allow with the methods that ARE supported. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorDto> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers)
                .body(new ErrorDto("Method not allowed", 405));
    }

    /** Everything else: genuinely unexpected failures only reach here now. Logged
     *  server-side only; never echoed to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleGeneric(Exception ex) {
        log.error("Unhandled exception while processing request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorDto("Internal server error", 500));
    }
}
