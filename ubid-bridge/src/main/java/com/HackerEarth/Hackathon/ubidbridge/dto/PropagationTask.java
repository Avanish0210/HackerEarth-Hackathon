package com.HackerEarth.Hackathon.ubidbridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropagationTask {
    private String eventId;            // globally unique — idempotency key
    private String ubid;               // the citizen/entity identifier
    private String sourceSystem;       // SWS | DEPT_A | DEPT_B ...
    private String targetSystem;       // which department to write to
    private String eventType;          // ADDRESS_CHANGE | NAME_CHANGE ...
    private Map<String, Object> payload; // translated field map for target schema
    private int retryCount;
    private OffsetDateTime createdAt;
    private String conflictPolicy;     // override policy for this task if needed
}
