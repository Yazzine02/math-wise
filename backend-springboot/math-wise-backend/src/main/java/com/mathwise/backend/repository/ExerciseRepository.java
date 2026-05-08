package com.mathwise.backend.repository;

import com.mathwise.backend.entity.Exercise;
import com.mathwise.backend.entity.KnowledgeNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {
    List<Exercise> findByKnowledgeNode(KnowledgeNode knowledgeNode);
    List<Exercise> findByKnowledgeNodeNodeCode(String nodeCode);
}
