package com.HackerEarth.Hackathon.TenderLens.repository;

import com.HackerEarth.Hackathon.TenderLens.entity.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    // All evaluations for a tender — for report generation
    List<Evaluation> findByTenderIdOrderByBidderIdAscCriterionIdAsc(Long tenderId);

    // All evaluations for a specific bidder
    List<Evaluation> findByBidderIdOrderByCriterionId(Long bidderId);

    // Specific cell in the evaluation matrix
    Optional<Evaluation> findByTenderIdAndBidderIdAndCriterionId(
            Long tenderId, Long bidderId, Long criterionId);

    // All evaluations needing review for a tender
    List<Evaluation> findByTenderIdAndVerdict(Long tenderId, String verdict);

    // Count verdicts per bidder
    @Query("SELECT e.verdict, COUNT(e) FROM Evaluation e " +
            "WHERE e.bidder.id = :bidderId GROUP BY e.verdict")
    List<Object[]> countVerdictsByBidder(Long bidderId);

    // Check if evaluation already exists (idempotency)
    boolean existsByTenderIdAndBidderIdAndCriterionId(
            Long tenderId, Long bidderId, Long criterionId);

    List<Evaluation> findByTenderIdAndBidderIdOrderByCriterionId(Long id, Long id1);
}
