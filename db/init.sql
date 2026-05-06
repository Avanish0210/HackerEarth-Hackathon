-- =============================================================================
-- HACKATHON PLATFORM — DATABASE INIT SCRIPT
-- Schemas: ubid | tenderlens
-- Run automatically by PostgreSQL on first container start
-- =============================================================================

-- ── Create schemas ────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS ubid;
CREATE SCHEMA IF NOT EXISTS tenderlens;

-- =============================================================================
-- SCHEMA: ubid  (UBID Bridge — Problem 1)
-- =============================================================================

-- Department registry — which departments carry a record for a given UBID
CREATE TABLE IF NOT EXISTS ubid.department_registry (
    id                  BIGSERIAL PRIMARY KEY,
    ubid                VARCHAR(100)    NOT NULL,
    department_id       VARCHAR(50)     NOT NULL,
    department_name     VARCHAR(200)    NOT NULL,
    integration_type    VARCHAR(20)     NOT NULL,   -- WEBHOOK | POLLING | SNAPSHOT
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    registered_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (ubid, department_id)
);

-- Idempotency store (mirror of Redis — backup for audit purposes)
CREATE TABLE IF NOT EXISTS ubid.idempotency_log (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            VARCHAR(100)    NOT NULL,
    ubid                VARCHAR(100)    NOT NULL,
    target_system       VARCHAR(50)     NOT NULL,
    processed_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (event_id, ubid, target_system)
);

