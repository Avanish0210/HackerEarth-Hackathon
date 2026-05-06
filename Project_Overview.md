# HackerEarth Hackathon Project

## 1. What is this project?
This project is a dual-backend government modernization platform designed for procurement automation and citizen data synchronization.

The two backend systems are:

1. **TenderLens**
   - An AI-powered procurement evaluation engine.
   - Purpose: Automate tender document analysis, criteria extraction, bidder evaluation, and report generation.
   - Used by procurement officers to speed up tender scoring and reduce manual bias.

2. **UBID Bridge**
   - A citizen data synchronization system.
   - Purpose: Keep citizen records consistent across multiple government departments using a unique identifier (UBID).
   - Handles webhook events, scheduled polling, conflict detection, idempotency, and audit logging.

## 2. What are the two backend systems?

### TenderLens
- Receives tender PDFs and parses them.
- Uses AI / LLMs to extract evaluation criteria automatically.
- Allows officers to confirm and refine extracted criteria.
- Accepts bidder submission PDFs and runs OCR/document parsing.
- Evaluates each bidder against confirmed tender criteria.
- Publishes results, metrics, and audit trails.
- Built with Spring Boot, Kafka, Redis, PostgreSQL, OCR, and AI integration.

### UBID Bridge
- Receives events from the State Welfare System (SWS) and other departments.
- Maps each citizen update to a UBID and looks up department subscriptions.
- Translates the event payload into department-specific formats.
- Uses Kafka to reliably propagate events to connected systems.
- Supports polling-based departments through snapshot diffing.
- Tracks idempotency to avoid duplicate updates.
- Detects conflicts and stores them for resolution.
- Maintains a full immutable audit feed.

## 3. What should the frontend be?
The frontend should be a unified dashboard and operational console with the following capabilities:

### For TenderLens
- Tender upload UI with file selection and metadata input.
- Criteria review page showing extracted criteria and confidence.
- Bidder upload and tracking screen.
- Evaluation dashboard with bidder scores, eligibility statuses, and detail drill-downs.
- Review queue for manual decisions when AI confidence is low.
- Audit log viewer for tenders, bidders, and evaluations.

### For UBID Bridge
- Event inbox / monitoring panel.
- Department registry UI showing systems connected to each UBID.
- Conflict dashboard for pending and resolved conflicts.
- Snapshot and polling status display.
- Audit feed and event history with filters by UBID, source, or event type.
- Demo/test controls for firing sample SWS events and conflict scenarios.

### Shared frontend needs
- Login and authorization for different user roles.
- System health and metrics overview.
- Kafka and queue status indicators.
- Links to API documentation.
- Clear distinction between procurement workflows vs citizen data sync workflows.

## 4. Main thinking and logic of the project

### Core principles
- **Automation**: Reduce manual effort by leveraging AI, OCR, and event-driven processing.
- **Reliability**: Use Kafka, retries, and idempotency for robust backend operations.
- **Auditability**: Keep a full history of actions, changes, and event propagation.
- **Separation of concerns**: One backend handles procurement intelligence, the other handles data synchronization.
- **Human oversight**: Escalate ambiguous cases for manual review rather than trusting automation blindly.

### TenderLens logic
1. **Tender ingestion**: Accept tender documents and parse metadata.
2. **Criteria extraction**: Use LLMs to discover eligibility and technical requirements.
3. **User confirmation**: Let officers verify or correct extracted criteria.
4. **Bidder ingestion**: Accept bidder proposal files, run OCR if needed.
5. **Evaluation**: Compare bidder data to criteria, generate verdicts, and calculate confidence.
6. **Review queue**: Forward low-confidence or disputed evaluations to humans.
7. **Reporting**: Produce evaluative reports and audit logs.

### UBID Bridge logic
1. **Event reception**: Accept events from the State Welfare System or demo endpoints.
2. **Registry lookup**: Find all departments that must receive the update.
3. **Translation**: Convert generic UBID events into department-specific payloads.
4. **Kafka propagation**: Publish tasks to Kafka for reliable delivery.
5. **Consumption**: Process each department update, call target APIs, and acknowledge success.
6. **Idempotency**: Keep records of processed events so the same update is not applied twice.
7. **Conflict detection**: Identify mismatched updates and queue for resolution.
8. **Polling and snapshots**: Periodically poll systems that cannot push updates and synchronize deltas.
9. **Audit feed**: Record every action in an append-only audit trail.

## 5. Suggested frontend architecture
- Single-page application built with React, Vue, or Angular.
- Use a shared API client to consume both backend systems.
- Provide separate routes for:
  - `/tenderlens` (tender management)
  - `/ubid` (UBID sync operations)
  - `/dashboard` (overall health and metrics)
  - `/audit` (logs and history)
  - `/conflicts` (resolution interface)
- Use visual cards, tables, and progress indicators for operational clarity.
- Add file upload components for tenders and bidder documents.
- Add event simulator controls for UBID testing.

## 6. Summary
This project is about combining two enterprise backend solutions in a single government modernization initiative:
- TenderLens automates procurement evaluation using AI.
- UBID Bridge synchronizes citizen data across departments using event-driven architecture.

The frontend must bind both systems together with intuitive dashboards, upload/review workflows, event monitoring, and audit visibility. The core idea is to make complex government workflows fast, consistent, and trustworthy.