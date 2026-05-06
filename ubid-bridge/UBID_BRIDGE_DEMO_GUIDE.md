# UBID Bridge Demo Guide

This guide is for showcasing UBID Bridge to seniors: what to start, what to click, what each page proves, and which frontend test cases demonstrate the backend working end to end.

## 1. What This Project Demonstrates

UBID Bridge is a synchronization layer between the Single Window System (SWS) and multiple department systems.

It demonstrates:

- SWS event intake through REST APIs.
- UBID-based routing to departments that carry the citizen record.
- Translation from canonical SWS fields into department-specific payloads.
- Kafka-based propagation tasks.
- Mock department writes.
- Idempotency handling through Redis.
- Audit logging in PostgreSQL.
- Conflict queue and manual resolution flow.
- A React dashboard for live operational visibility.

## 2. Services Needed Before The Demo

Start these services first:

- PostgreSQL on `localhost:5432`
- Kafka on `localhost:9092`
- Redis on `localhost:6379`
- Spring Boot backend on `http://localhost:8080/api/ubid`
- React frontend on `http://localhost:3000`

Backend config is in:

`src/main/resources/application.yaml`

Important backend settings:

- Server port: `8080`
- Context path: `/api/ubid`
- PostgreSQL database: `hackathon_db`
- Kafka topic used by router: `ubid-propagation-tasks`
- React frontend origin: `localhost` or `127.0.0.1`

## 3. Start Commands

Backend:

```powershell
mvn spring-boot:run
```

Frontend:

```powershell
cd frontend
npm.cmd run dev
```

Open:

```text
http://localhost:3000
```

## 4. Mandatory Demo Data Check

Before firing events, confirm that the dashboard shows active departments.

Open the Dashboard page and check:

- Backend reachable
- Active departments should be greater than `0`
- Registered translators should show `DEPT_A`, `DEPT_B`, `DEPT_C`

If active departments is `0`, the backend is running but no UBID has been registered yet. The demo router can auto-register a new UBID to the configured demo departments when the first event arrives.

For the demo UBID, insert department registry rows:

```sql
insert into ubid.department_registry
  (ubid, department_id, department_name, integration_type, active, registered_at)
values
  ('UBID-DEMO-001', 'DEPT_A', 'Revenue Department', 'WEBHOOK', true, now()),
  ('UBID-DEMO-001', 'DEPT_B', 'Municipal Corporation', 'POLLING', true, now()),
  ('UBID-DEMO-001', 'DEPT_C', 'Utility Department', 'SNAPSHOT', true, now());
```

After inserting, refresh the Dashboard. This manual SQL is optional if the auto-registration fallback is enabled in `EventRouter`.

## 5. Backend Health Checks To Show

Use these endpoints to prove the backend is live.

Dashboard stats:

```text
GET http://localhost:8080/api/ubid/dashboard/stats
```

Expected result:

- HTTP `200`
- JSON containing `totalEvents`, `successCount`, `pendingConflicts`, `activeDepartments`, `registeredTranslators`

Audit feed:

```text
GET http://localhost:8080/api/ubid/audit/feed
```

Expected result:

- HTTP `200`
- JSON array of recent audit logs

Mock state:

```text
GET http://localhost:8080/api/ubid/mock/state
```

Expected result:

- HTTP `200`
- JSON grouped by `DEPT_A`, `DEPT_B`, `DEPT_C`, and `SWS`

## 6. Frontend Demo Flow

### Step 1: Dashboard

Open the Dashboard page.

Show:

- Backend connection indicator.
- Stats cards.
- Registered translators.
- Live audit feed.
- Pending conflict count.

Explain:

The dashboard is polling backend APIs and showing operational state from PostgreSQL and backend services.

### Step 2: Event Router

Go to Event Router.

Click:

```text
Fire address demo
```

This calls:

```text
POST /api/ubid/events/demo
```

Expected:

- Toast says event accepted.
- Backend creates a demo `ADDRESS_CHANGE` event.
- Router finds departments registered for `UBID-DEMO-001`.
- Router publishes propagation tasks to Kafka.
- Consumer writes translated payloads into mock departments.
- Audit records are created.

Then go back to Dashboard and show:

- Total events increased.
- Success count increased after Kafka consumer processes tasks.
- Audit feed has new rows.

### Step 3: Mock Dept State

Go to Mock Dept State.

Click:

```text
Lookup UBID
```

Use:

```text
UBID-DEMO-001
```

Expected:

- DEPT_A has Revenue-style data.
- DEPT_B has Municipal-style data.
- DEPT_C has Utility-style data.
- This proves the same SWS event reached different department stores.

Also show Full Mock State.

### Step 4: Audit Trail

Go to Audit Trail.

Search by UBID:

```text
UBID-DEMO-001
```

Expected:

- Audit rows for every target department.
- Each row shows source, target, event type, status, and timestamp.

Explain:

This proves traceability. Every propagation attempt becomes an audit record.

### Step 5: Custom Event

Go to Event Router.

Use the custom event form:

UBID:

```text
UBID-DEMO-001
```

Event type:

```text
ADDRESS_CHANGE
```

Fields JSON:

```json
{
  "street": "88 Civil Lines",
  "city": "Prayagraj",
  "state": "Uttar Pradesh",
  "pincode": "211001",
  "district": "Prayagraj"
}
```

Click:

```text
Submit event
```

Expected:

- Event accepted.
- Mock department state changes.
- New audit records appear.

## 7. Frontend Test Cases

### Test Case 1: Backend Connectivity

Page:

```text
Dashboard
```

Action:

Open the dashboard.

Expected:

- Connection card says backend reachable.
- Stats load without error.
- Audit feed panel is visible.

If it says backend offline:

