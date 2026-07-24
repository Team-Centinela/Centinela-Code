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
This file is small on purpose — it is mixed into every session via
the `opencode.json` `instructions` list, so bloat hurts every
session.

## Working agreement

- **Repo:** Team-Centinela/Centinela-Code
- **Default branch under development:** `developer`
- **Released branch:** `main`
- **ON-call:** `gh issue list --label task --state open`
- **Current sprint:** see GitHub Issues with milestone label
  (e.g. `sprint-1`).
- **Budget:** $60 / 21 days. See `infrastructure/README.md`.

## Read first on session start

1. `../AGENTS.md` (root) — global behavior rules (now includes ADR-010 `Always` clauses)
2. `../docs/AGENTS.md` — `docs/` rules (now includes PR↔Issue↔Azure traceability)
3. `../docs/architecture/01-overview.md` — what is this thing (now includes Lane-Ownership Overlay)
4. `../docs/architecture/06-technology-stack.md` — what runs it
5. **All** ADRs in `../docs/decision-log/` (now including `ADR-010 — Issue & PR Discipline`)
6. `../docs/best-practices/05-pr-and-issue-discipline.md` — role matrix + companion infra convention
7. `../../.github/agent-preflight.md` — three-rule AI preflight before any work

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
| What PR / issue discipline do I follow? | `../docs/best-practices/05-pr-and-issue-discipline.md` + `../docs/patterns/07-azure-impact-companion-issue.md` + `../docs/decision-log/ADR-010-issue-pr-discipline.md`. AI agents read `../../.github/agent-preflight.md` first. |

## Issue ↔ Doc linkage (mandatory)

Per the issue templates (`.github/ISSUE_TEMPLATE/`):
- **ADR / Epic issue** → must list at least one ADR.
- **Task-managed** (lane work) → must list `Blocked by:` (closed-only), `Has azure-impact: yes/no`, and `Companion infra issue:` when azure-impact is yes. **Always** link to the lane's epic.
- **Infra-change** (companion) → `EXPECTED DELIVERY` + `COST-ATTRIBUTION`.
- **Ceremony** → `standup` / `retro` / `refinement` body filled only with comments.
- **ADR-amend** → drift diff recorded, ADR being amended clearly stated.
- **Bug** → must list the affected module/section.

When you read an issue to start work, harvest these doc references
*before* your first edit.

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
