package com.HackerEarth.Hackathon.TenderLens.repository;

import com.HackerEarth.Hackathon.TenderLens.entity.Tender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenderRepository extends JpaRepository<Tender, Long> {

    Optional<Tender> findByTenderRef(String tenderRef);
    List<Tender> findByStatusOrderByCreatedAtDesc(String status);
    List<Tender> findAllByOrderByCreatedAtDesc();
    boolean existsByTenderRef(String tenderRef);

}