-- Append-only audit log — never updated, never deleted
CREATE TABLE IF NOT EXISTS ubid.audit_log (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            VARCHAR(100)    NOT NULL,
    ubid                VARCHAR(100)    NOT NULL,
    source_system       VARCHAR(50)     NOT NULL,
    target_system       VARCHAR(50)     NOT NULL,
    event_type          VARCHAR(100)    NOT NULL,   -- e.g. ADDRESS_CHANGE
    field_changed       VARCHAR(100),
    old_value           TEXT,
    new_value           TEXT,
    status              VARCHAR(20)     NOT NULL,   -- SUCCESS | FAILED | SKIPPED | CONFLICT
    conflict_policy     VARCHAR(30),                -- LWW | SOURCE_PRIORITY | MANUAL_ESCALATION
    retry_count         INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
-- No UPDATE or DELETE ever issued on this table by the application

-- Conflict queue — holds unresolved conflicts for manual review
CREATE TABLE IF NOT EXISTS ubid.conflict_queue (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            VARCHAR(100)    NOT NULL,
    ubid                VARCHAR(100)    NOT NULL,
    field_name          VARCHAR(100)    NOT NULL,
    source_a            VARCHAR(50)     NOT NULL,
    value_a             TEXT            NOT NULL,
    source_b            VARCHAR(50)     NOT NULL,
    value_b             TEXT            NOT NULL,
    resolution_policy   VARCHAR(30)     NOT NULL,
    resolved            BOOLEAN         NOT NULL DEFAULT FALSE,
    resolved_by         VARCHAR(100),               -- officer who resolved
    resolved_value      TEXT,
    resolution_note     TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ
);

-- Department snapshot store (for SNAPSHOT integration type diff detection)
CREATE TABLE IF NOT EXISTS ubid.department_snapshot (
    id                  BIGSERIAL PRIMARY KEY,
    department_id       VARCHAR(50)     NOT NULL,
    ubid                VARCHAR(100)    NOT NULL,
    field_name          VARCHAR(100)    NOT NULL,
    field_value         TEXT,
    value_hash          VARCHAR(64)     NOT NULL,   -- SHA-256 of field_value
    snapshot_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (department_id, ubid, field_name)
);

-- Indexes for audit log queries
CREATE INDEX IF NOT EXISTS idx_audit_ubid        ON ubid.audit_log (ubid);
CREATE INDEX IF NOT EXISTS idx_audit_event_id    ON ubid.audit_log (event_id);
CREATE INDEX IF NOT EXISTS idx_audit_created_at  ON ubid.audit_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_conflict_ubid     ON ubid.conflict_queue (ubid);
CREATE INDEX IF NOT EXISTS idx_conflict_resolved ON ubid.conflict_queue (resolved);

-- =============================================================================
-- SCHEMA: tenderlens  (TenderLens — Problem 2)
-- =============================================================================

-- Tender documents uploaded by procurement officers
CREATE TABLE IF NOT EXISTS tenderlens.tender (
    id                  BIGSERIAL PRIMARY KEY,
    tender_ref          VARCHAR(100)    NOT NULL UNIQUE,   -- e.g. TENDER-2025-001
    title               VARCHAR(500)    NOT NULL,
    uploaded_by         VARCHAR(100)    NOT NULL,
    file_name           VARCHAR(300)    NOT NULL,
    file_path           VARCHAR(500)    NOT NULL,
    ocr_required        BOOLEAN         NOT NULL DEFAULT FALSE,
    status              VARCHAR(30)     NOT NULL DEFAULT 'UPLOADED',
    -- UPLOADED | CRITERIA_EXTRACTED | CRITERIA_CONFIRMED | EVALUATION_RUNNING | COMPLETED
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Criteria extracted from the tender document by the LLM
CREATE TABLE IF NOT EXISTS tenderlens.criterion (
    id                  BIGSERIAL PRIMARY KEY,
    tender_id           BIGINT          NOT NULL REFERENCES tenderlens.tender(id),
    criterion_ref       VARCHAR(50)     NOT NULL,          -- C-001, C-002 ...
    description         TEXT            NOT NULL,
    criterion_type      VARCHAR(30)     NOT NULL,          -- FINANCIAL | TECHNICAL | COMPLIANCE | CERTIFICATION
    is_mandatory        BOOLEAN         NOT NULL DEFAULT TRUE,
    threshold_value     VARCHAR(500),                      -- e.g. "5 crore", "ISO 9001"
    threshold_operator  VARCHAR(10),                       -- GTE | LTE | EQ | CONTAINS
    source_page         INT,
    confirmed_by_officer BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Bidder submissions
CREATE TABLE IF NOT EXISTS tenderlens.bidder (
    id                  BIGSERIAL PRIMARY KEY,
    tender_id           BIGINT          NOT NULL REFERENCES tenderlens.tender(id),
    bidder_ref          VARCHAR(100)    NOT NULL,
    company_name        VARCHAR(300)    NOT NULL,
    file_name           VARCHAR(300)    NOT NULL,
    file_path           VARCHAR(500)    NOT NULL,
    ocr_required        BOOLEAN         NOT NULL DEFAULT FALSE,
    parse_status        VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    -- PENDING | PARSING | PARSED | FAILED
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (tender_id, bidder_ref)
);

-- Per-criterion per-bidder evaluation results
CREATE TABLE IF NOT EXISTS tenderlens.evaluation (
    id                  BIGSERIAL PRIMARY KEY,
    tender_id           BIGINT          NOT NULL REFERENCES tenderlens.tender(id),
    bidder_id           BIGINT          NOT NULL REFERENCES tenderlens.bidder(id),
    criterion_id        BIGINT          NOT NULL REFERENCES tenderlens.criterion(id),
    extracted_value     TEXT,                              -- what was found in bidder doc
    source_document     VARCHAR(300),
    source_page         INT,
    verbatim_excerpt    TEXT,                              -- exact text from doc
    verdict             VARCHAR(20),
    -- ELIGIBLE | NOT_ELIGIBLE | NEEDS_REVIEW
    confidence_score    NUMERIC(4,3),                      -- 0.000 to 1.000
    ocr_quality_flag    BOOLEAN         NOT NULL DEFAULT FALSE,
    llm_call_id         VARCHAR(100),                      -- trace ID for LLM call
    evaluated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (tender_id, bidder_id, criterion_id)
);

-- Human review queue — low confidence / ambiguous evaluations
CREATE TABLE IF NOT EXISTS tenderlens.review_queue (
    id                  BIGSERIAL PRIMARY KEY,
    evaluation_id       BIGINT          NOT NULL REFERENCES tenderlens.evaluation(id),
    reason              VARCHAR(50)     NOT NULL,
    -- LOW_CONFIDENCE | OCR_QUALITY | AMBIGUOUS_VALUE | NOT_FOUND
    reviewer            VARCHAR(100),
    reviewed            BOOLEAN         NOT NULL DEFAULT FALSE,
    override_verdict    VARCHAR(20),                       -- officer's final call
    review_note         TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    reviewed_at         TIMESTAMPTZ
);

-- Immutable audit log for every LLM extraction and comparison step
CREATE TABLE IF NOT EXISTS tenderlens.audit_log (
    id                  BIGSERIAL PRIMARY KEY,
    tender_id           BIGINT          NOT NULL,
    bidder_id           BIGINT,
    criterion_id        BIGINT,
    action              VARCHAR(50)     NOT NULL,
    -- CRITERION_EXTRACTED | BIDDER_PARSED | VERDICT_GENERATED | REVIEW_ROUTED | REPORT_GENERATED
    detail              JSONB,                             -- full context as JSON
    llm_call_id         VARCHAR(100),
    performed_by        VARCHAR(100),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Generated PDF reports
CREATE TABLE IF NOT EXISTS tenderlens.report (
    id                  BIGSERIAL PRIMARY KEY,
    tender_id           BIGINT          NOT NULL REFERENCES tenderlens.tender(id),
    file_path           VARCHAR(500)    NOT NULL,
    generated_by        VARCHAR(100),
    generated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_tl_eval_tender   ON tenderlens.evaluation (tender_id);
CREATE INDEX IF NOT EXISTS idx_tl_eval_bidder   ON tenderlens.evaluation (bidder_id);
CREATE INDEX IF NOT EXISTS idx_tl_eval_verdict  ON tenderlens.evaluation (verdict);
CREATE INDEX IF NOT EXISTS idx_tl_review_done   ON tenderlens.review_queue (reviewed);
CREATE INDEX IF NOT EXISTS idx_tl_audit_tender  ON tenderlens.audit_log (tender_id);
CREATE INDEX IF NOT EXISTS idx_tl_criterion     ON tenderlens.criterion (tender_id);

-- =============================================================================
-- SEED DATA — Mock departments for UBID Bridge demo
-- =============================================================================
INSERT INTO ubid.department_registry (ubid, department_id, department_name, integration_type)
VALUES
    ('UBID-DEMO-001', 'DEPT_A', 'Revenue Department',    'WEBHOOK'),
    ('UBID-DEMO-001', 'DEPT_B', 'Municipal Corporation', 'POLLING'),
    ('UBID-DEMO-001', 'DEPT_C', 'Utility Department',    'SNAPSHOT'),
    ('UBID-DEMO-002', 'DEPT_A', 'Revenue Department',    'WEBHOOK'),
    ('UBID-DEMO-002', 'DEPT_B', 'Municipal Corporation', 'POLLING')
ON CONFLICT (ubid, department_id) DO NOTHING;

-- =============================================================================
-- DONE
-- =============================================================================
