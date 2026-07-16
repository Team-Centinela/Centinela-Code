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

1. `AGENTS.md` (root) — global behavior rules
2. `docs/AGENTS.md` — `docs/` rules
3. `docs/architecture/01-overview.md` — what is this thing
4. `docs/architecture/06-technology-stack.md` — what runs it
5. **All** ADRs in `docs/decision-log/`

## Topic → Doc routing

| Topic / Question | Doc |
|---|---|
| Module boundaries / cross-module deps | `architecture/02-modular-monolith.md` |
| Touching a domain model | `architecture/03-hexagonal-architecture.md` |
| Adding a domain event | `architecture/04-event-driven-communication.md` + `patterns/03-outbox-pattern.md` |
| Why is a service extracted? | `architecture/05-selective-extraction.md` + `services/<name>/README.md` |
| ADR lifecycle / how to add a new one | `docs/AGENTS.md` + `.github/ISSUE_TEMPLATE/adr.md` |
| Adding a new rule (FR-?) | `decision-log/ADR-004-rule-engine-pipeline-explainer.md` + `patterns/04-pipeline-pattern.md` |
| Where do documents live? | `decision-log/ADR-002-postgresql-only-db.md` + issue `#18` |
| Why no Cosmos DB? | `decision-log/ADR-002-postgresql-only-db.md` |
| Why Standard tier Service Bus? | `decision-log/ADR-003-async-messaging-reliability.md` |
| Saga across modules | `patterns/05-saga-pattern.md` |
| Idempotency on a consumer | `patterns/06-idempotency-key.md` |
| Tests | `best-practices/02-testing-strategy.md` |
| Errors | `best-practices/03-error-handling.md` |
| Logging/observability | `best-practices/04-logging-and-monitoring.md` |

## Issue ↔ Doc linkage (mandatory)

Per the issue templates (`.github/ISSUE_TEMPLATE/`):
- **ADR / Epic issue** → must list at least one ADR.
- **Sprint task** → must list at least one ADR or
  architecture/pattern doc.
- **Bug** → must list the affected module/section.

When you read an issue to start work, harvest these doc references
*before* your first edit.

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
