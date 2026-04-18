package com.mathwise.backend.controller;

import com.mathwise.backend.dto.AiFeedbackDto;
import com.mathwise.backend.dto.MathEvaluationRequestDto;
import com.mathwise.backend.service.AiEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/exercises")
@CrossOrigin(origins = "*")// Allow Flutter to call locally without CORS Errors
public class AiEvaluationController {
    private final AiEvaluationService aiEvaluationService;

    public AiEvaluationController(AiEvaluationService aiEvaluationService){
        this.aiEvaluationService = aiEvaluationService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<AiFeedbackDto> evaluateError(@RequestBody MathEvaluationRequestDto requestDto){
        AiFeedbackDto feedback = aiEvaluationService.evaluateStudentError(requestDto);
        return ResponseEntity.ok(feedback);
    }
}
