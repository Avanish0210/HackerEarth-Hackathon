package com.HackerEarth.Hackathon.ubidbridge.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "ubid.bridge")
public class UbidBridgeProperties {

    private long idempotencyTtlSeconds = 604800;
    private long conflictWindowMs      = 30000;
    private String defaultConflictPolicy = "LAST_WRITE_WINS";

    private Retry retry = new Retry();
    private Poller poller = new Poller();
    private List<Department> departments;

    @Data
    public static class Retry {
        private int maxAttempts       = 5;
        private long initialIntervalMs = 1000;
        private double multiplier     = 2.0;
        private long maxIntervalMs    = 30000;
    }

    @Data
    public static class Poller {
        private String cron = "0/60 * * * * *";
    }

    @Data
    public static class Department {
        private String id;
        private String name;
        private String baseUrl;
        private String integrationType;   // WEBHOOK | POLLING | SNAPSHOT
        private String translator;
    }
}
