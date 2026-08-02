---
title: Contributing to Centinela
type: overview
tags:
  - contributing
  - onboarding
  - sandbox
created: 2026-08-02
---

# Contributing to Centinela

Centinela is a real-time transactional fraud detection platform built by a 5-person team on a $60 / 21-day budget using AI-driven development. This document is the entry point for new collaborators — human or AI. It does not duplicate architectural decisions; those live in the ADRs and architecture pages under `docs/`. Read this file first, then follow the links.

## Pick your role

| You are… | Start here |
|---|---|
| A human joining the project | [`docs/ASSIGNMENT.md`](docs/ASSIGNMENT.md) (the fixed constraints) → [`docs/architecture/01-overview.md`](docs/architecture/01-overview.md) (system-level narrative) |
| An AI agent | [`AGENTS.md`](AGENTS.md) (root) → [`.opencode/CONTEXT-MAP.md`](.opencode/CONTEXT-MAP.md) → [`.github/agent-preflight.md`](.github/agent-preflight.md) (three-rule preflight) |
| A reviewer or governance lead | [`docs/best-practices/05-pr-and-issue-discipline.md`](docs/best-practices/05-pr-and-issue-discipline.md) (lane matrix + companion convention) + [`docs/decision-log/ADR-010-issue-pr-discipline.md`](docs/decision-log/ADR-010-issue-pr-discipline.md) |

## Toolchain prerequisites

The version table is canonical in [`docs/architecture/06-technology-stack.md`](docs/architecture/06-technology-stack.md) §Version Requirements. Minimum set:

- **Java 21 (LTS)** — Spring Boot 3.4.x
- **Python 3.12** — FastAPI + `azure-servicebus` 7.x
- **Node.js 22 (LTS)** — React + Vite
- **Terraform 1.9+** — AzureRM provider
- **Docker 27+** — required for the local emulator stack (next section)
- **GitHub CLI (`gh`)** — required for issue and PR workflows

## Local sandbox

The local sandbox is the **canonical pre-validation surface** per [`docs/decision-log/ADR-011-local-emulator-stack.md`](docs/decision-log/ADR-011-local-emulator-stack.md) §11.1–11.4. Real Azure is the deploy target of merged code, not a test surface. The full container list, image digests, and profile conventions are pinned in ADR-011 §11.2.

### Bring-up

```sh
# from the repository root
docker compose up -d --wait
```

This starts seven containers on the `centinela` Docker bridge network:

- `centinela-postgres` (PostGIS 16) — replaces Azure DB for PostgreSQL Flexible Server
- `centinela-servicebus` (Microsoft SB Emulator) — replaces Azure Service Bus Standard
- `centinela-sqledge` (SQL Server 2022) — internal state store for the SB Emulator only
- `centinela-floci-az` — replaces Azure Key Vault + Blob Storage + App Insights
- `centinela-ingestion`, `centinela-core-backend`, `centinela-serverless-engine` — the three Spring Boot services on the `local-emulator` profile

### Verify gate

[`scripts/verify-emulators.{ps1,sh}`](scripts/verify-emulators.ps1) is the canonical "is the stack still green" check. It reports PASS/FAIL counts across compose-up, Postgres, SB Emulator, Floci-AZ, SQL Edge, Spring Boot actuator, and Flyway history.

```sh
./scripts/verify-emulators.sh   # or verify-emulators.ps1 on Windows
```

A green run is the **prerequisite for any code change** touching the emulator surface (per ADR-010 §10.9 + ADR-011 §11.6). The expected outcome is `18 PASS / 0 FAIL`; treat any other result as a regression.

### Spring profile activation

The `local-emulator` profile binds the three Spring Boot services to the compose network. Activate it for local `mvn spring-boot:run` or in IDEs:

```sh
mvn spring-boot:run -Dspring-boot.run.profiles=local-emulator
```

The default `application.yml` is production-only and fails fast on missing `AZURE_SERVICEBUS_CONNECTION_STRING` / `APPLICATIONINSIGHTS_CONNECTION_STRING`. Do not load the `local-emulator` profile in production — ADR-011 §11.4 records the contract.

## What is off-limits in the sandbox

