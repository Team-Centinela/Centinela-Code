---
title: docs/ — Agent Behavior Rules
type: reference
tags:
  - copy
  - docs-rules
created: 2026-07-15
---
# `docs/` — Documentation-tree Agent Rules

This file extends the root `AGENTS.md` with rules that apply specifically
to work inside the `docs/` tree. The root `AGENTS.md` is the source of
truth for global behavior; this file is a tighter harness for
documentation work.

## What `docs/` is for

`docs/` holds **persistent** architectural context for the Centinela
project. Files in here are expected to stay correct for the entire
project lifecycle (and ideally beyond). Anything that expires in 6
months does **not** belong here.

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

If a user instructs the AI to write a `docs/<name>.md` file, verify
that the content is genuinely persistent. If the answer is *no*, open
an issue with the rationale and stop.

## Refactor rules

- Files in `docs/architecture/` should agree on terminology and
  acronyms. If a file uses a deprecated term, fix the file, do not
  create a new one.
- Cross-references between docs use relative paths
  (`architecture/01-overview.md`), not absolute URLs.
- Markdown style: ATX headings, fenced code blocks with language
  hints, tables for comparing options.

## Disposition of superseded content

If an ADR supersedes content that was previously held in the
repository, the older content moves to `docs/decision-log/` with its
`-draft` or `-superseded` suffix, **not deleted.** A closed Issue is
the signal that an old ADR is no longer live, but the file remains
for traceability.

## Coordination with code

If a code change in `services/<name>/` conflicts with text in
`docs/`, the **text wins by default** until the doc is updated. To
update the doc safely, open an Issue linking the code PR and the
relevant ADR, then update the doc in the same release cycle when
practical.

Active Issues in the Centinela repo should reference the affected
`docs/` files (e.g. *"ADR-002 supersedes content under
`architecture/06-technology-stack.md` §Cosmos DB"*). The Issue
Templates in `.github/ISSUE_TEMPLATE/` make ADR linkage mandatory.
