package com.HackerEarth.Hackathon.TenderLens.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tenderlens")
public class TenderLensProperties {

    private long idempotencyTtlSeconds = 86400;

    private Llm llm = new Llm();
    private Ocr ocr = new Ocr();
    private Evaluation evaluation = new Evaluation();
    private Report report = new Report();
    private Topics topics = new Topics();

    @Data
    public static class Llm {
        private double confidenceThreshold = 0.75;
        private int maxParseRetries = 3;
    }

    @Data
    public static class Ocr {
        private String tessdataPath = "/usr/share/tesseract-ocr/5/tessdata";
        private String language = "eng";
        private int dpi = 300;
    }

    @Data
    public static class Evaluation {
        private int parallelBidderThreads = 4;
        private String topic = "tenderlens-evaluation-jobs";
    }

    @Data
    public static class Report {
        private String outputDir = "/tmp/tenderlens/reports";
    }

    @Data
    public static class Topics {
        private String evaluationJobs  = "tenderlens-evaluation-jobs";
        private String reviewQueue     = "tenderlens-review-queue";
        private String auditEvents     = "tenderlens-audit-events";
    }
}
