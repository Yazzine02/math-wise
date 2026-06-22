package com.mathwise.practice.service;

import com.mathwise.common.dto.AiFeedbackDto;
import com.mathwise.common.dto.AnswerCheckRequestDto;
import com.mathwise.common.dto.AnswerCheckResponseDto;
import com.mathwise.common.dto.EvaluateAnswerRequestDto;
import com.mathwise.common.dto.MathEvaluationRequestDto;
import com.mathwise.common.entity.InteractionLog;
import com.mathwise.common.entity.KnowledgeNode;
import com.mathwise.common.entity.Student;
import com.mathwise.common.repository.InteractionLogRepository;
import com.mathwise.common.repository.KnowledgeNodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     *
     * <p>{@code @Transactional} ties the {@link InteractionLog} write to the
     * rest of the method. If the DB save throws (constraint violation,
     * connection pool exhaustion) Spring rolls back the transaction and the
     * exception propagates to the controller — the client receives a proper
     * error response instead of "Correct!" feedback for a row that was never
     * persisted. The external HTTP calls obviously cannot be rolled back, but
     * the audit trail and the client response are now consistent.
     */
    @Transactional
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
            // Phase 7: send the topic + its prereq chain so the LLM can reason
            // about WHICH prerequisite skill the student's slip actually
            // reflects (e.g. a division error during a linear-equation
            // problem). The LLM is still free to return any of the 8
            // canonical codes — these are hints, not constraints.
            aiRequest.setNodeCode(testedNode.getNodeCode());
            aiRequest.setPrerequisiteCodes(collectPrerequisiteChain(testedNode));

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
            String raw = feedback.getWeaknessNode();
            String canonical = nodeResolver.resolve(raw)
                    .map(KnowledgeNode::getNodeCode)
                    .orElse(testedNode.getNodeCode());
            feedback.setWeaknessNode(canonical);
        }
        feedback.setCorrect(isCorrect);

        // 4. Persist the audit row
        InteractionLog log = new InteractionLog();
        log.setStudent(student);
        log.setTestedNode(testedNode);
        log.setOriginalEquation(requestDto.getEquation());
        log.setStudentInput(requestDto.getStudentAnswer());
        log.setCorrect(isCorrect);
        log.setActive(true);
        if (!isCorrect) {
            log.setAiIdentifiedWeaknessCode(feedback.getWeaknessNode());
            log.setAiExplanation(feedback.getExplanation());
        }
        interactionLogRepository.save(log);

        return feedback;
    }

    /**
     * Walks the {@code prerequisiteNode} chain from the tested node outward,
     * collecting canonical codes in order (immediate prereq first). Bounded
     * length and visited-set as a cycle guard, identical pattern to the
     * descent in {@code StudentProgressService}.
     */
    private List<String> collectPrerequisiteChain(KnowledgeNode start) {
        List<String> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        KnowledgeNode cursor = start.getPrerequisiteNode();
        int hops = 0;
        while (cursor != null && hops++ < 10 && visited.add(cursor.getNodeCode())) {
            chain.add(cursor.getNodeCode());
            cursor = cursor.getPrerequisiteNode();
        }
        return chain;
    }
}