- Confirm Spring Boot is running.
- Open `http://localhost:8080/api/ubid/dashboard/stats`.
- Use `http://localhost:3000`, not only `127.0.0.1`, unless backend has the latest CORS fix.

### Test Case 2: Demo Event Fan-Out

Page:

```text
Event Router
```

Action:

Click `Fire address demo`.

Expected:

- Toast: event accepted.
- Dashboard total events increases.
- Audit Trail search for `UBID-DEMO-001` shows success rows.
- Mock Dept State shows data in department stores.

Important:

If the event is accepted but audit and mock stores do not change, check Kafka and backend logs first. The demo router auto-registers new UBIDs to the configured demo departments.

### Test Case 3: Custom Address Change

Page:

```text
Event Router
```

Action:

Submit a custom `ADDRESS_CHANGE` event with changed address fields.

Expected:

- Backend accepts the event.
- New audit rows are visible.
- Mock department records update with translated data.

### Test Case 4: Audit Search By UBID

Page:

```text
Audit Trail
```

Action:

Search:

```text
UBID-DEMO-001
```

Expected:

- Search result count is greater than `0`.
- Rows show `SWS -> DEPT_A`, `SWS -> DEPT_B`, and `SWS -> DEPT_C` after a successful demo event.

### Test Case 5: Audit Search By Event ID

Page:

```text
Event Router
```

Action:

Fire an event and copy the returned `eventId` from the response block.

Then go to:

```text
Audit Trail
```

Switch search mode to:

```text
Event ID
```

Paste the event ID.

Expected:

- Only records belonging to that event are shown.
- This proves event-level traceability.

### Test Case 6: Mock Department Lookup

Page:

```text
Mock Dept State
```

Action:

Lookup:

```text
UBID-DEMO-001
```

Expected:

- DEPT_A, DEPT_B, DEPT_C, and SWS panels return records.
- Department payloads may have different field names because each translator maps canonical SWS fields into department-specific schema.

### Test Case 7: Reset Mock Stores

Page:

```text
Mock Dept State
```

Action:

Click `Reset stores`.

Expected:

- Mock stores become empty.
- Audit logs do not disappear because audit logs are stored in PostgreSQL and are append-only.

Explain:

This shows the difference between temporary demo department state and permanent audit history.

### Test Case 8: Conflict Queue Screen

Page:

```text
Conflict Queue
```

Action:

Click `Fire conflict demo` from Event Router.

Expected:

- If the configured policy escalates conflicts manually, the conflict appears in the queue.
- Select a conflict, choose value A or B, enter resolution note, and click `Mark resolved`.
- Dashboard pending conflicts count decreases.
- Audit Trail shows a conflict or manual resolution record.

Important:

The current backend config uses:

```yaml
default-conflict-policy: LAST_WRITE_WINS
```

Also, SWS-related conflicts use source priority in the backend. Because of that, the conflict queue may stay empty unless the backend policy/path creates a manual escalation. For a live conflict-queue demo, set the relevant policy to `MANUAL_ESCALATION` and restart the backend, or seed a conflict row directly for presentation.

Example seed for queue-only UI demonstration:

```sql
insert into ubid.conflict_queue
  (event_id, ubid, field_name, source_a, value_a, source_b, value_b,
   resolution_policy, resolved, created_at)
values
  ('DEMO-CONFLICT-001', 'UBID-DEMO-001', 'pincode',
   'DEPT_B', '221001', 'DEPT_C', '211001',
   'MANUAL_ESCALATION', false, now());
```

Then refresh the Conflict Queue page.

## 8. What To Say While Presenting

Use this explanation:

UBID Bridge acts as a reliable synchronization layer. When SWS receives a citizen update, it sends a canonical event to the bridge. The bridge looks up which departments hold that UBID, translates the payload for each department, and publishes propagation tasks through Kafka. A consumer writes to the target systems and records every outcome in the audit log. The frontend is not fake data; it is reading stats, audit entries, conflicts, and mock department state from backend APIs.

For departments that cannot accept direct webhooks, the same architecture supports polling and snapshot integrations. The demo departments are in-memory mock systems, but the routing, audit, idempotency, and conflict-resolution structure mirrors a production integration layer.

## 9. Common Demo Problems

### Backend reachable but frontend says offline

Cause:

CORS or wrong URL.

Fix:

- Open frontend with `http://localhost:3000`.
- Confirm backend allows `localhost` and `127.0.0.1`.
- Restart Spring Boot after CORS changes.

### Event accepted but no audit records

Cause:

No active departments registered for that UBID, Kafka is not running, or consumer is not processing tasks.

Fix:

- Insert department registry rows for `UBID-DEMO-001`.
- Confirm Kafka is running on `localhost:9092`.
- Watch backend logs after firing event.

### Mock state stays empty

Cause:

The event reached `/events/demo`, but propagation tasks were not consumed.

Fix:

- Check Kafka.
- Check backend logs for `Processing task`.
- Check that department registry rows exist.

### Conflict queue stays empty

Cause:

The backend conflict policy may resolve automatically instead of escalating.

Fix:

- Use `MANUAL_ESCALATION` for the conflict demo path, or seed a conflict row for UI demonstration.

## 10. Best Senior Demo Order

Use this order for the cleanest presentation:

1. Dashboard: show backend is connected.
2. Mock Dept State: reset stores.
3. Event Router: fire address demo.
4. Dashboard: show stats and audit feed changed.
5. Mock Dept State: lookup `UBID-DEMO-001`.
6. Audit Trail: search by UBID.
7. Event Router: submit custom event.
8. Mock Dept State: show updated department records.
9. Conflict Queue: show seeded or manually escalated conflict resolution.
10. Audit Trail: show final traceability.
