package com.HackerEarth.Hackathon.TenderLens.service;

import com.HackerEarth.Hackathon.TenderLens.config.TenderLensProperties;
import com.HackerEarth.Hackathon.TenderLens.dto.LlmExtractionResponse;
import com.HackerEarth.Hackathon.TenderLens.entity.Bidder;
import com.HackerEarth.Hackathon.TenderLens.entity.Criterion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * For each confirmed criterion, asks the LLM to find the corresponding
 * evidence in the bidder's document.
 *
 * Returns a list of EvidenceResult — one per criterion — with:
 *   - extractedValue  (what the LLM found)
 *   - verbatimExcerpt (exact text from the document)
 *   - sourcePage
 *   - confidence (0.0-1.0)
 *   - found (false = LLM explicitly couldn't find it)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidderParserService {

    private final ChatClient chatClient;
    private final TenderLensProperties props;
    private final ObjectMapper objectMapper;

    private static final String EVIDENCE_PROMPT = """
            You are a procurement evaluator AI. Find evidence in the bidder document for ONE specific requirement.
            
            REQUIREMENT:
            - Description: {{DESCRIPTION}}
            - Type: {{TYPE}}
            - Threshold: {{OPERATOR}} {{THRESHOLD}}
            - Mandatory: {{MANDATORY}}
            
            BIDDER DOCUMENT:
            {{DOCUMENT}}
            
            Return ONLY valid JSON (no markdown, no explanation):
            {
              "found": true|false,
              "extractedValue": "the actual value found (e.g., '7.2 crore', 'ISO 9001:2015 certified')",
              "verbatimExcerpt": "exact quote from the document proving this",
              "sourcePage": 3,
              "confidence": 0.92,
              "notFoundReason": "why it was not found (only if found=false)"
            }
            
            Rules:
            - found=false ONLY when you genuinely cannot locate relevant evidence
            - confidence reflects how certain you are about the extraction
            - verbatimExcerpt must be word-for-word from the document
            - If you find partial evidence, set found=true with lower confidence
            """;

    public List<EvidenceResult> extractEvidenceForAllCriteria(
            Bidder bidder,
            List<Criterion> criteria,
            String bidderDocumentText) {

        List<EvidenceResult> results = new ArrayList<>();

        for (Criterion criterion : criteria) {
            EvidenceResult result = extractForOneCriterion(criterion, bidderDocumentText);
            result.setCriterionId(criterion.getId());
            result.setCriterionRef(criterion.getCriterionRef());
            results.add(result);
        }

        log.info("Extracted evidence for {}/{} criteria for bidder {}",
                results.stream().filter(EvidenceResult::isFound).count(),
                criteria.size(),
                bidder.getBidderRef());

        return results;
    }

    // ── Per-criterion extraction ──────────────────────────────────────────────

    private EvidenceResult extractForOneCriterion(Criterion criterion, String documentText) {
        String truncated = truncate(documentText, 10000);
        String prompt = EVIDENCE_PROMPT
                .replace("{{DESCRIPTION}}", safePromptText(criterion.getDescription()))
                .replace("{{TYPE}}", safePromptText(criterion.getCriterionType()))
                .replace("{{OPERATOR}}", safePromptText(criterion.getThresholdOperator()))
                .replace("{{THRESHOLD}}", safePromptText(criterion.getThresholdValue() != null ? criterion.getThresholdValue() : "N/A"))
                .replace("{{MANDATORY}}", criterion.isMandatory() ? "YES (disqualifying if missing)" : "NO (preferred)")
                .replace("{{DOCUMENT}}", safePromptText(truncated));

        int maxRetries = props.getLlm().getMaxParseRetries();
        Exception lastEx = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                return parseEvidenceResponse(response, criterion);

            } catch (Exception ex) {
                lastEx = ex;
                log.warn("Evidence extraction attempt {}/{} failed for criterion {}: {}",
                        attempt, maxRetries, criterion.getCriterionRef(), ex.getMessage());
                try { Thread.sleep(500L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }

        log.error("All evidence extraction attempts failed for criterion {}", criterion.getCriterionRef(), lastEx);
        // Return a "not found" result rather than crashing
        return EvidenceResult.builder()
                .criterionId(criterion.getId())
                .criterionRef(criterion.getCriterionRef())
                .found(false)
                .confidence(0.0)
                .notFoundReason("LLM extraction failed after " + maxRetries + " attempts")
                .build();
    }

    private EvidenceResult parseEvidenceResponse(String rawResponse, Criterion criterion) throws Exception {
        String cleaned = rawResponse.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }

        LlmExtractionResponse response = objectMapper.readValue(cleaned, LlmExtractionResponse.class);

        return EvidenceResult.builder()
                .criterionId(criterion.getId())
                .criterionRef(criterion.getCriterionRef())
                .found(response.isFound())
                .extractedValue(response.getExtractedValue())
                .verbatimExcerpt(response.getVerbatimExcerpt())
                .sourcePage(response.getSourcePage())
                .confidence(response.getConfidence())
                .notFoundReason(response.getNotFoundReason())
                .build();
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        int half = maxChars / 2;
        return text.substring(0, half) + "\n\n[...]\n\n" + text.substring(text.length() - half);
    }

    private String safePromptText(String value) {
        if (value == null) return "";
        return value.replace("\r", " ").replace("\n", " ");
    }

    // ── EvidenceResult ────────────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EvidenceResult {
        private Long criterionId;
        private String criterionRef;
        private boolean found;
        private String extractedValue;
        private String verbatimExcerpt;
        private Integer sourcePage;
        private double confidence;
        private String notFoundReason;
    }
}