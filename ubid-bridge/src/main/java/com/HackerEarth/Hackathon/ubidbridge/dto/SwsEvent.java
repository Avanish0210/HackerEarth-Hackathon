package com.HackerEarth.Hackathon.ubidbridge.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Inbound event received from SWS via webhook or REST call.
 * The Bridge translates this into one or more PropagationTasks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwsEvent {

    @NotBlank
    private String eventId;

    @NotBlank
    private String ubid;

    @NotBlank
    private String eventType;           // e.g. ADDRESS_CHANGE, NAME_CHANGE

    @NotNull
    private Map<String, Object> fields; // canonical SWS field map

    private OffsetDateTime occurredAt;
    private String initiatedBy;         // officer ID who made the change in SWS
}
