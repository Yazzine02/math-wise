package com.mathwise.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * The path → service map. These four patterns ARE the monolith's public API,
 * forwarded verbatim — the Flutter client cannot tell the difference.
 *
 * <p>Targets come from env vars with localhost defaults, so the same image
 * works on a dev machine (services on host ports) and in docker-compose
 * (service-name DNS) without a code change.
 *
 * <pre>
 *   /api/auth/**                      → auth-service     (:9091)
 *   /api/courses/**                   → content-service  (:9092)
 *   /api/student/**, /api/exercises/** → practice-service (:9093)
 * </pre>
 */
@Configuration
public class RouteConfig {

    @Value("${AUTH_SERVICE_URL:http://localhost:9091}")
    private String authServiceUrl;

    @Value("${CONTENT_SERVICE_URL:http://localhost:9092}")
    private String contentServiceUrl;

    @Value("${PRACTICE_SERVICE_URL:http://localhost:9093}")
    private String practiceServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> gatewayRoutes() {
        // Spring Cloud Gateway (server-webmvc) 5.x style: http() proxies to
        // the URI set by the uri(...) before-filter.
        return route("auth-service")
                    .route(path("/api/auth/**"), http())
                    .before(uri(authServiceUrl))
                    .build()
            .and(route("content-service")
                    .route(path("/api/courses/**"), http())
                    .before(uri(contentServiceUrl))
                    .build())
            .and(route("practice-service")
                    .route(path("/api/student/**").or(path("/api/exercises/**")), http())
                    .before(uri(practiceServiceUrl))
                    .build());
    }
}
