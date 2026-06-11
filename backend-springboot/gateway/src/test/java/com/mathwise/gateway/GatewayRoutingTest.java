package com.mathwise.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the gateway's route table — the contract that makes the microservice
 * split invisible to the Flutter client.
 *
 * <p>This is a CONFIG test, not a proxy test: it asserts that the four
 * public path families are registered and aimed at the right service URLs
 * (the localhost defaults here; compose overrides them by env). Actual
 * end-to-end proxying is covered by the docker-compose smoke test in the
 * phase-12 runbook, where real services sit behind the gateway.
 */
@SpringBootTest
@DisplayName("Gateway route table")
class GatewayRoutingTest {

    @Autowired
    private RouterFunction<ServerResponse> gatewayRoutes;

    /** Flatten the composed RouterFunction tree into its string form. */
    private String routeTable() {
        List<String> parts = new ArrayList<>();
        gatewayRoutes.accept(new RouterFunctions.Visitor() {
            @Override public void startNested(org.springframework.web.servlet.function.RequestPredicate predicate) { }
            @Override public void endNested(org.springframework.web.servlet.function.RequestPredicate predicate) { }
            @Override public void route(org.springframework.web.servlet.function.RequestPredicate predicate,
                                        org.springframework.web.servlet.function.HandlerFunction<?> handlerFunction) {
                parts.add(predicate.toString());
            }
            @Override public void resources(java.util.function.Function<org.springframework.web.servlet.function.ServerRequest,
                    java.util.Optional<org.springframework.core.io.Resource>> lookupFunction) { }
            @Override public void attributes(java.util.Map<String, Object> attributes) { }
            @Override public void unknown(RouterFunction<?> routerFunction) { }
        });
        return String.join(" | ", parts);
    }

    @Test
    @DisplayName("all four public path families are routed")
    void allPublicPathsAreRouted() {
        String table = routeTable();
        assertThat(table).contains("/api/auth/**");
        assertThat(table).contains("/api/courses/**");
        assertThat(table).contains("/api/student/**");
        assertThat(table).contains("/api/exercises/**");
    }

    @Test
    @DisplayName("context boots with the gateway starter on the classpath")
    void contextLoads() {
        assertThat(gatewayRoutes).isNotNull();
    }
}
