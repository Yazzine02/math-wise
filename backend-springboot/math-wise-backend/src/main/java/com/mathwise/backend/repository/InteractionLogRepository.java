package com.mathwise.backend.repository;

import com.mathwise.backend.entity.InteractionLog;
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
}
