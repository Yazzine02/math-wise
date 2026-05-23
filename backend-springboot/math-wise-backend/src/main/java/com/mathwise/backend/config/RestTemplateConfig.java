package com.mathwise.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate bean used by AiEvaluationService to call the FastAPI service.
 *
 * <p>The default {@link RestTemplate} has no timeouts at all — a slow or hung
 * Ollama instance would tie up the calling Tomcat thread indefinitely. A few
 * stuck requests is enough to exhaust the request thread pool and bring the
 * whole backend down. We size timeouts for the slowest legitimate call
 * (LLM generation).
 *
 * <p>Connect timeout is small (3s): we're on a private docker network, if we
 * can't establish a TCP connection in 3 seconds the AI service is effectively
 * unreachable. Read timeout is 60s: Llama3.2:3b on a CPU can take 30+ seconds
 * on the first request when the model is loading, so we give it headroom.
 *
 * <p><b>Why not RestTemplateBuilder?</b> In Spring Boot 4 the builder was
 * relocated out of the {@code spring-boot} artifact into a separate
 * {@code spring-boot-restclient} module that isn't pulled in by
 * {@code spring-boot-starter-webmvc}. Rather than add a dependency just to
 * configure timeouts, we set them directly on a
 * {@link SimpleClientHttpRequestFactory} (part of {@code spring-web}, which
 * we already have).
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return new RestTemplate(factory);
    }
}
