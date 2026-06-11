package com.mathwise.backend.service;

import com.mathwise.common.entity.KnowledgeNode;
import com.mathwise.common.repository.KnowledgeNodeRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves a free-form string to a canonical {@link KnowledgeNode}.
 *
 * <p>The LLM (Llama via FastAPI) often returns the human-readable title
 * (e.g. "Multiplication") in {@code weakness_node} instead of the canonical
 * code ("ARITH_MULTIPLICATION"). The same kind of slip can happen on the
 * client side. Rather than rejecting these, we recover by trying the code
 * first and falling back to a case-insensitive title match.
 *
 * <p>Returning {@link Optional#empty()} means the string genuinely could not
 * be matched to any known node — callers should treat that as "skip" rather
 * than throw, when possible.
 */
@Component
public class KnowledgeNodeResolver {

    private final KnowledgeNodeRepository repo;

    public KnowledgeNodeResolver(KnowledgeNodeRepository repo) {
        this.repo = repo;
    }

    public Optional<KnowledgeNode> resolve(String text) {
        if (text == null) return Optional.empty();
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return Optional.empty();

        Optional<KnowledgeNode> byCode = repo.findByNodeCode(trimmed);
        if (byCode.isPresent()) return byCode;

        return repo.findByTitleIgnoreCase(trimmed);
    }
}
