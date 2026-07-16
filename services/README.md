# services/

This directory holds the runtime services of the Centinela fraud detection platform.

Per ADR-001 (`docs/decision-log/ADR-001-modular-monolith-hexagonal.md`), the default posture is a **Modular Monolith** with selective extraction. Services that have been pre-extracted (per ADR-001 §"Extracted services") live in their own subdirectories here.

| Service | Technology | Reason | Module it backs |
|---|---|---|---|
| `ingestion/`   | Spring Boot (Java 21)   | Independent scaling for burst traffic | `app.transactions:api` + outbox |
| `core-backend/`| Spring Boot (Java 21)   | Modulith host; default unless extracted | `app.cases`, `app.alerts`, `app.scoring`, `app.rules`, `app.reporting`, `app.auth`, `app.admin` |
| `ocr-worker/`  | FastAPI (Python 3.12)   | Azure AI Document Intelligence SDK is Python-first | `app.documents:ocr` |
| `frontend/`    | Static SPA (TBD)        | Analyst dashboard served from CDN | — |

See `docs/architecture/01-overview.md` for the full module map and
`docs/architecture/05-selective-extraction.md` for the extraction playbook.

> Per AGENTS.md §"Persistent vs Temporal", these service directories are intended for code only. Issues, status, and progress live as GitHub Issues — do not write markdown files inside service folders unless they are persistent references (e.g. internal-README, generated API docs).
