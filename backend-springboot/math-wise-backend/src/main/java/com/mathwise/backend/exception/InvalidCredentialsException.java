package com.mathwise.backend.exception;

/**
 * Thrown by {@code AuthController.login} when either the email isn't
 * registered or the password doesn't match the stored hash. Mapped to
 * HTTP 401 + code {@code INVALID_CREDENTIALS} by
 * {@link GlobalExceptionHandler}.
 *
 * <p>Deliberately a single exception type for both "unknown email" and
 * "wrong password" — leaking the distinction would let an attacker enumerate
 * registered emails.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
