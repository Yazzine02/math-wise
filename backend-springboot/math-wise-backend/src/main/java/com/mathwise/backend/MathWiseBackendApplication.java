package com.mathwise.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Spring Boot 4 relocation: @EntityScan moved from
// org.springframework.boot.autoconfigure.domain into the spring-boot-persistence module.
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @EnableAsync} activates Spring's async proxy so that any bean
 * method annotated with {@code @Async} (notably
 * {@code ExercisePoolListener.onPoolLow}) runs on a managed thread pool
 * instead of blocking the caller. Without this, {@code @Async} is silently
 * a no-op.
 *
 * <p>Phase 12: entities, repositories and shared components now live in the
 * {@code common} module under {@code com.mathwise.common} — outside this
 * class's package tree — so all three scan roots are declared explicitly.
 */
@SpringBootApplication(scanBasePackages = {"com.mathwise.backend", "com.mathwise.common"})
@EntityScan("com.mathwise.common.entity")
@EnableJpaRepositories("com.mathwise.common.repository")
@EnableAsync
public class MathWiseBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MathWiseBackendApplication.class, args);
    }

}