- The Service Bus Emulator SAS key in `docker/servicebus/Config.json` is dev-only. Real Azure uses Key Vault references per [`docs/decision-log/ADR-006-security-auth.md`](docs/decision-log/ADR-006-security-auth.md) §6.3.
- PostgreSQL credentials in `docker-compose.yml` are `postgres / postgres`. Real Azure uses Azure AD auth via ACA managed identity per [`docs/decision-log/ADR-002-postgresql-only-db.md`](docs/decision-log/ADR-002-postgresql-only-db.md).
- A planned `[E.48]` lint rule (issue #108) gates the `local-emulator` profile out of production image builds. Do not bypass it.

## AI-assisted Azure changes

The tools below are required for any agent or human session that touches the real-Azure surface (Terraform `*.tf`, Key Vault refs, Service Bus binding, KEDA scaler, MI role assignments, container build context). Per [`infrastructure/RUNBOOK-FIRST-APPLY.md`](infrastructure/RUNBOOK-FIRST-APPLY.md) §Step C and [`docs/decision-log/ADR-011-local-emulator-stack.md`](docs/decision-log/ADR-011-local-emulator-stack.md) §11.6 layer 1, this is the closed set the project enforces — a missing tool is a claim-time blocker per [`.github/agent-preflight.md`](.github/agent-preflight.md) Rule 2.

| Tool | Required for | Notes |
|---|---|---|
| `terraform` (1.9+) | `fmt`, `init`, `validate`, `plan` | Per [`docs/architecture/06-technology-stack.md`](docs/architecture/06-technology-stack.md) §Version Requirements |
| `az` CLI | Live Azure queries, RBAC checks, resource-provider registration | Authenticated against the working subscription before any `apply` |
| `gh` CLI | Issue and PR workflows, comment posting | Required by [`docs/best-practices/05-pr-and-issue-discipline.md`](docs/best-practices/05-pr-and-issue-discipline.md) |
| `checkov` | IaC static analysis (the `matrices-build` gate) | RUNBOOK §Step C + ADR-011 §11.6 layer 1 |
| `tflint` | Terraform lint (the `matrices-build` gate) | RUNBOOK §Step C |
| `tfsec` | Optional IaC scanner (alternative to `checkov`) | Not explicitly required by the project; only if `checkov` is unavailable |

Verify the toolchain before claiming any `Has azure-impact: yes` task:

```sh
terraform version   # >= 1.9
az version
gh --version
checkov --version
tflint --version
```

## First real-Azure apply

The sandbox is the gate, not the destination. When work is ready to ship to real Azure:

1. Cross-lane E2E green locally (`./scripts/verify-emulators.sh` reports all PASS).
2. ADR-010 merged on `main` — see the [hard prerequisite gate in ADR-011 §11.7](docs/decision-log/ADR-011-local-emulator-stack.md#117-adr-010-hard-prerequisite-gate).
3. Read [`infrastructure/RUNBOOK-FIRST-APPLY.md`](infrastructure/RUNBOOK-FIRST-APPLY.md) for the apply runbook, and the companion infra issue template at [`.github/ISSUE_TEMPLATE/infra-change.md`](.github/ISSUE_TEMPLATE/infra-change.md) for `EXPECTED DELIVERY` + `COST-ATTRIBUTION` blocks.

Do not run `terraform apply` without an open companion infra issue — ADR-010 §10.4 requires the close-out template to exist before any real-Azure state mutation.

## Cost ceiling

The $60 / 21-day ceiling is the cost-care promise. Every Azure-impacting change carries a paired `infra`-labelled companion issue with a `COST-ATTRIBUTION` block in one of three modes (a, b, or c per ADR-010 §10.5). The doc-row ledger lives in [`infrastructure/README.md`](infrastructure/README.md) §"Cost guardrails". If a change burns more than the lane's pro-rata slice, surface it on the companion issue before applying.

## Issue and PR workflow

All work is issue-tracked. The templates in [`.github/ISSUE_TEMPLATE/`](.github/ISSUE_TEMPLATE/) enforce the discipline:

| Template | Use for |
|---|---|
| `adr.md` | New architecture decision (creates `docs/decision-log/ADR-NNN-…`) |
| `adr-amend.md` | Drift on an existing ADR |
| `task-managed.md` | Lane work — carries `Blocked by:`, `Has azure-impact:`, `Companion infra issue:` |
| `infra-change.md` | Companion infra issues for `azure-impact: yes` tasks |
| `bug.md` | Defect reports |
| `ceremony.md` | Standup / retro / refinement notes |
| `sprint-task.md` | Deprecated — use `task-managed.md` |

The normative contract is [`docs/decision-log/ADR-010-issue-pr-discipline.md`](docs/decision-log/ADR-010-issue-pr-discipline.md) + [`docs/best-practices/05-pr-and-issue-discipline.md`](docs/best-practices/05-pr-and-issue-discipline.md). The AI agent's preflight is [`.github/agent-preflight.md`](.github/agent-preflight.md) (three rules: blockers, azure-impact, re-read source-of-truth docs).

## Adding a service or module

1. Read [`docs/architecture/05-selective-extraction.md`](docs/architecture/05-selective-extraction.md) — extraction is the default-deny path.
2. Per ADR-001 §"Extracted services" and the per-service README imprint rule, every extracted service needs a `services/<name>/README.md` before its ADR can close.
3. Use the existing `services/ingestion/`, `services/serverless-engine/`, `services/core-backend/`, `services/ocr-worker/`, `services/frontend/` as templates.

## Recording a decision

File an issue via [`.github/ISSUE_TEMPLATE/adr.md`](.github/ISSUE_TEMPLATE/adr.md). The ADR is drafted in `docs/decision-log/ADR-NNN-<name>.md` and clusters with the issue in the same release cycle. ADR-005 §"Information routing" defines what is persistent (here) vs. temporal (GitHub Issues).

## References

- [`README.md`](README.md) — repo overview and ADR index
- [`AGENTS.md`](AGENTS.md) — global behavior rules
- [`docs/AGENTS.md`](docs/AGENTS.md) — `docs/`-tree rules
- [`docs/architecture/01-overview.md`](docs/architecture/01-overview.md) — system narrative
- [`docs/architecture/06-technology-stack.md`](docs/architecture/06-technology-stack.md) — version table
- [`docs/decision-log/ADR-011-local-emulator-stack.md`](docs/decision-log/ADR-011-local-emulator-stack.md) — sandbox spec
- [`docs/decision-log/ADR-010-issue-pr-discipline.md`](docs/decision-log/ADR-010-issue-pr-discipline.md) — issue/PR contract
- [`docs/best-practices/05-pr-and-issue-discipline.md`](docs/best-practices/05-pr-and-issue-discipline.md) — lane matrix
- [`infrastructure/README.md`](infrastructure/README.md) — cost guardrails + companion convention
- [`infrastructure/RUNBOOK-FIRST-APPLY.md`](infrastructure/RUNBOOK-FIRST-APPLY.md) — first apply runbook
- [`.github/agent-preflight.md`](.github/agent-preflight.md) — AI preflight
