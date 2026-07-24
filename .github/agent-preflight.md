# `.github/agent-preflight.md` — Centinela AI Agent Preflight

> Per ADR-010 §10.6. Read this file **before claiming or implementing any work** in Centinela.

## Three-Rule Preflight

Before claiming an issue:

### Rule 1 — Verify blockers satisfy

Read the `Blocked by:` line on the issue you intend to claim. **Every listed issue number must resolve to a closed or unblocked-at-claim-time state.** If any blocker is open, do not claim; surface the conflict on the issue comment instead.

If the issue has **no** `Blocked by:` line but the work touches cross-area territory (infra ↔ code ↔ observability), **add one** by referencing the issue you're sure is closed (e.g. `#B.2` for Flyway V1 in Lane B).

### Rule 2 — Verify `Has azure-impact` + companion infra

Read `Has azure-impact:`. If `yes`:

1. Verify `Companion infra issue:` is present (URL or `#—`).
2. Verify the companion is reachable (open or figure it out from references).
3. If the companion has `EXPECTED DELIVERY` and `COST-ATTRIBUTION` blocks already filled, mirror them.

If `no` — also confirm: is the work secretly touching a deployment surface? Look at any of:

- `application*.yml`, `application*.properties`
- Dockerfile, container build context
- Spring Cloud Stream binder or Service Bus binding code
- Secret accessor class or Key Vault ref config
- Terraform `*.tf` files

If yes to any, the work is `Has azure-impact: yes` and missing a companion.

### Rule 3 — Re-read source-of-truth docs

Before any **write** action (PR, code change, new doc), re-read in this order:

1. The **ADR(s)** referenced in the issue (look in `/docs/decision-log/`).
2. The **pattern doc(s)** referenced (`/docs/patterns/`).
3. The **service README** of the file you are touching (`/services/<service>/README.md`).
4. AGENTS.md **last** to confirm no rule conflict.

A pull request that opens without this re-read can be safely rejected.

## Discipline reminders

- **AI agents and humans follow the same rules.** No privileged path.
- **Issue claim is the gate, not PR-open.** Pre-flight rules apply at claim time.
- **The CI gates `matrices-build` and `link-check` enforce on merge.** If the AI can't satisfy them, the merge fails; do not skip.
- **When in doubt, open an issue** referencing `process` label rather than silently proceeding.
- **No force-push on `develop`.** Squash merging from feature branches only.

## Companion-template quick reference

For a task-management claim under Lane B (Ingestion) the canonical claim preamble is:

```
Pre-flight (Rule 1):  Blockers #B.2 (Flyway V1) ✓, #B.3 (domain) ✓, #E.1 ✗ → wait.
Pre-flight (Rule 2):  Has azure-impact: no (auth filter scope; no env-var change).
Pre-flight (Rule 3):  ADR-006 §6.1 re-read. patterns/06-idempotency-key.md cross-ref.
                       services/ingestion/README.md §Auth filter re-read.
```

For an infra claim under Lane C (Serverless Engine):

```
Pre-flight (Rule 1):  Blockers #127 (PR bootstrap not merged) ✗ → wait.
Pre-flight (Rule 2):  Has azure-impact: yes → companion #C.A1 same milestone.
Pre-flight (Rule 3):  ADR-003 §3.4 + ADR-009 §9.1 re-read.
                       docs/patterns/07-azure-impact-companion-issue.md re-read.
```

## If you encounter drift

- **Code/PR body contradicts an ADR**: open an `adr-amendment` issue via the `.github/ISSUE_TEMPLATE/adr-amend.md` template. Do not rewrite the code or the ADR silently.
- **Issue template is missing a field**: file the issue via the closest-matching template; reference the missing-field gap in `process` label.
- **Branch is mis-targeted (PR targets `main` instead of `develop`)**: comment "target = develop per ADR-010 / AGENTS.md", and do not merge to `main`.
- **Obsidian mirror folders appear**: leave them; `.gitignore` is the source of truth.

## Related Documents

- ADR-010 — `../docs/decision-log/ADR-010-issue-pr-discipline.md`
- `docs/best-practices/05-pr-and-issue-discipline.md`
- `docs/patterns/07-azure-impact-companion-issue.md`
- `/AGENTS.md`
- `/docs/AGENTS.md`
