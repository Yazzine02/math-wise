package com.mathwise.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers setup imported by every test that needs a real
 * Postgres (i.e. every {@code @SpringBootTest} in this project).
 *
 * <p><b>Why a shared config class instead of per-test {@code @Container}
 * fields?</b>
 *
 * <ul>
 *   <li><b>Spring context caching.</b> When multiple test classes import
 *       this same configuration and use the same {@code @SpringBootTest}
 *       shape, Spring reuses the application context across them. That
 *       reuse extends to the container bean too — one Postgres process
 *       serves the entire test suite, instead of starting a new one per
 *       test class. Saves several seconds per class.</li>
 *
 *   <li><b>The container is a real Spring bean.</b> {@code @ServiceConnection}
 *       (Spring Boot 3.1+) auto-wires its URL / user / password into the
 *       datasource config. No {@code application-test.properties} to
 *       maintain.</li>
 *
 *   <li><b>Discoverable and reusable.</b> A new test that needs a database
 *       just adds {@code @Import(TestcontainersConfiguration.class)} to
 *       its declaration. No magic, no copy-paste of container setup.</li>
 * </ul>
 *
 * <p><b>Why {@code proxyBeanMethods = false}?</b> The bean has no
 * inter-bean references, so the CGLIB proxy Spring would otherwise create
 * is unnecessary overhead. Idiomatic for {@code @TestConfiguration}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Testcontainers 2.x dropped the SELF generic on container classes.
        return new PostgreSQLContainer("postgres:16");
    }
}
