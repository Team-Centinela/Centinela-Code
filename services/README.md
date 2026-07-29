# services/

This directory holds the runtime services of the Centinela fraud detection platform.

Per ADR-001 (`../docs/decision-log/ADR-001-modular-monolith-hexagonal.md`), the default posture is a **Modular Monolith** with selective extraction. Services that have been pre-extracted (per ADR-001 §"Extracted services" and `../docs/architecture/05-selective-extraction.md`) live in their own subdirectories here.

| Service | Technology | Reason | What it owns |
|---|---|---|---|
| `ingestion/`         | Spring Boot (Java 21) on Azure Container Apps (Consumption, HTTP-scaled) | Independent scaling for burst traffic; failure isolation | Ingest endpoint, `oltp.transactions` writes, `transactions-raw` outbox |
| `serverless-engine/` | Spring Boot (Java 21) on Azure Container Apps (Consumption, KEDA `azure-servicebus` scaler) | Independent scaling for burst fraud-evaluation work; rule configs change on a different cadence than case management | Pipeline Pattern (FR-1..FR-4), `transactions.triggered_rules` writes, `FraudEvaluationCompleted` outbox |
| `core-backend/`      | Spring Boot (Java 21) on Azure Container Apps (Consumption, HTTP-scaled) | Modular monolith host for case-management workflow | `cases`, `alerts`, `reporting`, `auth`, `admin`, `app.transactions` query API |
| `ocr-worker/`        | FastAPI (Python 3.12) on Azure Container Apps (Consumption, KEDA `azure-servicebus` scaler) | Azure AI Document Intelligence SDK is Python-first | `documents` blob + metadata flow |
| `frontend/`          | Static SPA on Azure Static Web Apps | Analyst dashboard served from CDN | — |

See `../docs/architecture/01-overview.md` for the full system map and
`../docs/architecture/05-selective-extraction.md` for the extraction playbook.

> Per AGENTS.md §"Persistent vs Temporal", these service directories are intended for code only. Issues, status, and progress live as GitHub Issues — do not write markdown files inside service folders unless they are persistent references (e.g. internal-README, generated API docs).

## Per-Lane Ownership (per ADR-010)

| Service | Lane | Code owner | Azure parcel owner |
|---|---|---|---|
| `ingestion/` | B | @3105jero | @3105jero |
| `serverless-engine/` | C | @3105jero (domain) / @SebastianT2006 (plumbing) | @SebastianT2006 |
| `core-backend/` | D | @JjuanGarcia77 | @JjuanGarcia77 |
| `ocr-worker/` | (TBD Sprint 3, Lane E or successor) | @Santiagodxz | @Santiagodxz |
| `frontend/` | E (Sprint 3) | @Santiagodxz | @Santiagodxz |
| `shared-outbox/` *(Maven module)* | E | @Santiagodxz | (n/a) |
| `shared-observability/` *(Maven module)* | E | @Santiagodxz | (n/a) |
| `test-support/` *(Maven module)* | E | @Santiagodxz | (n/a) |

Sprint-1 per-lane workstream epics:

- **#135** — Lane B (Ingestion)
- **#136** — Lane C (Serverless Engine)
- **#138** — Lane D (Core Backend)
- **#137** — Lane E (Platform + Frontend)
- **#134** — Lane A (Governance)

See [`../docs/best-practices/05-pr-and-issue-discipline.md`](../docs/best-practices/05-pr-and-issue-discipline.md) for the canonical role & responsibility matrix.

## Open issues on this directory

- **#51** — *Register serverless-engine Maven module in `services/pom.xml`*. **Closed by PR #121** (2026-07-24). This entry is kept for cross-link purposes only. Maintenance: none.
- **#127** is the per-lane Epic #5-Day-A infrastructure bootstrap; **#139-#150** are the companion infra issues per ADR-010.
