package com.mathwise.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @EnableAsync} activates Spring's async proxy so that any bean
 * method annotated with {@code @Async} (notably
 * {@code ExercisePoolListener.onPoolLow}) runs on a managed thread pool
 * instead of blocking the caller. Without this, {@code @Async} is silently
 * a no-op.
 */
@SpringBootApplication
@EnableAsync
public class MathWiseBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MathWiseBackendApplication.class, args);
    }

}
