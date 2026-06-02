package com.mathwise.backend.repository;

import com.mathwise.backend.entity.InteractionLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface InteractionLogRepository extends JpaRepository<InteractionLog, UUID> {

    List<InteractionLog> findByStudentId(UUID studentId);

    List<InteractionLog> findByStudentIdAndIsCorrectFalse(UUID studentId);

    /**
     * Aggregate the student's recent wrong-answer rows by AI-identified
     * weakness code. The {@code since} parameter applies a rolling window so
     * the adaptive engine doesn't pull in mistakes from months ago that the
     * student has since corrected.
     *
     * <p>Returns rows of {@code [String nodeCode, Long failureCount]} ordered
     * by failureCount descending. Rows whose {@code aiIdentifiedWeaknessCode}
     * is null (correct answers, or wrong answers the LLM couldn't classify)
     * are excluded.
     */
    @Query("SELECT i.aiIdentifiedWeaknessCode, COUNT(i) as cnt " +
           "FROM InteractionLog i " +
           "WHERE i.student.id = :studentId " +
           "  AND i.isCorrect = false " +
           "  AND i.aiIdentifiedWeaknessCode IS NOT NULL " +
           "  AND i.createdAt > :since " +
           "GROUP BY i.aiIdentifiedWeaknessCode " +
           "ORDER BY cnt DESC")
    List<Object[]> findRecentTopWeaknessesByStudentId(
            @Param("studentId") UUID studentId,
            @Param("since") LocalDateTime since);

    /**
     * Last-N attempts on a specific knowledge node, newest first. Used by
     * {@code StudentProgressService.isMastered} to check whether the
     * student's recent record on the node is clean (all correct).
     *
     * <p>Caller passes {@code PageRequest.of(0, N)} as the {@code pageable}
     * argument to limit results to the freshest N rows.
     */
    List<InteractionLog> findByStudentIdAndTestedNodeNodeCodeOrderByCreatedAtDesc(
            UUID studentId, String nodeCode, Pageable pageable);

    /**
     * Distinct knowledge-node codes the student has ever attempted (any
     * outcome). Used by the cold-start logic to decide whether the student
     * is a true beginner (empty list → start at curriculum root) and by the
     * 70/30 exploration logic to choose a non-weakness review topic.
     */
    @Query("SELECT DISTINCT i.testedNode.nodeCode FROM InteractionLog i " +
           "WHERE i.student.id = :studentId")
    List<String> findDistinctAttemptedNodeCodesByStudentId(@Param("studentId") UUID studentId);
}
