package com.mathwise.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Uniform error envelope returned by {@code GlobalExceptionHandler}.
 *
 * <p>Replaces Spring's default error body (which includes a stack trace and
 * differs in shape across exception types). Flutter switches on {@code code}
 * to decide how to present the error to the user.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code status} — HTTP status code (400, 404, 503, …)</li>
 *   <li>{@code code} — short stable identifier
 *       ({@code "INVALID_INPUT"}, {@code "AI_SERVICE_UNAVAILABLE"}, …) that
 *       the client switches on. Kept stable across releases — message text
 *       may change but codes won't.</li>
 *   <li>{@code message} — human-readable explanation for display</li>
 *   <li>{@code timestamp} — server time of the failure, ISO-8601</li>
 *   <li>{@code path} — request path that produced the error (for logs +
 *       client-side debugging). Optional — only set when available</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDto {

    private int status;
    private String code;
    private String message;
    private Instant timestamp;
    private String path;

    public ErrorResponseDto(int status, String code, String message, String path) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.timestamp = Instant.now();
        this.path = path;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public String getPath() { return path; }
}
