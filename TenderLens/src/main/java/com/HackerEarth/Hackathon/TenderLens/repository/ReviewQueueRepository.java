package com.HackerEarth.Hackathon.TenderLens.repository;

import com.HackerEarth.Hackathon.TenderLens.entity.ReviewQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface ReviewQueueRepository extends JpaRepository<ReviewQueue , Long> {
    // All pending items for officer review dashboard
    List<ReviewQueue> findByReviewedFalseOrderByCreatedAtDesc();

    // Pending items for a specific tender
    @Query("SELECT r FROM ReviewQueue r " +
            "JOIN r.evaluation e WHERE e.tender.id = :tenderId " +
            "AND r.reviewed = false ORDER BY r.createdAt DESC")
    List<ReviewQueue> findPendingByTenderId(Long tenderId);

    // Check if already in review queue
    boolean existsByEvaluationId(Long evaluationId);

    long countByReviewedFalse();
}
