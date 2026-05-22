package com.mathwise.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error envelope returned by {@code GlobalExceptionHandler}.
 *
 * <p>Replaces Spring's default error body (which includes a stack trace and
 * differs in shape across exception types). Flutter switches on {@code code}
 * to decide how to present the error to the user.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code status} — HTTP status code (400, 404, 409, 503, …)</li>
 *   <li>{@code code} — short stable identifier
 *       ({@code "INVALID_INPUT"}, {@code "VALIDATION_FAILED"},
 *       {@code "AI_SERVICE_UNAVAILABLE"}, …) that the client switches on.
 *       Kept stable across releases — message text may change but codes
 *       won't.</li>
 *   <li>{@code message} — human-readable explanation for display</li>
 *   <li>{@code timestamp} — server time of the failure, ISO-8601</li>
 *   <li>{@code path} — request path that produced the error</li>
 *   <li>{@code fieldErrors} — optional, only populated for validation
 *       failures: a map of {@code field-name → first-violation-message}.
 *       Omitted from the JSON when null (so the wire format for non-validation
 *       errors stays unchanged).</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDto {

    private int status;
    private String code;
    private String message;
    private Instant timestamp;
    private String path;
    private Map<String, String> fieldErrors;

    public ErrorResponseDto(int status, String code, String message, String path) {
        this(status, code, message, path, null);
    }

    public ErrorResponseDto(int status, String code, String message, String path,
                             Map<String, String> fieldErrors) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.timestamp = Instant.now();
        this.path = path;
        this.fieldErrors = fieldErrors;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public String getPath() { return path; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
