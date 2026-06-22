package com.mathwise.practice.controller;

import com.mathwise.common.dto.AiFeedbackDto;
import com.mathwise.common.dto.AnswerCheckResponseDto;
import com.mathwise.common.entity.Exercise;
import com.mathwise.common.entity.InteractionLog;
import com.mathwise.common.entity.KnowledgeNode;
import com.mathwise.common.entity.Student;
import com.mathwise.common.repository.ExerciseRepository;
import com.mathwise.common.repository.InteractionLogRepository;
import com.mathwise.common.repository.KnowledgeNodeRepository;
import com.mathwise.common.repository.StudentRepository;
import com.mathwise.common.security.JwtUtil;
import com.mathwise.practice.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the practice loop: two-stage evaluation, weakness
 * normalisation, adaptive next-exercise, and the progress summary.
 *
 * <p>The database is REAL (Testcontainers Postgres) — the adaptive engine's
 * JPQL aggregation and the audit-log writes run against actual SQL. The
 * FastAPI boundary is the only thing mocked: {@code @MockitoBean RestTemplate}
 * replaces the HTTP client, letting each test script the AI's behaviour
 * (correct verdict, wrong verdict, hallucinated weakness label) without an
 * LLM in the loop.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DisplayName("Practice flow — integration")
class EvaluationFlowIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private KnowledgeNodeRepository knowledgeNodeRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private InteractionLogRepository interactionLogRepository;
    @Autowired private JwtUtil jwtUtil;

    /** Replaces the bean from RestTemplateConfig — the FastAPI boundary. */
    @MockitoBean private RestTemplate restTemplate;

    private Student student;
    private String bearer;

    @BeforeEach
    void seedMinimalCurriculum() {
        // Logs reference student + node; wipe them first so each test stands alone.
        interactionLogRepository.deleteAll();

        // One foundational node with one exercise. (content-service is not
        // part of this context — practice only ever READS curriculum data,
        // so the test seeds the minimum it needs.)
        KnowledgeNode division = knowledgeNodeRepository.findByNodeCode("ARITH_DIVISION")
                .orElseGet(() -> {
                    KnowledgeNode n = new KnowledgeNode();
                    n.setNodeCode("ARITH_DIVISION");
                    n.setTitle("Division");
                    n.setDifficultyLevel(2);
                    n.setActive(true);
                    return knowledgeNodeRepository.save(n);
                });
        if (exerciseRepository.findByKnowledgeNode(division).isEmpty()) {
            Exercise e = new Exercise();
            e.setKnowledgeNode(division);
            e.setQuestionText("What is 84 ÷ 7?");
            e.setCorrectAnswer("12");
            e.setDifficultyLevel(1);
            e.setActive(true);
            exerciseRepository.save(e);
        }

        String email = "practice-it@example.com";
        student = studentRepository.findByEmail(email).orElseGet(() -> {
            Student s = new Student();
            s.setEmail(email);
            s.setPassword("irrelevant-bcrypt-not-checked-here");
            s.setDisplayName("Practice IT");
            s.setActive(true);
            return studentRepository.save(s);
        });
        bearer = "Bearer " + jwtUtil.generateToken(email);
    }

    private void stubCheckAnswer(boolean correct) {
        AnswerCheckResponseDto check = new AnswerCheckResponseDto();
        check.setCorrect(correct);
        check.setUsedSymbolicCheck(true);
        when(restTemplate.postForObject(contains("/check-answer"), any(), eq(AnswerCheckResponseDto.class)))
                .thenReturn(check);
    }

    private static final String EVALUATE_BODY = """
            {"node_code":"ARITH_DIVISION",
             "equation":"What is 84 ÷ 7?",
             "correct_answer":"12",
             "student_answer":"%s"}""";

    @Test
    @DisplayName("correct answer: LLM is never called, log row is correct=true")
    void correct_answer_skips_llm() throws Exception {
        stubCheckAnswer(true);

        mockMvc.perform(post("/api/exercises/evaluate")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE_BODY.formatted("12")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_correct").value(true));

        // The two-stage design's whole point: a correct answer never reaches
        // the LLM diagnosis endpoint.
        verify(restTemplate, never())
                .postForObject(contains("/evaluate-error"), any(), eq(AiFeedbackDto.class));

        List<InteractionLog> logs = interactionLogRepository.findByStudentId(student.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().isCorrect()).isTrue();
        assertThat(logs.getFirst().getAiIdentifiedWeaknessCode()).isNull();
    }

    @Test
    @DisplayName("wrong answer: LLM weakness label is normalised to the canonical code and persisted")
    void wrong_answer_diagnoses_and_normalises() throws Exception {
        stubCheckAnswer(false);
        // The LLM often returns the human-readable TITLE ("Division") instead
        // of the canonical code — KnowledgeNodeResolver must repair it.
        AiFeedbackDto diagnosis = new AiFeedbackDto();
        diagnosis.setWeaknessNode("Division");
        diagnosis.setExplanation("84 ÷ 7 = 12, not 13 — check the last multiple.");
        when(restTemplate.postForObject(contains("/evaluate-error"), any(), eq(AiFeedbackDto.class)))
                .thenReturn(diagnosis);

        mockMvc.perform(post("/api/exercises/evaluate")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE_BODY.formatted("13")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_correct").value(false))
                .andExpect(jsonPath("$.weakness_node").value("ARITH_DIVISION"))
                .andExpect(jsonPath("$.explanation").value(notNullValue()));

        List<InteractionLog> logs = interactionLogRepository.findByStudentId(student.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().isCorrect()).isFalse();
        assertThat(logs.getFirst().getAiIdentifiedWeaknessCode()).isEqualTo("ARITH_DIVISION");
        assertThat(logs.getFirst().getAiExplanation()).isNotBlank();
    }

    @Test
    @DisplayName("GET /next-exercise serves the seeded node (cold start)")
    void next_exercise_cold_start() throws Exception {
        mockMvc.perform(get("/api/student/next-exercise").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.node_code").value("ARITH_DIVISION"))
                .andExpect(jsonPath("$.question_text").value(notNullValue()))
                .andExpect(jsonPath("$.correct_answer").value(notNullValue()));
    }

    @Test
    @DisplayName("GET /progress aggregates the recorded failure")
    void progress_reflects_failures() throws Exception {
        // Record one failure first (self-contained — no cross-test ordering).
        stubCheckAnswer(false);
        AiFeedbackDto diagnosis = new AiFeedbackDto();
        diagnosis.setWeaknessNode("ARITH_DIVISION");
        diagnosis.setExplanation("Off by one.");
        when(restTemplate.postForObject(contains("/evaluate-error"), any(), eq(AiFeedbackDto.class)))
                .thenReturn(diagnosis);
        mockMvc.perform(post("/api/exercises/evaluate")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATE_BODY.formatted("13")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/student/progress").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weaknesses[0].node_code").value("ARITH_DIVISION"))
                .andExpect(jsonPath("$.weaknesses[0].failure_count").value(1));
    }

    @Test
    @DisplayName("requests without a token are rejected")
    void requires_authentication() throws Exception {
        mockMvc.perform(get("/api/student/progress"))
                .andExpect(status().is4xxClientError());
    }
}
