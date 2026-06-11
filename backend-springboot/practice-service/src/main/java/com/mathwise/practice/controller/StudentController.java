package com.mathwise.practice.controller;

import com.mathwise.common.dto.ExerciseDto;
import com.mathwise.common.dto.WeaknessSummaryDto;
import com.mathwise.common.entity.Student;
import com.mathwise.practice.service.StudentProgressService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentProgressService studentProgressService;

    public StudentController(StudentProgressService studentProgressService) {
        this.studentProgressService = studentProgressService;
    }

    @GetMapping("/progress")
    public ResponseEntity<WeaknessSummaryDto> getProgress(@AuthenticationPrincipal Student student) {
        WeaknessSummaryDto summary = studentProgressService.getWeaknessSummary(student.getId());
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/next-exercise")
    public ResponseEntity<ExerciseDto> getNextExercise(
            @AuthenticationPrincipal Student student,
            @RequestParam(name = "node_code", required = false) String nodeCode) {
        ExerciseDto exercise = studentProgressService.getNextExercise(student.getId(), nodeCode);
        return ResponseEntity.ok(exercise);
    }
}
