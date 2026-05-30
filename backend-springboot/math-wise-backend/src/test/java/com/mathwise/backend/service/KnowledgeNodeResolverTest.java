package com.mathwise.backend.service;

import com.mathwise.backend.entity.KnowledgeNode;
import com.mathwise.backend.repository.KnowledgeNodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link KnowledgeNodeResolver}.
 *
 * <p><b>What this test demonstrates (the teaching part):</b>
 *
 * <ul>
 *   <li>{@code @ExtendWith(MockitoExtension.class)} — enables Mockito's
 *       automatic mock injection. Without this, {@code @Mock} fields would
 *       stay null.</li>
 *
 *   <li>{@code @Mock} — creates a mock instance of the repository. The
 *       mock returns sensible defaults (empty Optional, null, 0) for every
 *       method unless we tell it otherwise.</li>
 *
 *   <li>{@code @InjectMocks} — instantiates the System Under Test (SUT)
 *       and wires the mocks in via the constructor. We're testing the
 *       real resolver, but its only collaborator is a mock.</li>
 *
 *   <li>The Arrange / Act / Assert pattern — every test method below
 *       follows it. Stick to it religiously: it makes tests trivial
 *       to read three months from now.</li>
 *
 *   <li>{@code assertThat(...).isEqualTo(...)} — AssertJ's fluent
 *       assertion API. Reads as English and gives better failure
 *       messages than JUnit's bare {@code assertEquals}.</li>
 * </ul>
 *
 * <p><b>What this test does NOT do:</b> hit a real database, start the
 * Spring context, perform I/O. That's deliberate — those concerns belong
 * to integration tests. Mixing them in here would slow this suite from
 * milliseconds to seconds.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeNodeResolver")
class KnowledgeNodeResolverTest {

    @Mock
    private KnowledgeNodeRepository repository;

    @InjectMocks
    private KnowledgeNodeResolver resolver;

    /**
     * Helper: build a KnowledgeNode without touching the database. The
     * setters come from BaseEntity + KnowledgeNode itself. Useful because
     * we'll need a stand-in node in several tests.
     */
    private KnowledgeNode node(String code, String title) {
        KnowledgeNode n = new KnowledgeNode();
        n.setNodeCode(code);
        n.setTitle(title);
        return n;
    }

    // ─────────────────────────────────────────────────────────────────────
    // The happy path: an exact code match returns immediately.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("returns the node when the input matches an existing node_code")
    void resolves_by_exact_code() {
        // Arrange: tell the mock repo what to return when asked for ARITH_ADDITION.
        KnowledgeNode expected = node("ARITH_ADDITION", "Addition");
        when(repository.findByNodeCode("ARITH_ADDITION")).thenReturn(Optional.of(expected));

        // Act: call the method under test.
        Optional<KnowledgeNode> result = resolver.resolve("ARITH_ADDITION");

        // Assert: we got the expected node back.
        assertThat(result).contains(expected);

        // Bonus: the resolver should not fall through to the title-match
        // lookup when the code already matched. We verify the second
        // method was *never* called.
        verify(repository, never()).findByTitleIgnoreCase(any());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fallback path: the input wasn't a known code, so try the title.
    // This is what saves us when the LLM returns "Multiplication" instead
    // of "ARITH_MULTIPLICATION" — see the Phase 7 weakness fix.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("falls back to case-insensitive title match when no code matches")
    void falls_back_to_title_match() {
        when(repository.findByNodeCode("Multiplication")).thenReturn(Optional.empty());
        KnowledgeNode mult = node("ARITH_MULTIPLICATION", "Multiplication");
        when(repository.findByTitleIgnoreCase("Multiplication")).thenReturn(Optional.of(mult));

        Optional<KnowledgeNode> result = resolver.resolve("Multiplication");

        assertThat(result).contains(mult);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Defensive paths. A well-written method should handle these without
    // throwing, and the tests assert that it does.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("returns empty for null input")
    void returns_empty_for_null() {
        Optional<KnowledgeNode> result = resolver.resolve(null);

        assertThat(result).isEmpty();
        // The repo should never have been hit — null short-circuits early.
        verify(repository, never()).findByNodeCode(any());
    }

    @Test
    @DisplayName("returns empty for blank input")
    void returns_empty_for_blank() {
        assertThat(resolver.resolve("")).isEmpty();
        assertThat(resolver.resolve("   ")).isEmpty();
    }

    @Test
    @DisplayName("trims whitespace before lookup")
    void trims_input_before_lookup() {
        KnowledgeNode mult = node("ARITH_MULTIPLICATION", "Multiplication");
        when(repository.findByNodeCode("ARITH_MULTIPLICATION")).thenReturn(Optional.of(mult));

        Optional<KnowledgeNode> result = resolver.resolve("  ARITH_MULTIPLICATION  ");

        assertThat(result).contains(mult);
    }

    @Test
    @DisplayName("returns empty when neither lookup matches")
    void returns_empty_when_nothing_matches() {
        when(repository.findByNodeCode("BOGUS")).thenReturn(Optional.empty());
        when(repository.findByTitleIgnoreCase("BOGUS")).thenReturn(Optional.empty());

        assertThat(resolver.resolve("BOGUS")).isEmpty();
    }
}
