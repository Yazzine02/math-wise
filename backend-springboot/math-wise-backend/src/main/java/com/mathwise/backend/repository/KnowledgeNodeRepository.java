package com.mathwise.backend.repository;

import com.mathwise.backend.entity.KnowledgeNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KnowledgeNodeRepository extends JpaRepository<KnowledgeNode, UUID> {
    Optional<KnowledgeNode> findByNodeCode(String nodeCode);

    /**
     * Used by {@code KnowledgeNodeResolver} to recover when the LLM returns a
     * human-readable title (e.g. "Multiplication") instead of the canonical
     * node_code (e.g. "ARITH_MULTIPLICATION").
     */
    Optional<KnowledgeNode> findByTitleIgnoreCase(String title);
}
