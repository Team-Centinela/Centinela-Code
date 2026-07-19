# Saga Pattern (Orchestrated Long-Running Transactions)

For multi-step business operations that cross module boundaries — such as "transaction → evaluate → case created → alert raised" — a **Saga** coordinates atomic units through the message broker rather than distributed transactions.

## Why This Matters in Centinela

The end-to-end pipeline in `architecture/04-event-driven-communication.md` is a long-running, multi-actor process:

```
Transaction Received (Ingestion API)
       │
       ▼
Rule Engine Scoring on the Serverless Engine (FraudEvaluationCompleted)
       │
       ▼
Case Created on the Core Backend (CaseOpened, only if score > threshold)
       │
       ▼
Alert Raised on the Core Backend (FraudAlertRaised)
       │
       ▼
Analyst resolves the case on the Core Backend (CaseResolved)
```

No global 2PC. Each step is its own ACID transaction locally plus a domain event for the next actor. This is a **chained-orchestration saga**: each module's command handler responds to the previous event, performs the local work, and emits the next event.

## Saga Catalog

| Saga Name | Initiated When | Steps | Termination |
|---|---|---|---|
| **Case Lifecycle Saga** | `TransactionReceived` event published by Ingestion API | 1. Score on the Serverless Engine (Rule Engine) — emits `FraudEvaluationCompleted`<br/>2. If score > threshold → open case on the Core Backend (Case Module) — emits `CaseOpened`<br/>3. Raise alert on the Core Backend (Alert Module) — emits `FraudAlertRaised`<br/>4. Analyst assignment + resolution on the Core Backend — emits `CaseResolved` | `CaseResolved` marks saga terminal |
| **Document Verification Saga** | Analyst uploads verification document to a case | 1. Upload blob + write `DocumentRecord` on the Core Backend — emits `DocumentPending`<br/>2. OCR Worker extracts fields — emits `DocumentProcessed`<br/>3. Core Backend appends extracted data to case — emits `CaseDataAppended`<br/>4. Alert closes OCR-related findings | `CaseDataAppended` marks saga terminal |

## Compensating Actions

If a step fails after the saga has progressed, **compensating actions** reverse prior local effects where possible. Centinela's compensations are limited to:

| Step that failed | Compensation |
|---|---|
| Case creation fails (DB error) | Mark transaction as `score_saved_no_case`; manual review by admin. No partial state to undo — the transaction row exists from the scoring step. |
| Alert raising fails | Retry with exponential backoff via Service Bus DLQ redelivery. If exhausted, set `alert.status = FAILED_PUBLISH` and notify admin. |
| OCR fails | OCR Worker returns `FAILED` status; `DocumentRecord.status = FAILED`; human reviewer can re-trigger OCR. |
| Analyst resolution fails (DB write conflict) | Operational: return 409 to UI; reject the optimistic-update path; no compensation needed. |

Compensating actions are **explicit** documented code paths, never implicit. ADR-001 insists on this — note this in any future saga implementation.

## Saga vs Outbox

Distinct concerns:

- **Outbox** (`patterns/03-outbox-pattern.md`): guarantees *each module's* local write + event is atomic. A module building block, not a cross-module coordination primitive.
- **Saga**: orchestrates a business workflow across modules over async events. A cross-module coordination primitive.

Both are required and they layer on each other: **saga steps run inside outbox-protected handlers**.

## Why Choreography, not Orchestration

Centinela uses **choreographed** sagas (implicit coordinator via event flow), not a centralized orchestrator.

| Aspect | Choreography (chosen) | Orchestration (rejected) |
|---|---|---|
| Coordinator | Each handler knows what event triggers it; no central state | A central Saga Service owns the state machine |
| Failure points | Multiplied — each module must be tolerant of late or missing events | Centralized — one place to debug |
| Coupling | Loose: producers/consumers agree only on the event contract | Tight: orchestrator must know every step's interface |
| Complexity to deploy | Trivially added on top of the already existing event-driven layer | Need a new state-machine runtime — significant build / 3-week project risk |
| Visibility | Requires distributed tracing (ADR-007) and audit log per module | Centralized log |

For a **21-day project with 4 people**, choreography is the only viable option; orchestration would consume the budget.

## References

- `architecture/04-event-driven-communication.md` — event catalog
- `patterns/03-outbox-pattern.md` — per-handler reliability
- `best-practices/04-logging-and-monitoring.md` — distributed tracing & correlation IDs

## Status

**DRAFT** — implemented across all saga steps at the unit-test level first; integration tests in Sprint 2/3, per [#4](https://github.com/Team-Centinela/Centinela-Code/issues/4) Sprint 1, [#5](https://github.com/Team-Centinela/Centinela-Code/issues/5) Sprint 2, [#6](https://github.com/Team-Centinela/Centinela-Code/issues/6) Sprint 3.
