package com.mathwise.backend.repository;

import com.mathwise.backend.entity.Exercise;
import com.mathwise.backend.entity.KnowledgeNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {
    List<Exercise> findByKnowledgeNode(KnowledgeNode knowledgeNode);
    List<Exercise> findByKnowledgeNodeNodeCode(String nodeCode);

    /**
     * Cheap pool-size check for the Phase 8 generation pipeline. Avoids
     * loading every Exercise just to count them.
     */
    long countByKnowledgeNode(KnowledgeNode knowledgeNode);
}
