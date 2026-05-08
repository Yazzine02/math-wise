package com.mathwise.backend.controller;

import com.mathwise.backend.dto.AiFeedbackDto;
import com.mathwise.backend.dto.EvaluateAnswerRequestDto;
import com.mathwise.backend.service.AiEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exercises")
public class AiEvaluationController {

    private final AiEvaluationService aiEvaluationService;

    public AiEvaluationController(AiEvaluationService aiEvaluationService) {
        this.aiEvaluationService = aiEvaluationService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<AiFeedbackDto> evaluateError(@RequestBody EvaluateAnswerRequestDto requestDto) {
        AiFeedbackDto feedback = aiEvaluationService.evaluateStudentAnswer(requestDto);
        return ResponseEntity.ok(feedback);
    }
}
