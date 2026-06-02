package com.mathwise.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathwise.backend.TestcontainersConfiguration;
import com.mathwise.backend.dto.LoginRequestDto;
import com.mathwise.backend.dto.RegisterRequestDto;
import com.mathwise.backend.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4 moved AutoConfigureMockMvc out of
// `org.springframework.boot.test.autoconfigure.web.servlet` into
// `org.springframework.boot.webmvc.test.autoconfigure`. Same pattern
// as the RestTemplateBuilder relocation in Phase 2.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code /api/auth/*}.
 *
 * <p><b>How this differs from the unit tests:</b>
 *
 * <ul>
 *   <li>{@code @SpringBootTest} actually boots the whole application —
 *       every bean, every controller, every service. Slower (typically
 *       3-8s to start up), but it verifies the real wiring.</li>
 *   <li>{@code @Testcontainers} + {@code @Container} spin up a real
 *       Postgres in a throwaway docker container. We test against the
 *       same database engine production runs on, not an in-memory H2.</li>
 *   <li>{@code @ServiceConnection} on the container auto-binds it to
 *       Spring's datasource config — no manual properties to write.</li>
 *   <li>{@code MockMvc} lets us send HTTP requests in-process. No port
 *       binding, no network. JSON serialisation, security filters,
 *       validation, controller advice — they all run. Only the TCP
 *       socket is faked.</li>
 *   <li>{@code @BeforeEach} clears the {@code students} table so each
 *       test starts from a known state. Without this, tests would
 *       interfere with each other and become order-dependent.</li>
 * </ul>
 *
 * <p><b>The {@code IT} suffix is a Maven Failsafe convention</b> —
 * {@code *Test.java} runs under Surefire (fast unit tests during compile),
 * {@code *IT.java} runs under Failsafe (slower integration tests during
 * verify). Our pom doesn't yet enforce this split, but the naming sets
 * us up for it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DisplayName("AuthController — integration")
class AuthControllerIT {

    /**
     * Postgres is provided by {@link TestcontainersConfiguration} as a
     * Spring-managed {@code @ServiceConnection} bean — shared across every
     * test class in this suite, so the container only starts once.
     *
     * <p>The JWT signing secret uses the placeholder default from
     * {@code application.properties} (47 chars, well over the 32-byte
     * minimum for HMAC-SHA256). No explicit override needed.
     */
    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;

    // Spring Boot 4 standardises on Jackson 3 and no longer registers a
    // com.fasterxml.jackson.databind.ObjectMapper (Jackson 2) bean by default,
    // so @Autowired-ing this type fails with "No qualifying bean". This test
    // only needs to serialise request DTOs to JSON, so we construct a plain
    // Jackson 2 mapper directly (jackson-databind is on the test classpath via
    // jjwt-jackson). It still honours the DTOs' @JsonProperty annotations
    // (e.g. display_name), which is all the request bodies rely on.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetDb() {
        // Clean slate. Each test asserts behaviour on an empty `students` table,
        // so we delete everything before the next test runs.
        studentRepository.deleteAll();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Happy paths
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /register with valid input returns 200 and a JWT")
    void register_happy_path() throws Exception {
        RegisterRequestDto req = new RegisterRequestDto();
        req.setEmail("ali@example.com");
        req.setPassword("hunter22");
        req.setDisplayName("Ali");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(notNullValue()))
                .andExpect(jsonPath("$.email").value("ali@example.com"))
                .andExpect(jsonPath("$.display_name").value("Ali"));
    }

    @Test
    @DisplayName("POST /login with matching credentials returns 200 and a JWT")
    void login_happy_path() throws Exception {
        // First register so there's an account to log in to. Re-using the
        // controller here means we're also indirectly testing the
        // register path — that's deliberate.
        RegisterRequestDto reg = new RegisterRequestDto();
        reg.setEmail("ali@example.com");
        reg.setPassword("hunter22");
        reg.setDisplayName("Ali");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(reg))).andExpect(status().isOk());

        LoginRequestDto login = new LoginRequestDto();
        login.setEmail("ali@example.com");
        login.setPassword("hunter22");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(notNullValue()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sad paths — these verify the Phase 5 validation + error envelope work.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /register with malformed email returns 400 VALIDATION_FAILED + fieldErrors")
    void register_validation_failure() throws Exception {
        RegisterRequestDto req = new RegisterRequestDto();
        req.setEmail("not-an-email");
        req.setPassword("short");                  // too short
        req.setDisplayName("");                    // blank

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").value(notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.password").value(notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.displayName").value(notNullValue()));
    }

    @Test
    @DisplayName("POST /register with an already-used email returns 409 EMAIL_ALREADY_EXISTS")
    void register_email_already_exists() throws Exception {
        RegisterRequestDto first = new RegisterRequestDto();
        first.setEmail("ali@example.com");
        first.setPassword("hunter22");
        first.setDisplayName("Ali");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(first))).andExpect(status().isOk());

        // Same email, different display name → must conflict.
        RegisterRequestDto duplicate = new RegisterRequestDto();
        duplicate.setEmail("ali@example.com");
        duplicate.setPassword("different");
        duplicate.setDisplayName("Ali2");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /login with wrong password returns 401 INVALID_CREDENTIALS")
    void login_wrong_password() throws Exception {
        RegisterRequestDto reg = new RegisterRequestDto();
        reg.setEmail("ali@example.com");
        reg.setPassword("hunter22");
        reg.setDisplayName("Ali");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(reg))).andExpect(status().isOk());

        LoginRequestDto login = new LoginRequestDto();
        login.setEmail("ali@example.com");
        login.setPassword("wrong-password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /login with an unknown email returns 401 INVALID_CREDENTIALS (no enumeration)")
    void login_unknown_email_same_response_shape() throws Exception {
        // Same response as wrong-password by design — see the Phase 5 note
        // about preventing account enumeration. This test pins that contract.
        LoginRequestDto login = new LoginRequestDto();
        login.setEmail("nobody@example.com");
        login.setPassword("anything");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
