package com.mathwise.backend.service;

import com.mathwise.backend.dto.AiFeedbackDto;
import com.mathwise.backend.dto.AnswerCheckRequestDto;
import com.mathwise.backend.dto.AnswerCheckResponseDto;
import com.mathwise.backend.dto.EvaluateAnswerRequestDto;
import com.mathwise.backend.dto.MathEvaluationRequestDto;
import com.mathwise.backend.entity.InteractionLog;
import com.mathwise.backend.entity.KnowledgeNode;
import com.mathwise.backend.entity.Student;
import com.mathwise.backend.repository.InteractionLogRepository;
import com.mathwise.backend.repository.KnowledgeNodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AiEvaluationService {

    private final RestTemplate restTemplate;
    private final InteractionLogRepository interactionLogRepository;
    private final KnowledgeNodeRepository knowledgeNodeRepository;
    private final KnowledgeNodeResolver nodeResolver;

    @Value("${ai-service.url}")
    private String aiServiceUrl;

    public AiEvaluationService(RestTemplate restTemplate,
                                InteractionLogRepository interactionLogRepository,
                                KnowledgeNodeRepository knowledgeNodeRepository,
                                KnowledgeNodeResolver nodeResolver) {
        this.restTemplate = restTemplate;
        this.interactionLogRepository = interactionLogRepository;
        this.knowledgeNodeRepository = knowledgeNodeRepository;
        this.nodeResolver = nodeResolver;
    }

    /**
     * Two-stage evaluation:
     * <ol>
     *   <li><b>Symbolic correctness check</b> — SymPy on the FastAPI side. Fast
     *       (~50ms) and accepts mathematically-equivalent alternative forms
     *       (1/2 ⇔ 0.5, (x+2)(x+3) ⇔ x²+5x+6, etc.). This is authoritative.</li>
     *   <li><b>LLM diagnosis</b> — only invoked when the student is wrong.
     *       Skipping the LLM on correct answers saves 5–30s per request and
     *       avoids the noise of a small model trying to comment on a perfect
     *       answer.</li>
     * </ol>
     */
    public AiFeedbackDto evaluateStudentAnswer(EvaluateAnswerRequestDto requestDto) {
        Student student = (Student) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        KnowledgeNode testedNode = knowledgeNodeRepository.findByNodeCode(requestDto.getNodeCode())
                .orElseThrow(() -> new IllegalArgumentException("Unknown node code: " + requestDto.getNodeCode()));

        // 1. Symbolic correctness check (always)
        AnswerCheckRequestDto checkRequest = new AnswerCheckRequestDto(
                requestDto.getCorrectAnswer(),
                requestDto.getStudentAnswer()
        );
        AnswerCheckResponseDto checkResponse = restTemplate.postForObject(
                aiServiceUrl + "/check-answer",
                checkRequest,
                AnswerCheckResponseDto.class
        );
        boolean isCorrect = checkResponse != null && checkResponse.isCorrect();

        // 2. LLM diagnosis ONLY when wrong
        AiFeedbackDto feedback;
        if (!isCorrect) {
            MathEvaluationRequestDto aiRequest = new MathEvaluationRequestDto();
            aiRequest.setEquation(requestDto.getEquation());
            aiRequest.setCorrectAnswer(requestDto.getCorrectAnswer());
            aiRequest.setStudentAnswer(requestDto.getStudentAnswer());

            feedback = restTemplate.postForObject(
                    aiServiceUrl + "/evaluate-error",
                    aiRequest,
                    AiFeedbackDto.class
            );
            if (feedback == null) {
                feedback = new AiFeedbackDto();
            }
        } else {
            // Correct — skip the LLM. Build a minimal positive-feedback object.
            feedback = new AiFeedbackDto();
        }

        // 3. Normalise the AI-supplied weakness (only meaningful when wrong)
        if (!isCorrect) {
            String raw = feedback.getWeakness_node();
            String canonical = nodeResolver.resolve(raw)
                    .map(KnowledgeNode::getNodeCode)
                    .orElse(testedNode.getNodeCode());
            feedback.setWeakness_node(canonical);
        }
        feedback.setIs_correct(isCorrect);

        // 4. Persist the audit row
        InteractionLog log = new InteractionLog();
        log.setStudent(student);
        log.setTestedNode(testedNode);
        log.setOriginalEquation(requestDto.getEquation());
        log.setStudentInput(requestDto.getStudentAnswer());
        log.setCorrect(isCorrect);
        log.setActive(true);
        if (!isCorrect) {
            log.setAiIdentifiedWeaknessCode(feedback.getWeakness_node());
            log.setAiExplanation(feedback.getExplanation());
        }
        interactionLogRepository.save(log);

        return feedback;
    }
}
