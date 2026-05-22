package com.mathwise.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT generation + verification.
 *
 * <p>The signing secret is now injected from the {@code jwt.secret} property
 * (resolved from the {@code JWT_SECRET} env var, with a development-only
 * placeholder default in {@code application.properties}). The previous
 * implementation hardcoded the secret in source — anyone with read access to
 * the repo could forge tokens for any user.
 */
@Component
public class JwtUtil {

    // 24h. Token rotation / refresh-tokens are an Auth v2 concern (not in
    // release/0.0.1) — for now this is the entire session lifetime.
    private static final long EXPIRATION_TIME = 86400000L;

    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        // HMAC-SHA256 requires at least 256 bits (32 bytes). Spring's
        // application.properties default and the documented .env.example
        // both exceed this; if someone configures a shorter secret the
        // exception thrown here is the right failure mode (fail fast on boot).
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(secretKey)
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
