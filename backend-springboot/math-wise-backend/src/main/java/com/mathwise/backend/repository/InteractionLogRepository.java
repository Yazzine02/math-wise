package com.mathwise.backend.repository;

import com.mathwise.backend.entity.InteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InteractionLogRepository extends JpaRepository<InteractionLog, UUID> {

    List<InteractionLog> findByStudentId(UUID studentId);

    List<InteractionLog> findByStudentIdAndIsCorrectFalse(UUID studentId);

    @Query("SELECT i.aiIdentifiedWeaknessCode, COUNT(i) as cnt " +
           "FROM InteractionLog i " +
           "WHERE i.student.id = :studentId AND i.isCorrect = false AND i.aiIdentifiedWeaknessCode IS NOT NULL " +
           "GROUP BY i.aiIdentifiedWeaknessCode " +
           "ORDER BY cnt DESC")
    List<Object[]> findTopWeaknessesByStudentId(@Param("studentId") UUID studentId);
}
