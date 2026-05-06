package com.HackerEarth.Hackathon.TenderLens.service;

import com.HackerEarth.Hackathon.TenderLens.config.TenderLensProperties;
import com.HackerEarth.Hackathon.TenderLens.dto.CriterionDto;
import com.HackerEarth.Hackathon.TenderLens.dto.LlmExtractionResponse;
import com.HackerEarth.Hackathon.TenderLens.entity.Criterion;
import com.HackerEarth.Hackathon.TenderLens.entity.Tender;
import com.HackerEarth.Hackathon.TenderLens.repository.CriterionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls Ollama (llama3.2) to extract eligibility criteria from a tender document.
 *
 * The LLM is given a structured prompt asking it to return ONLY valid JSON.
 * The response is parsed into CriterionDto list, then persisted as Criterion entities.
 *
 * Retries up to tenderlens.llm.max-parse-retries times on JSON parse failure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CriterionExtractorService {

    private final ChatClient chatClient;
    private final CriterionRepository criterionRepository;
    private final TenderLensProperties props;
    private final ObjectMapper objectMapper;

    private static final String EXTRACTION_PROMPT = """
            You are an expert procurement officer AI. Analyze the following tender document and extract ALL eligibility criteria.
            
            Return ONLY a valid JSON object (no markdown, no explanation) with this exact structure:
            {
              "criteria": [
                {
                  "criterionRef": "C-001",
                  "description": "Clear description of what is required",
                  "criterionType": "FINANCIAL|TECHNICAL|COMPLIANCE|CERTIFICATION",
                  "mandatory": true|false,
                  "thresholdValue": "the specific value/requirement (e.g., '5 crore', 'ISO 9001', '3 years')",
                  "thresholdOperator": "GTE|LTE|EQ|CONTAINS|EXISTS",
                  "sourcePage": 1,
                  "confidence": 0.95
                }
              ]
            }
            
            Rules:
            - Extract EVERY criterion mentioned, including financial, technical, compliance, and certification requirements
            - criterionType must be one of: FINANCIAL, TECHNICAL, COMPLIANCE, CERTIFICATION
            - thresholdOperator: GTE (>=), LTE (<=), EQ (=), CONTAINS (text match), EXISTS (just needs to exist)
            - mandatory: true if disqualifying, false if preferred/optional
            - confidence: 0.0-1.0, your confidence this is a real criterion
            - sourcePage: best estimate of which page this came from
            - Number criteria sequentially: C-001, C-002, etc.
            
            TENDER DOCUMENT:
            {{DOCUMENT}}
            """;

    @Transactional
    public List<Criterion> extractAndSave(Tender tender, String documentText) {
        log.info("Extracting criteria from tender: {}", tender.getTenderRef());

        List<CriterionDto> dtos = extractWithRetry(documentText, props.getLlm().getMaxParseRetries());

        List<Criterion> saved = new ArrayList<>();
        for (CriterionDto dto : dtos) {
            Criterion criterion = Criterion.builder()
                    .tender(tender)
                    .criterionRef(dto.getCriterionRef())
                    .description(dto.getDescription())
                    .criterionType(dto.getCriterionType())
                    .mandatory(dto.isMandatory())
                    .thresholdValue(dto.getThresholdValue())
                    .thresholdOperator(dto.getThresholdOperator())
                    .sourcePage(dto.getSourcePage())
                    .extractionConfidence(BigDecimal.valueOf(dto.getConfidence()))
                    .confirmedByOfficer(false)  // Must be confirmed before evaluation
                    .build();
            saved.add(criterionRepository.save(criterion));
        }

        log.info("Extracted and saved {} criteria for tender {}", saved.size(), tender.getTenderRef());
        return saved;
    }

    // ── LLM call with retry ───────────────────────────────────────────────────

    private List<CriterionDto> extractWithRetry(String documentText, int maxRetries) {
        // Truncate very long documents — llama3.2 has context limits
        String truncated = truncate(documentText, 12000);
        String prompt = EXTRACTION_PROMPT.replace("{{DOCUMENT}}", truncated);

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                return parseResponse(response);

            } catch (Exception ex) {
                lastException = ex;
                log.warn("LLM extraction attempt {}/{} failed: {}", attempt, maxRetries, ex.getMessage());
                // Small backoff between retries
                try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }

        log.error("All {} extraction attempts failed", maxRetries, lastException);
        return List.of(); // Return empty rather than crash — officer can add manually
    }

    private List<CriterionDto> parseResponse(String rawResponse) throws Exception {
        // Strip any markdown code blocks the LLM might add despite instructions
        String cleaned = rawResponse.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }

        LlmExtractionResponse response = objectMapper.readValue(cleaned, LlmExtractionResponse.class);

        if (response.getCriteria() == null || response.getCriteria().isEmpty()) {
            throw new RuntimeException("LLM returned empty criteria list");
        }

        double confidenceThreshold = props.getLlm().getConfidenceThreshold();

        // Filter out very low confidence extractions
        return response.getCriteria().stream()
                .filter(c -> c.getConfidence() >= confidenceThreshold)
                .toList();
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        // Keep beginning and end — most criteria are front-loaded or in appendices
        int half = maxChars / 2;
        return text.substring(0, half) + "\n\n[... DOCUMENT TRUNCATED ...]\n\n" + text.substring(text.length() - half);
    }
}