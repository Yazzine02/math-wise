package com.mathwise.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Identity service (:9091). Registers/authenticates students and issues
 * JWTs; consumers of those tokens (content/practice services) validate them
 * independently with the shared secret — no runtime call back to this
 * service is ever needed (stateless auth).
 *
 * <p>Scan roots are explicit because the shared kernel (entities,
 * repositories, error envelope, JWT filter) lives in the {@code common}
 * module outside this package tree.
 */
@SpringBootApplication(scanBasePackages = {"com.mathwise.auth", "com.mathwise.common"})
@EntityScan("com.mathwise.common.entity")
@EnableJpaRepositories("com.mathwise.common.repository")
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
