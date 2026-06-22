package com.mathwise.content.controller;

import com.mathwise.common.entity.Student;
import com.mathwise.common.repository.StudentRepository;
import com.mathwise.common.security.JwtUtil;
import com.mathwise.content.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code /api/courses/**}.
 *
 * <p>Boots the whole content-service against a throwaway Postgres
 * (Testcontainers). {@code DataSeeder} runs on context start, so the
 * assertions below exercise the REAL seeded curriculum — the same 8 nodes
 * the production database gets on first boot.
 *
 * <p>JWT handling mirrors production exactly: a student row is inserted,
 * a token is minted with the SAME JwtUtil + secret the filter validates
 * with, and requests carry it as a Bearer header. There is no auth-service
 * involved — stateless validation is the whole point of the shared-secret
 * design.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DisplayName("CourseController — integration")
class CourseControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private JwtUtil jwtUtil;

    private String bearer;

    @BeforeEach
    void ensureStudentAndToken() {
        // The JwtAuthFilter loads the Student by the token's email subject —
        // the row must exist for authentication to succeed.
        String email = "course-it@example.com";
        if (studentRepository.findByEmail(email).isEmpty()) {
            Student s = new Student();
            s.setEmail(email);
            s.setPassword("irrelevant-bcrypt-not-checked-here");
            s.setDisplayName("Course IT");
            s.setActive(true);
            studentRepository.save(s);
        }
        bearer = "Bearer " + jwtUtil.generateToken(email);
    }

    @Test
    @DisplayName("GET /api/courses without a token is rejected")
    void list_requires_authentication() throws Exception {
        // Exact status (401 vs 403) is an entry-point detail; the contract
        // that matters is: no token → no content.
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /api/courses lists the 8 seeded lessons ordered by difficulty")
    void list_returns_seeded_courses() throws Exception {
        mockMvc.perform(get("/api/courses").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].difficulty_level").value(1))
                .andExpect(jsonPath("$[7].difficulty_level").value(5))
                .andExpect(jsonPath("$[0].node_code").value(notNullValue()))
                .andExpect(jsonPath("$[0].intro").value(notNullValue()))
                .andExpect(jsonPath("$[0].estimated_minutes").value(greaterThan(0)));
    }

    @Test
    @DisplayName("GET /api/courses/{code} returns the full lesson")
    void lesson_detail() throws Exception {
        mockMvc.perform(get("/api/courses/ARITH_ADDITION").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.node_code").value("ARITH_ADDITION"))
                .andExpect(jsonPath("$.node_title").value("Addition"))
                .andExpect(jsonPath("$.theory").value(notNullValue()))
                .andExpect(jsonPath("$.examples").isArray())
                .andExpect(jsonPath("$.tip").value(notNullValue()));
    }

    @Test
    @DisplayName("GET /api/courses/{unknown} returns the 404 envelope")
    void lesson_unknown_code_is_404() throws Exception {
        mockMvc.perform(get("/api/courses/NOT_A_NODE").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(notNullValue()));
    }
}
