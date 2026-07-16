# ADR-005: Monorepo Unification

## Status
**DRAFT** → **EXECUTED** (2026-07-15). ADR approved and acted upon by the team. Track finalization at [#16](https://github.com/Team-Centinela/Centinela-Code/issues/16). Historical context: this ADR aligns with the original GitHub Issue [Team-Centinela/Centinela-docs#17](https://github.com/Team-Centinela/Centinela-docs/issues/17) (ADR-008).

## Context

Two repositories carry Centinela today:

- `Team-Centinela/Centinela-docs` — *archived* repository. Historical architectural and decision documentation. (Read-only since 2026-07-15.)
- `Team-Centinela/Centinela-Code` — currently empty. Implementation repository.

The split emerged organically, but in a project executed by AI agents in 21 days with a 4-person team, **the split fragments context**:

| Problem | Cause |
|---|---|
| AI agents working in `Centinela-Code` cannot see `architecture/`, `patterns/`, `best-practices/`, `decision-log/` without manually opening another repo | Cross-repo context is not loaded by IDE/Copilot |
| Historical ADRs ([`Centinela-docs` #1 to #28](https://github.com/Team-Centinela/Centinela-docs/issues?q=is%3Aissue)) and local architecture markdown drift out sync | Two locations for related content |
| Obsidian mirror folders appear in `Centinela-docs` because the GH Issues plugins mirror issues from each repo separately | Two parallel issue trackers' worth of mirrors |
| Reviewers must traverse PRs that are purely docs but live in the code repo without context | Slow PR review |

## Decision

**Unify `Centinela-docs` and `Centinela-Code` into a single monorepo: `Team-Centinela/Centinela-Code`.**

```
centinela-code/
├── docs/                   # Currently this directory you are reading lives in.
│   ├── architecture/
│   ├── patterns/
│   ├── best-practices/
│   ├── decision-log/
│   ├── AGENTS.md
│   └── ASSIGNMENT.md
├── services/               # The 4 services (ingestion, core, ocr, frontend)
├── infrastructure/         # Terraform IaC
├── .github/                # Workflows, issue templates
└── README.md
```

The next-generation `AGENTS.md` in the monorepo becomes the only one and removes the *Never create code files inside this repository* rule (it now applies to `docs/` only).

## Consequences

### Positive
- Single context window for every AI session — rules, patterns, decisions, and code in one repo.
- ADRs sit next to the code they describe.
- One PR cycle for "doc + code that implement the doc."
- One issue tracker (transfer is automatic in GH with the issue API).
- The unified Obsidian GitHub plugin mirrors one set of issues, not two.

### Negative
- Repo size grows (docs add ≈ 60 KB of markdown — negligible).
- CI builds trigger on doc-only changes (mitigated by `paths-*` filters in `.github/workflows/ci.yml`).
- Issue transfer for 17 closed/open issues may need manual numbering preservation.
- The `Centinela-docs` repo becomes archival — pointer only.

### Migration plan (for Sprint 0)

1. Create the new monorepo layout, copying `architecture/`, `patterns/`, `best-practices/`, `decision-log/`, `AGENTS.md`, `ASSIGNMENT.md`, `README.md`, `.gitignore` from this repo.
2. Transfer all 17 GitHub Issues from this repository to `Centinela-Code` (gh CLI: `gh issue transfer <num> Team-Centinela/Centinela-Code`).
3. Update all `(`#N`)` references to the new repository URLs (`https://github.com/Team-Centinela/Centinela-Code/issues/N`). Historical references that point to archival issues (e.g. old `#3`, `#4`, `#5`, `#7`, `#17`) keep their original Centinela-docs URLs so the lineage is traceable.
4. Move `services/`, `infrastructure/`, `.github/`, `services/` directories from `Centinela-Code` into the unified root (with the move/rename of `docs/`).
5. Replace this repository's `README.md` with a stub pointing to `Centinela-Code`.
6. Update the `.gitignore` here to permit Obsidian to be re-pointed at the new repo.
7. After all references have been changed and the new repo is in steady-state, archive this repo.

## Alternatives considered

| Alternative | Reason rejected |
|---|---|
| Keep both repositories as-is | AI fragmentation, forum-double issues, ongoing drift. |
| Pull documentation into `Centinela-Code` without retiring the docs repo | Force repos to ask "where is the doc?" — adds complexity, doesn't fix the source-of-truth drift. |
| Hold off until after the 21-day project | Issue drift + agent context loss already starting; deferring multiplies the cost. |

## References

- `AGENTS.md` § "Persistent vs temporal — where content belongs"
- `README.md` § "How to contribute"
- Historical GitHub Issue [Team-Centinela/Centinela-docs#17](https://github.com/Team-Centinela/Centinela-docs/issues/17) (ADR-008 draft) — superseded by this ADR-005

## Status

**EXECUTED** (2026-07-15) — by the AI session that finalized this Markdown. [Team-Centinela/Centinela-docs](https://github.com/Team-Centinela/Centinela-docs) now archived. Issue transfer and ADR review tracked at [#16](https://github.com/Team-Centinela/Centinela-Code/issues/16) and [#20](https://github.com/Team-Centinela/Centinela-Code/issues/20).
