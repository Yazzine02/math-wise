package com.mathwise.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The public front door (:9090). See {@link com.mathwise.gateway.config.RouteConfig}
 * for the path → service map. Deliberately stateless and security-free:
 * authentication is each downstream service's job.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
