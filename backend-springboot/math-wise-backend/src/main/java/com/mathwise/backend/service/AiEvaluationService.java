package com.mathwise.backend.service;

import com.mathwise.backend.dto.AiFeedbackDto;
import com.mathwise.backend.dto.EvaluateAnswerRequestDto;
import com.mathwise.backend.dto.MathEvaluationRequestDto;
import com.mathwise.backend.entity.InteractionLog;
import com.mathwise.backend.entity.KnowledgeNode;
import com.mathwise.backend.entity.Student;
import com.mathwise.backend.repository.InteractionLogRepository;
import com.mathwise.backend.repository.KnowledgeNodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AiEvaluationService {

    private final RestTemplate restTemplate;
    private final InteractionLogRepository interactionLogRepository;
    private final KnowledgeNodeRepository knowledgeNodeRepository;

    @Value("${ai-service.url}")
    private String aiServiceUrl;

    public AiEvaluationService(RestTemplate restTemplate,
                                InteractionLogRepository interactionLogRepository,
                                KnowledgeNodeRepository knowledgeNodeRepository) {
        this.restTemplate = restTemplate;
        this.interactionLogRepository = interactionLogRepository;
        this.knowledgeNodeRepository = knowledgeNodeRepository;
    }

    public AiFeedbackDto evaluateStudentAnswer(EvaluateAnswerRequestDto requestDto) {
        MathEvaluationRequestDto aiRequest = new MathEvaluationRequestDto();
        aiRequest.setEquation(requestDto.getEquation());
        aiRequest.setCorrectAnswer(requestDto.getCorrectAnswer());
        aiRequest.setStudentAnswer(requestDto.getStudentAnswer());

        String endpoint = aiServiceUrl + "/evaluate-error";
        ResponseEntity<AiFeedbackDto> response = restTemplate.postForEntity(endpoint, aiRequest, AiFeedbackDto.class);
        AiFeedbackDto feedback = response.getBody();

        boolean isCorrect = requestDto.getCorrectAnswer().trim()
                .equalsIgnoreCase(requestDto.getStudentAnswer().trim());

        Student student = (Student) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        KnowledgeNode testedNode = knowledgeNodeRepository.findByNodeCode(requestDto.getNodeCode())
                .orElseThrow(() -> new IllegalArgumentException("Unknown node code: " + requestDto.getNodeCode()));

        InteractionLog log = new InteractionLog();
        log.setStudent(student);
        log.setTestedNode(testedNode);
        log.setOriginalEquation(requestDto.getEquation());
        log.setStudentInput(requestDto.getStudentAnswer());
        log.setCorrect(isCorrect);
        log.setActive(true);
        if (feedback != null && !isCorrect) {
            log.setAiIdentifiedWeaknessCode(feedback.getWeakness_node());
            log.setAiExplanation(feedback.getExplanation());
        }
        interactionLogRepository.save(log);

        return feedback;
    }
}
