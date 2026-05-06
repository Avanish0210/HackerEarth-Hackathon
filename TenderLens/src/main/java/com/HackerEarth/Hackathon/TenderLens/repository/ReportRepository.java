package com.HackerEarth.Hackathon.TenderLens.repository;

import com.HackerEarth.Hackathon.TenderLens.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report,Long> {

    List<Report> findByTenderIdOrderByGeneratedAtDesc(Long tenderId);

    Optional<Report> findTopByTenderIdOrderByGeneratedAtDesc(Long tenderId);
}
