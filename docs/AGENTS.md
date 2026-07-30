---
title: docs/ — Agent Behavior Rules
type: reference
tags:
  - copy
  - docs-rules
created: 2026-07-15
---
# `docs/` — Documentation-tree Agent Rules

This file extends the root `AGENTS.md` with rules that apply specifically to work inside the `docs/` tree. The root `AGENTS.md` is the source of truth for global behavior; this file is a tighter harness for documentation work.

## What `docs/` is for

`docs/` holds **persistent** architectural context for the Centinela project. Files in here are expected to stay correct for the entire project lifecycle (and ideally beyond).
Anything that expires in 6 months does **not** belong here.

The four canonical subdirectories:

| Subdirectory | Holds |
|---|---|
| `docs/architecture/` | System-level narrative: overview, modular monolith, hexagonal layering, event-driven communication, selective extraction, technology stack |
| `docs/patterns/`      | Reusable design patterns that recur across modules |
| `docs/best-practices/` | Code-level conventions: organization, testing, errors, logging |
| `docs/decision-log/`  | ADRs that enumerate disposed alternatives and the chosen path |

`ASSIGNMENT.md` is the only file inside `docs/` that is *fixed upstream*
(it does not change without the assignment being changed).

## What may NOT live in `docs/`

- Sprint status, progress, blockers → GitHub Issues
- Reasoning for an active PR → that lives in the PR description
- A "TODO" or "in progress" note about work — open an Issue instead
- Duplicated content from a living doc — link by `#N`, do not paste

If a user instructs the AI to write a `docs/<name>.md` file, verify that the content is genuinely persistent. If the answer is *no*, open an issue with the rationale and stop.

## Refactor and authoring rules

- Files in `docs/architecture/` should agree on terminology and acronyms. If a file uses a deprecated term, fix the file, do not create a new one.
- Cross-references between docs use relative paths (`architecture/01-overview.md`), not absolute URLs.
- Markdown style: ATX headings, fenced code blocks with language hints, tables for comparing options.
- Updating any file in `docs/`, maintain a single source of truth. 
- The output MUST represent only the current, definitive state. Reference links to past iterations, superseded versions, PR-by-PR changes, session self-corrections, audit events, or "previously…" / "now…" narrative prose MUST be stripped from the document body.
- Document text MUST use present tense for current state. Past decisions that a future reader needs to interpret the current one MAY appear in (d) ADR `## Context` (background, not chronology), (e) ADR `## Status` (one-line identity: state + date + tracking issue), (f) ADR `## References` (cite only — link, do not narrate), (g) the `-draft` / `-superseded` filename suffix per "Disposition of superseded content" below. ADRs MUST NOT grow append-on-event audit tables.

## Disposition of superseded content

If an ADR supersedes content that was previously held in the repository, the older content moves to `docs/decision-log/` with its `-draft` or `-superseded` suffix, **not deleted.** A closed Issue is the signal that an old ADR is no longer live, but the file remains for traceability.

## Coordination with code

If a code change in `services/<name>/` conflicts with text in `docs/`, the **text wins by default** until the doc is updated. To update the doc safely, open an Issue linking the code PR and the relevant ADR, then update the doc in the same release cycle when practical.

Active Issues in the Centinela repo should reference the affected `docs/` files (e.g. *"ADR-002 supersedes content under `architecture/06-technology-stack.md` §Cosmos DB"*). The Issue Templates in `.github/ISSUE_TEMPLATE/` make ADR linkage mandatory.

## PR↔Issue ↔ Azure traceability (per ADR-010)

When a code PR alters the deployment surface — env var, secret, secret-name ref, container build context, Service Bus binding, KEDA config, ACA Managed Identity role assignment — the PR **must**:

1. Carry the `azure-impact: yes` label and a `Companion infra issue: #—` line referencing a paired `infra`-labelled companion issue.
2. Mirror the `EXPECTED DELIVERY` block from `task-managed.md` into both the issue and the companion.
3. Pick a `centinela:*` cost-attribution mode (doc-row or Azure tags); the companion issue records the cost.

PRs that change code without an accompanying doc touch on the affected ADR / pattern / service README are an *audit-trail break* — see [ADR-010 §10.4](../decision-log/ADR-010-issue-pr-discipline.md) and [`docs/best-practices/05-pr-and-issue-discipline.md`](../best-practices/05-pr-and-issue-discipline.md).

## Cross-doc PRs

If a PR touches more than one of {ADRs, architecture pages, service READMEs, AGENTS.md}, the body must enumerate every affected file and the reason — so a reviewer can read the textual consequence in one PR view instead of digging through commits. Single concern per commit (Commit Hygiene §1) plus cross-doc disclosure is the separation; both are required.

When a new ADR is added or an existing ADR is amended, the cross-doc reconciliation entry must surface every `AGENTS.md` + `docs/AGENTS.md` + `CONTEXT-MAP.md` + `agent-preflight.md` + `best-practices/05-pr-and-issue-discipline.md` reference affected. See ADR-012 §12.1 for the instruction-loader contract that pins the AGENTS ↔ ADR cross-reference surface.