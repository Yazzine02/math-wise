package com.mathwise.backend.service;

import com.mathwise.backend.dto.AiFeedbackDto;
import com.mathwise.backend.dto.MathEvaluationRequestDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AiEvaluationService {
    private final RestTemplate restTemplate;

    //Pull Fastapi endpoint url from application.properties
    @Value("${ai-service.url}")
    private String aiServiceUrl;

    //IOC was used here
    public AiEvaluationService(RestTemplate restTemplate){
        this.restTemplate=restTemplate;
    }

    public AiFeedbackDto evaluateStudentError(MathEvaluationRequestDto requestDto){
        String endpoint = aiServiceUrl + "/evaluate-error";
        ResponseEntity<AiFeedbackDto> response = restTemplate.postForEntity(
                endpoint,
                requestDto,
                AiFeedbackDto.class
        );
        //TODO: Add logic to save identified weakness to pgsql DB
        return response.getBody();
    }
}
