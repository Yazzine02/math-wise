package com.mathwise.backend.repository;

import com.mathwise.backend.entity.KnowledgeNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KnowledgeNodeRepository extends JpaRepository<KnowledgeNode, UUID> {
    Optional<KnowledgeNode> findByNodeCode(String nodeCode);
}
