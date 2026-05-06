package com.HackerEarth.Hackathon.TenderLens.repository;

import com.HackerEarth.Hackathon.TenderLens.entity.Criterion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CriterionRepository extends JpaRepository<Criterion, Long> {

    List<Criterion> findByTenderIdOrderByCriterionRef(Long tenderId);

    List<Criterion> findByTenderIdAndMandatoryTrue(Long tenderId);

    List<Criterion> findByTenderIdAndConfirmedByOfficerFalse(Long tenderId);

    long countByTenderIdAndConfirmedByOfficerTrue(Long tenderId);

    long countByTenderId(Long tenderId);

    List<Criterion> findByTenderIdAndConfirmedByOfficerTrue(Long tenderId);
}
