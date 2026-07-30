---
title: Centinela — Context Map
type: reference
description: Single-page navigation index for AI agents. Loaded with every session per opencode.json `instructions`.
tags:
  - ai-agent
  - context
  - bootstrap
created: 2026-07-15
---
# Centinela Context Map

A shortcut index that points AI agents to the right doc quickly.
This file is small on purpose — it is mixed into every session via the `opencode.json` `instructions` list, so bloat hurts every session.

> **Note on what opencode.json loads (ADR-012 §12.1, historical context for #195 review item 2):** as of 2026-07-29, `opencode.json:instructions` enumerates every file the agent reads on session start — every ADR under `docs/decision-log/`, `.github/agent-preflight.md`, AGENTS.md, CONTEXT-MAP, and the two architecture docs. The previous posture (5 files by exact path) was misleading and is closed by ADR-012 §12.1's explicit-enumeration form.
> A new ADR added without updating `instructions` fails the CI gate described in §12.1. **AI agents MUST read each ADR/preflight by explicit `Read` tool call before claiming work**, per ADR-010 §10.6 + `/.github/agent-preflight.md` Rule 3, even when the file appears in `instructions` (the loader's effective context ≠ a verified read).

## Working agreement

- **Repo:** Team-Centinela/Centinela-Code
- **Default branch under development:** `developer`
- **Released branch:** `main`
- **ON-call:** `gh issue list --label task --state open`
- **Current sprint:** see GitHub Issues with milestone label
  (e.g. `sprint-1`).
- **Budget:** $60 / delivery date July 31st, 2026. See `infrastructure/README.md`.

## Read first on session start

> **Loader caveat:** OpenCode does not auto-load these. Each ADR + preflight must be read explicitly. The numbered list below is the recommended `Read` order.

1. `../AGENTS.md` (root) — global behavior rules (now includes ADR-010 `Always` clauses)
2. `../docs/AGENTS.md` — `docs/` rules (now includes PR↔Issue↔Azure traceability)
3. `../docs/architecture/01-overview.md` — what is this thing (now includes Lane-Ownership Overlay)
4. `../docs/architecture/06-technology-stack.md` — what runs it
5. **All** ADRs in `../docs/decision-log/` — each is loaded via `opencode.json:instructions` (explicit enumeration per ADR-012 §12.1). Read order: ADR-001 → ADR-002 → ADR-003 → ADR-004 → ADR-005 → ADR-006 → ADR-007 → ADR-009 → ADR-010 → ADR-011 → ADR-012. Skip ADR-008 only — that number is reserved and the file does not exist. **Skipping an existing ADR is forbidden** — it is the failure mode ADR-012 §12.1 was designed to prevent.
6. `../docs/best-practices/05-pr-and-issue-discipline.md` — role matrix + companion infra convention
7. `../.github/agent-preflight.md` — three-rule AI preflight before any work

## Topic → Doc routing

| Topic / Question | Doc |
|---|---|
| Module boundaries / cross-module deps | `../docs/architecture/02-modular-monolith.md` |
| Touching a domain model | `../docs/architecture/03-hexagonal-architecture.md` |
| Adding a domain event | `../docs/architecture/04-event-driven-communication.md` + `../docs/patterns/03-outbox-pattern.md` |
| Why is a service extracted? | `../docs/architecture/05-selective-extraction.md` + `services/<name>/README.md` (Serverless Engine = third extracted service). The Serverless Engine's Maven `<module>` registration in `services/pom.xml` is tracked at [#51](https://github.com/Team-Centinela/Centinela-Code/issues/51). |
| ADR lifecycle / how to add a new one | `../docs/AGENTS.md` + `../.github/ISSUE_TEMPLATE/adr.md` |
| Adding a new rule (FR-?) | `../docs/decision-log/ADR-004-rule-engine-pipeline-explainer.md` + `../docs/patterns/04-pipeline-pattern.md` (engine now lives in `services/serverless-engine/`) |
| Where do documents live? | `../docs/decision-log/ADR-002-postgresql-only-db.md` + issue `#18` |
| Why no Cosmos DB? | `../docs/decision-log/ADR-002-postgresql-only-db.md` |
| Why Standard tier Service Bus? | `../docs/decision-log/ADR-003-async-messaging-reliability.md` |
| Why Azure Container Apps (not Functions / AKS)? | `../docs/decision-log/ADR-009-compute-substrate-container-apps-static-web-apps.md` (primary) + `../docs/architecture/06-technology-stack.md` §Why Each Technology + `../docs/decision-log/ADR-001-modular-monolith-hexagonal.md` §Alternatives |
| Saga across modules / services | `../docs/patterns/05-saga-pattern.md` |
| Idempotency on a consumer | `../docs/patterns/06-idempotency-key.md` |
| Tests | `../docs/best-practices/02-testing-strategy.md` |
| Errors | `../docs/best-practices/03-error-handling.md` |
| Logging/observability | `../docs/best-practices/04-logging-and-monitoring.md` |
| What PR / issue discipline do I follow? | `../docs/best-practices/05-pr-and-issue-discipline.md` + `../docs/patterns/07-azure-impact-companion-issue.md` + `../docs/decision-log/ADR-010-issue-pr-discipline.md`. AI agents read `../.github/agent-preflight.md` first. |
| Phase 0 / emulator-surface discipline | `../docs/decision-log/ADR-010-issue-pr-discipline.md` §10.9 + `../docs/decision-log/ADR-011-local-emulator-stack.md` §11.6 + `../docs/patterns/07-azure-impact-companion-issue.md` §"Phase 0 Close". Use Mode (c) — Emulator Commitment in `infra-change.md`. |

## Issue ↔ Doc linkage (mandatory)

Per the issue templates (`.github/ISSUE_TEMPLATE/`):
- **ADR / Epic issue** → must list at least one ADR.
- **Task-managed** (lane work) → must list `Blocked by:` (closed-only), `Has azure-impact: yes/no`, surface selection (§10.9.1), and `Companion infra issue:` when azure-impact is yes. **Always** link to the lane's epic.
- **Infra-change** (companion) → `EXPECTED DELIVERY` + `COST-ATTRIBUTION` (real-Azure: Mode (a)+(b); emulator: Mode (c) — Emulator Commitment).
- **Ceremony** → `standup` / `retro` / `refinement` body filled only with comments.
- **ADR-amend** → drift diff recorded, ADR being amended clearly stated.
- **Bug** → must list the affected module/section.

When you read an issue to start work, harvest these doc references *before* your first edit.

## Per-lane workstream epics (Sprint 1)

| Lane | Issue | Owner |
|---|---|---|
| A (governance) | #134 | @SrLampi1001 |
| B (Ingestion)  | #135 | @3105jero |
| C (Engine)     | #136 | @SebastianT2006 (+ domain by @3105jero) |
| D (Core)       | #138 | @JjuanGarcia77 |
| E (Platform)   | #137 | @Santiagodxz |

Companion infra issues per ADR-010 §10.3 (each is a paired `[X.A*]` issue in the same sprint):

- Lane B: #139 (B.A1), #140 (B.A2), #141 (B.A3)
- Lane C: #142 (C.A1), #143 (C.A2), #144 (C.A3)
- Lane D: #145 (D.A1), #146 (D.A2), #147 (D.A3)
- Lane E: #148 (E.A1), #149 (E.A2), #150 (E.A3)

## Quick GitHub CLI cheatsheet

```sh
# Today's open work
gh issue list --state open --label task

# Active ADR drafts pending approval
gh issue list --state open --label adr

# What sprint is this in?
gh issue list --state all <--milestone | --label sprint-1>

# Cross-link
gh issue comment <num> --body "See ADR-002 and #11"
```
