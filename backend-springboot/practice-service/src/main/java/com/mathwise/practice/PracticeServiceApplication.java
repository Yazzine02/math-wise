package com.mathwise.practice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Practice service (:9093): adaptive exercise selection, two-stage answer
 * evaluation (SymPy check → LLM diagnosis via FastAPI), progress summary,
 * and the asynchronous exercise-pool top-up.
 *
 * <p>{@code @EnableAsync} is load-bearing: without it the {@code @Async}
 * {@code ExercisePoolListener.onPoolLow} silently runs on the caller's
 * thread, blocking the student's request on an LLM round-trip.
 */
@SpringBootApplication(scanBasePackages = {"com.mathwise.practice", "com.mathwise.common"})
@EntityScan("com.mathwise.common.entity")
@EnableJpaRepositories("com.mathwise.common.repository")
@EnableAsync
public class PracticeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PracticeServiceApplication.class, args);
    }
}
