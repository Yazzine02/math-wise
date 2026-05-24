package com.mathwise.backend.repository;

import com.mathwise.backend.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    Optional<Lesson> findByKnowledgeNodeNodeCode(String nodeCode);
}
