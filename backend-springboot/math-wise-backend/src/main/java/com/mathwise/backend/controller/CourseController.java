package com.mathwise.backend.controller;

import com.mathwise.backend.dto.CourseSummaryDto;
import com.mathwise.backend.dto.LessonDto;
import com.mathwise.backend.entity.KnowledgeNode;
import com.mathwise.backend.entity.Lesson;
import com.mathwise.backend.repository.LessonRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final LessonRepository lessonRepository;

    public CourseController(LessonRepository lessonRepository) {
        this.lessonRepository = lessonRepository;
    }

    @GetMapping
    public ResponseEntity<List<CourseSummaryDto>> listCourses() {
        List<CourseSummaryDto> summaries = lessonRepository.findAll().stream()
                .sorted(Comparator.comparingInt(l -> l.getKnowledgeNode().getDifficultyLevel()))
                .map(l -> {
                    KnowledgeNode node = l.getKnowledgeNode();
                    return new CourseSummaryDto(
                            node.getNodeCode(),
                            node.getTitle(),
                            l.getIntro(),
                            node.getDifficultyLevel(),
                            l.getEstimatedMinutes()
                    );
                })
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{nodeCode}")
    public ResponseEntity<LessonDto> getLesson(@PathVariable String nodeCode) {
        Lesson lesson = lessonRepository.findByKnowledgeNodeNodeCode(nodeCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found for: " + nodeCode));
        KnowledgeNode node = lesson.getKnowledgeNode();
        LessonDto dto = new LessonDto(
                node.getNodeCode(),
                node.getTitle(),
                node.getDifficultyLevel(),
                lesson.getEstimatedMinutes(),
                lesson.getIntro(),
                lesson.getTheory(),
                lesson.getExamples(),
                lesson.getTip()
        );
        return ResponseEntity.ok(dto);
    }
}
