package com.mathwise.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers setup for this service's integration tests.
 * {@code @ServiceConnection} auto-binds the container's URL/credentials to
 * the datasource — no test properties to maintain. One Postgres container
 * serves the whole module's IT suite via Spring context caching.
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
