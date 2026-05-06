package com.HackerEarth.Hackathon.TenderLens.repository;

import com.HackerEarth.Hackathon.TenderLens.entity.Bidder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BidderRepository extends JpaRepository<Bidder, Long> {
    List<Bidder> findByTenderIdOrderByCompanyName(Long tenderId);

    Optional<Bidder> findByTenderIdAndBidderRef(Long tenderId, String bidderRef);

    List<Bidder> findByTenderIdAndParseStatus(Long tenderId, String parseStatus);

    long countByTenderId(Long tenderId);

    List<Bidder> findByTenderId(Long tenderId);

}
