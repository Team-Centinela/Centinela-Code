# ADR-005: Monorepo Unification

## Context

The project originally carried its architectural documentation in a separate repository (`Team-Centinela/Centinela-docs`) and its code in another (`Team-Centinela/Centinela-Code`). The split was the path of least resistance at the time, but for a 21-day project (Due July 31, 2026) executed by AI agents on a 5-person team, it fragmented context:

| Problem | Cause |
|---|---|
| AI agents working in `Centinela-Code` could not see `architecture/`, `patterns/`, `best-practices/`, `decision-log/` without opening another repo | Cross-repo context is not loaded by IDE/Copilot |
| Historical ADRs ([`Centinela-docs` #1 to #28](https://github.com/Team-Centinela/Centinela-docs/issues?q=is%3Aissue)) and local architecture markdown drifted out of sync | Two locations for related content |
| Reviewers traversed docs-only PRs that lived in the code repo without surrounding context | Slow PR review |

`Team-Centinela/Centinela-docs` is **archived** (since 2026-07-15) and retained for lineage only — historical issue URLs (`docs#3`, `docs#7`, `docs#17`, etc.) are preserved so past references resolve.

## Decision

**Unify `Centinela-docs` and `Centinela-Code` into a single monorepo: `Team-Centinela/Centinela-Code`.**

```
centinela-code/
├── docs/                   # architecture/, patterns/, best-practices/, decision-log/, AGENTS.md, ASSIGNMENT.md
├── services/               # The 4 services (ingestion, core, ocr, frontend)
├── infrastructure/         # Terraform IaC
├── .github/                # Workflows, issue templates
└── README.md
```

The monorepo's value is keeping persistent architectural decisions next to the code they describe. §Information routing (below) defines how each kind of content is placed so the unification does not re-fragment via hidden `.md` files.

## Information routing (post-unification)

A monorepo that co-locates code and docs only pays off if non-durable content does **not** leak into `.md` files. Each information type has one canonical home:

| Information type | Canonical home | Lifetime |
|---|---|---|
| Architectural decisions, patterns, reference docs | `docs/architecture/`, `docs/patterns/`, `docs/best-practices/`, `docs/decision-log/` | Persistent (project lifetime) |
| Sprint tasks, blockers, progress | GitHub Issues with Type `task`,`bug`,`feature` and/or `epic` label | Temporal (closes with the sprint) |
| Discussion, Q&A, design back-and-forth | GitHub Issue & PR comments | Temporal (lives with the issue/PR that triggered it) |
| Code review findings, PR-level design rationale | PR description & review comments | Temporal (lives with the PR) |
| Commit history, file-level change trail | Git log | Persistent |
| ADR amendment tracking | Issue label `adr` + linked issue in the commit body | Persistent |
| Azure delivery records | Paired `infra`-labelled companion issue + PR description | Temporal (lives with the issue/PR) |

### Azure delivery records are not `.md` files

`infrastructure/README.md` carries the durable cost-attribution table; the **delivery record** for each Azure-impact change — the `EXPECTED DELIVERY` block from `.github/ISSUE_TEMPLATE/infra-change.md` — lives only in the paired `infra`-labelled companion issue and its PR description.

Azure delivery details grow large, are tightly coupled to a single issue/PR, and would pollute the codebase if duplicated. **Do not write a `.md` file for an Azure delivery inside this repository.** The issue/PR is the canonical record; the ADR or commit body references it by number.

This is restated in `AGENTS.md` §"Persistent vs temporal" and enforced by the `infra-change.md` / `task-managed.md` issue templates. The traceability rules themselves live in `ADR-010-issue-pr-discipline.md`.

## Consequences

### Positive
- Single context window for every AI session — rules, patterns, decisions, and code in one repo.
- ADRs sit next to the code they describe.
- One PR cycle for "doc + code that implements the doc."
- One issue tracker.
- Explicit information-routing discipline (this ADR §Information routing) prevents the repo from re-fragmenting via hidden `.md` files.

### Negative
- Repo size grows (docs add ≈ 60 KB of markdown — negligible).
- CI builds trigger on doc-only changes (mitigated by `paths-*` filters in `.github/workflows/ci.yml`).
- The legacy `Centinela-docs` repo is archival — pointer only, retained for issue-number lineage.

## Alternatives considered

| Alternative | Reason rejected |
|---|---|
| Keep both repositories as-is | AI fragmentation, parallel issue trackers, ongoing drift. |
| Pull documentation into `Centinela-Code` without retiring the docs repo | "Where is the doc?" ambiguity; does not fix source-of-truth drift. |
| Hold off until after the 21-day project | Issue drift + agent context loss already starting; deferring multiplies the cost. |

## References

- `AGENTS.md` § "Persistent vs temporal — where content belongs"
- `README.md` § "How to contribute"
- `ADR-010-issue-pr-discipline.md` § "PR↔Issue ↔ Azure traceability"
- Historical GitHub Issue [Team-Centinela/Centinela-docs#17](https://github.com/Team-Centinela/Centinela-docs/issues/17) (ADR-008 draft) — superseded by this ADR-005

## Status

**ACCEPTED** (2026-07-15). `Team-Centinela/Centinela-docs` archived. Issue transfer and ADR review tracked at [#16](https://github.com/Team-Centinela/Centinela-Code/issues/16) and [#20](https://github.com/Team-Centinela/Centinela-Code/issues/20).