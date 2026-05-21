package com.mathwise.backend.exception;

import com.mathwise.backend.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Centralised exception → HTTP-response mapping.
 *
 * <p>Before this class, domain exceptions thrown by the service layer fell
 * through to Spring's default error handler, which returns a 500 with a
 * partial stack trace as JSON. That gave the Flutter client no clean signal
 * to switch on, and leaked internal types over the wire.
 *
 * <p>Every handler here returns an {@link ErrorResponseDto}. The
 * machine-readable {@code code} field is the stable contract — message text
 * can change but codes won't, so the client can write robust
 * branch-on-code logic.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Validation problems and "unknown node code" style errors —
     * caller-side mistakes that map to 400.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Invalid input on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "INVALID_INPUT", ex.getMessage(), request);
    }

    /**
     * "No exercises for node" / "No knowledge nodes seeded" — server-side
     * data problems. Returned as 404 so the client can show a "no content"
     * UI rather than a generic crash.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        log.error("Server-state error on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "RESOURCE_MISSING", ex.getMessage(), request);
    }

    /**
     * FastAPI is unreachable (TCP refused, DNS failure, timeout). Mapped to
     * 503 so the client can show a "service temporarily unavailable" UI
     * instead of "something went wrong."
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponseDto> handleResourceAccess(
            ResourceAccessException ex, HttpServletRequest request) {
        log.error("AI service unreachable on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_UNAVAILABLE",
                "AI service is temporarily unavailable. Please try again.", request);
    }

    /**
     * FastAPI returned a 4xx/5xx that wasn't already a {@link ResponseStatusException}.
     * Distinct code from {@code AI_SERVICE_UNAVAILABLE} because the meaning is
     * different: the service is up but failed to process the request.
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponseDto> handleRestClient(
            RestClientException ex, HttpServletRequest request) {
        log.error("AI service error on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "AI_SERVICE_ERROR",
                "AI service returned an unexpected response. Please try again.", request);
    }

    /**
     * Pass-through for exceptions that already carry a deliberate HTTP status
     * (e.g. {@code throw new ResponseStatusException(HttpStatus.NOT_FOUND, ...)}
     * in {@code CourseController.getLesson}).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDto> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        log.warn("ResponseStatusException on {}: {} {}", request.getRequestURI(), status, ex.getReason());
        return build(status, status.name(),
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(), request);
    }

    /**
     * Last-resort handler. Logs the full stack trace server-side but does NOT
     * leak it over the wire — clients receive a generic message.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again.", request);
    }

    private ResponseEntity<ErrorResponseDto> build(HttpStatus status, String code,
                                                    String message, HttpServletRequest request) {
        ErrorResponseDto body = new ErrorResponseDto(
                status.value(), code, message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
