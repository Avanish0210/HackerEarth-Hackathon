package com.HackerEarth.Hackathon.TenderLens.repository;

import com.HackerEarth.Hackathon.TenderLens.entity.TenderAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenderAuditLogRepository extends JpaRepository<TenderAuditLog,Long> {

    List<TenderAuditLog> findByTenderIdOrderByCreatedAtDesc(Long tenderId);

    List<TenderAuditLog> findByTenderIdAndBidderIdOrderByCreatedAtDesc(
            Long tenderId, Long bidderId);

    List<TenderAuditLog> findByActionOrderByCreatedAtDesc(String action);
}
