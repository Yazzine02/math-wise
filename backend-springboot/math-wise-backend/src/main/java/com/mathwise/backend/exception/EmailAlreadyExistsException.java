package com.mathwise.backend.exception;

/**
 * Thrown by {@code AuthController.register} when the email is already in use.
 * Mapped to HTTP 409 + code {@code EMAIL_ALREADY_EXISTS} by
 * {@link GlobalExceptionHandler}.
 */
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("An account with email '" + email + "' already exists");
    }
}
