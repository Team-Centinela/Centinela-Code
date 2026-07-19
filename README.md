---
title: Centinela — Real-time Fraud Detection Platform
type: overview
tags:
  - overview
  - onboarding
created: 2026-07-15
---

# Centinela

Real-time transactional fraud detection platform for a small / medium
fintech, built within a **$60 / 21-day** budget by a **4-person** team
using AI-driven development.

This is a **monorepo**: code, infrastructure, and persistent
architectural documents live together so AI agents and humans can move
between them without context fragmentation.

## Project at a glance

| | |
|---|---|
| **Money budget**       | $60 over 21 days |
| **Team**               | 4 people (full-stack, AI-assisted) |
| **Delivery model**     | Modular monolith + selective extraction (ADR-001) |
| **Storage**            | Azure PostgreSQL Flexible Server (B1ms, schema-per-module) (ADR-002) |
| **Async backbone**     | Azure Service Bus Standard tier (Topics required for `case-events`) (ADR-003) |
| **Rule Engine**        | Pipeline Pattern w/ deterministic NL Explainer (ADR-004), packaged as the *Serverless Engine* — a third extracted service on Azure Container Apps Consumption |
| **Repo posture**       | Single monorepo (ADR-005) |
| **Deployment model**   | All four backends on Azure Container Apps Consumption + Static Web Apps for the frontend |

## Repository Navigation

```
Centinela-Code/
├── README.md              ← you are here
├── AGENTS.md              ← AI agent behavior rules (every session loads this)
├── opencode.json          ← AI tooling config
├── docs/                  ← persistent architectural context
│   ├── AGENTS.md          ← rules specific to the docs/ tree
│   ├── architecture/      ← 6 narrative files
│   ├── patterns/          ← 6 design patterns
│   ├── best-practices/    ← 4 convention files
│   ├── decision-log/      ← ADRs (Architecture Decision Records)
│   └── ASSIGNMENT.md      ← project-fixed constraints
├── services/              ← runtime services (4 + frontend)
│   ├── ingestion/         ← Spring Boot, ACA Consumption, HTTP-scaled
│   ├── serverless-engine/ ← Spring Boot, ACA Consumption, KEDA azure-servicebus on transactions-raw
│   ├── core-backend/      ← Spring Boot modular monolith, ACA Consumption, HTTP-scaled
│   ├── ocr-worker/        ← FastAPI, ACA Consumption, KEDA azure-servicebus on documents-pending
│   └── frontend/          ← React + Vite SPA, Azure Static Web Apps
├── infrastructure/        ← Terraform IaC
├── .github/               ← CI workflows + Issue templates
└── .opencode/             ← context map + project-specific AI skills
```

### Where to start

- **You are a human joining the project** → read `docs/ASSIGNMENT.md`
  then `docs/architecture/01-overview.md`.
- **You are an AI agent** → read `AGENTS.md` first (it is referenced
  by `opencode.json` `instructions:`). Then `.opencode/CONTEXT-MAP.md`
  to find the right doc for the work at hand.
- **You want to add a feature** → find or open a GitHub Issue
  (`gh issue list`); the issue templates in `.github/ISSUE_TEMPLATE/`
  guarantee that each issue points to its driving ADR/doc.
- **You want to record a decision** → use the **ADR draft** template
  and create `docs/decision-log/ADR-NNN-<name>.md` linked from the
  issue body.

## Persisted decisions (ADRs)

| ADR | Title | Status |
|---|---|---|
| [ADR-001](docs/decision-log/ADR-001-modular-monolith-hexagonal.md) | Modular Monolith + Hexagonal (incl. three extracted services) | ACCEPTED (Sprint 0) |
| [ADR-002](docs/decision-log/ADR-002-postgresql-only-db.md) | Unified PostgreSQL Persistence | APPROVED (Sprint 0) |
| [ADR-003](docs/decision-log/ADR-003-async-messaging-reliability.md) | Async Messaging & Reliability | ACCEPTED (Sprint 0) |
| [ADR-004](docs/decision-log/ADR-004-rule-engine-pipeline-explainer.md) | Rule Engine — Pipeline Pattern (Serverless Engine) | ACCEPTED (Sprint 0) |
| [ADR-005](docs/decision-log/ADR-005-monorepo-unification.md) | Monorepo Unification | EXECUTED (2026-07-15) |
| [ADR-009](docs/decision-log/ADR-009-compute-substrate-container-apps-static-web-apps.md) | Compute Substrate — ACA Consumption + SWA Free | DRAFT (pending Sprint 0 review) |

> ADR-006 (Security & Auth), ADR-007 (Observability/Cost), and ADR-008
> (Config & Secrets) are tracked as issues and will land after Sprint 0.

## What is *not* in this repo

- ❌ Job control code (TargetProcess / Linear)
- ❌ Customer data — no data is committed, ever
- ❌ Any binary that could fit in `git lfs` (large models, media)
- ❌ The historical `Centinela-docs` repo (archived
  [here](https://github.com/Team-Centinela/Centinela-docs))

## Quick links

- Issues: <https://github.com/Team-Centinela/Centinela-Code/issues>
- Issue templates enforcing ADR linkage:
  `.github/ISSUE_TEMPLATE/adr.md`, `sprint-task.md`, `bug.md`
- CI: `.github/workflows/ci.yml`
- AI tooling: `opencode.json`

---

> **Repository courtesy:** All architecture state is owned by an
> Agent in this monorepo. The historical `Centinela-docs` repository is
> archived (read-only) and contains only the 6 closed issues that
> pre-date the unification.
