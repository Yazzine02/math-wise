package com.mathwise.backend.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate bean used by AiEvaluationService to call the FastAPI service.
 *
 * <p>Default {@link RestTemplate} has no timeouts at all — a slow or hung
 * Ollama instance would tie up the calling Tomcat thread indefinitely. A few
 * stuck requests is enough to exhaust the request thread pool and bring the
 * whole backend down. We size timeouts for the slowest legitimate call (LLM
 * generation), which is significantly slower than the symbolic check.
 *
 * <p>Connect timeout is small (3s): we're on a private docker network, if we
 * can't establish a TCP connection in 3 seconds the AI service is effectively
 * unreachable. Read timeout is 60s: Llama3.2:3b on a CPU can take 30+ seconds
 * on the first request when the model is loading, so we give it headroom.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(60))
                .build();
    }
}
