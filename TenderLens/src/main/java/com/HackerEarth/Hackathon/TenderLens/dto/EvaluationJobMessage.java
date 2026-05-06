package com.HackerEarth.Hackathon.TenderLens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Kafka message that triggers async evaluation of one bidder
 * against all confirmed criteria for a tender.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationJobMessage {

    private Long tenderId;
    private Long bidderId;
    private String tenderRef;
    private String bidderRef;
    private String companyName;
    private String filePath;
    private boolean ocrRequired;
    private OffsetDateTime queuedAt;
    private String requestedBy;
}
