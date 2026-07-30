# ADR-012: OpenCode Execution + Human-Handoff Contract

## Context

Centinela is a 5-person team with a 21-day / $60 budget. **Code development is performed by AI agents; humans focus on orchestration, architectural judgment, and the rare decisions the AI cannot make on its own.** PRs #120–#130, the §10.9 amendment (#195 / PR #199), and ADR-011 (PR #199) were produced under this model — the LOC committed by `feat/week-one-consolidation` against `main` (≈ 12,754 / −533 lines across 225 files at the time of writing) is **>90 % AI-authored**. This is not a future-state aspiration; it is the current operating posture.

An AI agent operating on this repository needs **three coordinated things** to make correct, audit-traceable decisions:

1. **Instructions** — the rules it must follow (preflight, AGENTS.md, ADRs, lane matrix). Without the instructions in context, the agent re-derives them from scratch and frequently diverges.
2. **Context** — the durable repository state (what was decided, what is merged, what is in flight, what is blocked, which ADR applies). Without context, the agent treats the repository as a blank slate and ships plausible-but-wrong changes.
3. **Algorithms** — the decision procedures the instructions and context feed (preflight checks, blocker resolution, cost attribution, commit-cadence decisions). Without algorithms, the agent has rules but no procedure for applying them and skips steps silently.

A failure of any of these three degrades into the same symptom: a PR that "looks fine" but contradicts an ADR, an issue closed without the closing comment, a `git push` without human authorization, a `terraform apply` against a wrong subscription. **The repository's compliance posture is the cumulative output of the AI agents operating on it, not an after-the-fact review step.**

The §10.9 Emulation Amendment (issue #195, PR #194 / PR #199) closed six gaps between ADR-010 and ADR-011 but raised five governance gaps in the broader OpenCode execution surface that are out of scope for that amendment:

| # | Gap | Surface | Symptom |
|---|---|---|---|
| 2 | `opencode.json:3-8` `instructions` lists 5 files by exact path. OpenCode does not recursively load `docs/decision-log/*.md` or `.github/agent-preflight.md`. The CONTEXT-MAP and AGENTS.md imply these load "automatically". | Instructions | Agent skips the ADR re-read step (preflight Rule 3) because the ADRs are not in its context window. |
| 3 | `opencode.json:19-30` `permission` block has no catch-all; `gh pr*` and `gh issue*` include `close` + `merge`. Agents can close issues and merge PRs by default. | Algorithms | Agent closes a tracked issue whose doc has not landed, or merges a PR whose CI is red. |
| 4 | No commit-cadence contract. A user may grant bounded standing authorization for checkpoint commits, but `git push` is not separately authorized. | Algorithms | Agent produces a coherent working tree but pushes without explicit human authorization at the end of a session. |
| 5 | No human-only action matrix. ADR acceptance, PR approval/merge, branch-protection changes, Azure auth/apply/destroy, budget decisions, secrets, issue closure are not enumerated. | Algorithms | Agent silently performs a human-only action because no rule forbids it. |
| 7 | Branch protection on `develop` and on PR head is **404 (not configured)**; 0 status checks; `link-check` + `matrices-build` jobs do not exist on `.github/workflows/`. ADR-010 §10.7 specifies these as required; prose is not enforcement. | Context (durable state) | CI does not fail on broken links or matrix failure; "always-green" guarantee on `develop` is fiction. |

The user-authored review on #195 (comment [`IC_kwDOTYe2Vs8AAAABMJtfrw`](https://github.com/Team-Centinela/Centinela-Code/issues/195#issuecomment-5110456239)) recommends a blocking sibling ADR — this one — rather than expanding the §10.9 amendment. The recommendation is accepted.

### Why this is single-decision-maker (audit-trail disclosure)

The §10.9 amendment was authorized by @SrLampi1001 alone with the team (except SrLampi1001) unavailable and WhatsApp-only concurrence; the violation is documented on #195. **ADR-012 normally requires team ratification under ADR-010's governance posture.** On 2026-07-29 the user (Lane A owner) directed that ADR-012 ship as a single-decision-maker governance amendment on the grounds that the §12.x enforcement layer is **operationally imperative**: code is >90 % AI-authored, the §10.9-amendment experience already demonstrated the failure mode of operating without this contract, and the cost of waiting for team ratification is more drift accumulation against a $60 budget with 6 days remaining. **Audit-trail disclosure is recorded in §12.7** so a future agent or auditor can identify who made the call and on what grounds. Team ratification will be sought at the next ceremony; until then, this ADR is enforceable.

## Decision

Centinela adopts the following OpenCode execution + human-handoff contract. **Every AI agent and every human collaborator follows the same rules; no privileged path exists for either.** §12.1–§12.3 are infrastructure-level (config + workflow), §12.4–§12.5 are normative contract, §12.6 is enforcement, §12.7 is audit-trail.

### 12.1 Instruction loader contract

`opencode.json` `instructions` MUST enumerate every file the agent must read on session start, **or** declare a recursive pattern (paths/globs). Both forms are valid; **silently listing a subset of files while the docs imply broader loading is forbidden.**

| Form | When valid | Example |
|---|---|---|
| **Recursive pattern** | When the OpenCode loader supports glob expansion for `instructions` | `"instructions/docs/decision-log/**/*.md"` |
| **Explicit enumeration** | Always valid; required when the loader does not support globs | `"docs/decision-log/ADR-010-...md"`, `"docs/decision-log/ADR-011-...md"`, `"docs/decision-log/ADR-012-...md"` |

The CONTEXT-MAP (`.opencode/CONTEXT-MAP.md`) MUST accurately describe what the loader actually does. Phrases like "loaded automatically" or "auto-load" are forbidden if the loader does not auto-load; the CONTEXT-MAP must say so explicitly (the existing note in §"Note on what opencode.json loads (review item 2 of #195)" is the canonical wording).

**CI gate**: a `link-check`-adjacent job (or a dedicated `instruction-loader-check` job) asserts that every `docs/decision-log/ADR-*.md` file appears in `opencode.json:instructions` (in the enumeration form) OR is covered by a recursive pattern. The job fails CI when a new ADR lands and the instruction list is not updated. **This gate is the enforcement mechanism for #196 item 2.**

### 12.2 Permission defaults

`opencode.json` `permission` block MUST declare an explicit catch-all (`"edit": "ask"` is the default; `"bash": "ask"` for unlisted commands) **and** explicit `deny` entries for the human-only actions enumerated in §12.4. The current blanket `"edit": "allow"` + unrestricted `bash` is forbidden — it permits agents to perform any of the human-only actions by default.

| Command class | Default | Rationale |
|---|---|---|
| `gh issue*` (read) | `allow` | Issue reading is an information-gathering action, not state-changing. |
| `gh issue close` | **deny** | Issue closure requires the closing-comment discipline (ADR-010 §10.4); agents emit `USER ACTION REQUIRED` per §12.5. |
| `gh issue edit` | `ask` | Issue edits may rewrite acceptance criteria or labels; surface for review. |
| `gh pr*` (read) | `allow` | PR reading is information-gathering. |
| `gh pr merge` | **deny** | Merge is a state-changing action requiring human authorization per §12.4 + §12.5. |
| `gh pr close` | **deny** | PR closure requires human authorization. |
| `git push` | **deny** | Push is per-commit authorized per §12.3; standing authorization does not extend to push. |
| `git commit` (with prior `checkpoint` standing authorization) | `allow` | Standing authorization must be explicitly granted per §12.3; the agent must cite the grant in its preamble. |
| `terraform apply` / `terraform destroy` | **deny** | Real-Azure state mutation requires human authorization per §12.4. |
| `az *` (write) | **deny** | Same as Terraform. |
| `gh api` (mutation) | `ask` | Surface for review; the response can include state mutations. |

The catch-all `bash` MUST be `"ask"`, not `"allow"`. An agent that needs a new command requests authorization at the boundary per §12.5 and stops until granted. **This is the "stop at boundary" contract.**

### 12.3 Commit cadence contract

The user picks a **commit cadence** at the start of each session, expressed in the preamble. Three modes are valid:

| Mode | User standing authorization | Agent behavior |
|---|---|---|
| **`none`** | None. | Agent stages and shows the diff for review. **No commit, no push.** User commits manually. |
| **`checkpoint`** | "You may create local commits at logical boundaries; do not push." | Agent commits at logical boundaries (one commit per single concern per Commit Hygiene §1). **No push.** User pushes manually. |
| **`final`** | "You may create local commits and push once at session end." | Agent commits at logical boundaries + pushes once at session end. **Push is per-session, not per-commit; the agent pauses and confirms before pushing.** |

Standing authorization for `checkpoint` MUST be explicit (e.g., "use checkpoint mode for this session" in the user's prompt) and recorded in the session preamble. **Implicit authorization (a session that runs for 30 turns without explicit mode) defaults to `none`.**

`git push` is **always per-commit authorized** — even in `final` mode, the agent pauses and confirms before each push. The push confirmation cites (a) the branch name, (b) the commit SHAs to be pushed, (c) the linked issue number(s), and (d) the verification-evidence summary if a companion infra issue is involved.

**Rationale.** The risk model is: an AI agent produces a working tree that *looks* correct but is not what a human reviewer would have committed. Commit cadence gives the human a checkpoint; pushing is the irreversible boundary, and so it requires explicit per-event authorization.

### 12.4 Human-only action matrix

The following actions are **human-only**. AI agents emit `USER ACTION REQUIRED` (exact wording in §12.5) and stop at the boundary. **No agent path exists for these actions**, including under standing authorization in §12.3.

| Action | Why human-only | Reference |
|---|---|---|
| **ADR acceptance / ratification** | Architectural decision; AI may draft, humans ratify | ADR-010 governance lane |
| **PR approval / merge** | Code review responsibility | ADR-010 §10.7 |
| **Branch protection configuration** | Affects the "always-green" guarantee on `develop` | ADR-010 §10.7 |
| **Azure authentication** | Subscription-level security boundary | n/a (security baseline) |
| **`terraform apply` / `terraform destroy`** | Real-Azure state mutation; irreversible within a session | ADR-009, ADR-002 |
| **Budget decisions** (raise ceiling, override alert thresholds) | $60 / 21-day ceiling is the cost-care promise | ADR-007 §7.7 |
| **Secret rotation / creation** | Secrets are an authentication boundary | ADR-006 §6.3 |
| **Issue closure on a tracked item whose AC includes a doc change** | The closing-comment discipline (ADR-010 §10.4) requires the SHA of the doc commit; AI cannot synthesize it | ADR-010 §10.4 |
| **Push to `main`** | Released branch; one-maintainer + CI-green rule | ADR-005 monorepo model |

**Cross-reference matrix.** §12.2 deny-list is the *operational* enforcement; this matrix is the *normative* contract. An entry in §12.2 without a corresponding row here is a misconfiguration; a row here without a corresponding §12.2 deny entry is unenforced prose.

### 12.5 Handoff wording contract — `USER ACTION REQUIRED`

When an agent encounters a §12.4 boundary or needs a §12.3 authorization it does not have, it emits the **exact** handoff block:

```
USER ACTION REQUIRED

Boundary: <row from §12.4 matrix> (e.g. "ADR acceptance / ratification")
Issue(s): #<N> (tracking issue this affects)
Action requested: <one-line description>
Context: <one-paragraph summary; cite ADR + path when relevant>
After you act: <what the agent will do next>

Standing authorization status: <none | checkpoint | final> per §12.3
```

The agent **stops emitting subsequent actions** until the user acts (or grants standing authorization and re-prompts). This is the "stop at boundary" contract. The exact wording is normative — variations dilute the audit trail and make automated scanning of commit history impossible.

A `USER ACTION REQUIRED` block in a commit message body **is forbidden** (see ADR-010 Commit Hygiene + §12.6 enforcement). It belongs in chat / issue comments / PR review comments, not in repository history. Repository history is for *what was done*, not *what was asked*.

### 12.6 Branch protection + CI enforcement

Branch protection on `develop` and on every PR head branch is **configuration, not prose**. ADR-010 §10.7 specifies:

| Branch | Required checks | Required reviews | Squash-only |
|---|---|---|---|
| `main` | `link-check`, `matrices-build` | 1 maintainer | yes |
| `develop` | `link-check`, `matrices-build` | 1 reviewer per lane | no (regular merge) |

`link-check` already exists as `.github/workflows/docs-link-check.yml`; it runs `lychee` and fails on broken links per AGENTS.md §"Docs CI".

**`matrices-build` is the new job owed by this ADR.** It MUST:

1. Run Java 21 (`mvn verify`), Python 3.12 (`pytest`), Node 22 (`npm run build && npm test`) on every PR touching `services/`, `infrastructure/`, or `.github/`.
2. Run the `verify-emulators.{ps1,sh}` script (per ADR-011 §11.2) on every PR touching `docker-compose.yml`, `docker/**`, `application-local-emulator.yml`, or any `application*.yml` profile.
3. Fail the build on any `❌ FAIL` or `[FAIL]` from either gate.

**Until `matrices-build` exists and branch protection requires it, ADR-012 §12.6 is in a transitional state.** Lane E (@Santiagodxz) owes the workflow file; Lane A (@SrLampi1001) owes the `gh api` calls to wire branch protection. The status is tracked in the §12.7 audit trail.

### 12.7 Audit trail

| Date | Author | Action | Reference |
|---|---|---|---|
| 2026-07-29 | @SrLampi1001 (single-decision-maker per user directive) | Ratified ADR-012 as a governance amendment | This issue (#196); user directive on 2026-07-29 overriding team-ratification requirement |
| 2026-07-29 | (pending) Lane E (@Santiagodxz) | Land `matrices-build` workflow file | #196 acceptance criteria row 5 |
| 2026-07-29 | (pending) Lane A (@SrLampi1001) | Wire branch protection via `gh api` | #196 acceptance criteria row 5 |
| (next ceremony) | Team | Ratify ADR-012 by team review | #196 §"Audit-trail note (single-decision-maker)" |
| (rolling) | Any agent or human who encounters a §12.4 boundary | Append a row above citing the issue/PR | This ADR is the source of truth |

**Identification requirement.** Every row in this table carries the actor's GitHub handle. The reason: a future agent reading the audit trail must be able to identify which decision was made by whom and on what grounds. The §10.9-amendment experience (#195 + comment [`IC_kwDOTYe2Vs8AAAABMJtfrw`](https://github.com/Team-Centinela/Centinela-Code/issues/195#issuecomment-5110456239)) demonstrates why this matters.

### 12.8 Commit-message content rule (companion to #256)

Repository commits MUST contain only durable change context useful to repository history. They MUST NOT contain:

- Session-only instruction text the AI agent received in the current session (e.g. quoted AGENTS.md clauses, preflight-rule recitations, prompt fragments).
- Compliance filler ("per ADR-012 §12.4", "as required by the agent preflight", etc.).
- `USER ACTION REQUIRED` blocks (per §12.5).
- Reasoning trace from the AI's session ("I noticed…", "the user said…", "based on the conversation…").

The commit message describes **what was changed and why the change is durable**, not **how the AI arrived at it**. The history reader is a future human or AI who did not participate in the session.

This rule reconciles with the existing Commit Hygiene (§"Single concern per commit, with cross-referenced docs") — the cross-reference is by issue/PR number and ADR filename, not by quoted prose.

The rule is normative; enforcement is by review (Lane A's governance review per `#134` §"Verification Checklist" + the `link-check` job as the CI gate for cross-references that resolve). Commit messages that violate the rule are amended in a follow-up commit, not silently accepted.

## Consequences

### Positive

- **Predictable AI behavior.** Instructions, context, and algorithms are co-located in the agent's effective context; the failure modes in the §Context table are structurally closed.
- **Auditable governance.** Every human-only action has a deny entry (§12.2), a normative row (§12.4), and an audit row (§12.7). A reader can answer "who authorized X?" by looking up the issue and the §12.7 table.
- **Branch protection enforces the prose.** ADR-010 §10.7's "always-green" guarantee becomes a CI reality, not a roadmap.
- **Commit-history hygiene.** Repository `git log` reads as durable change history, not AI session noise; future agents reading the log get signal, not compliance filler.
- **Single-decision-maker path is documented.** The audit trail records who made the call and on what grounds; future agents see the precedent and apply it consistently.

### Negative

- **More ceremony per session.** Every session declares a commit cadence (§12.3); every boundary emits `USER ACTION REQUIRED` (§12.5); every human-only action shows up in the audit trail (§12.7). For a 30-turn session this is a few hundred tokens of overhead. **Acceptable cost for the audit clarity.**
- **`matrices-build` job is new.** Lane E owes the workflow file; until it lands, §12.6 is in a transitional state and prose-only.
- **Permission block is restrictive.** New commands require `ask` authorization rather than default-allow. This is the explicit trade-off vs. the prior blanket-allow posture — fewer surprises, more chat overhead.
- **Single-decision-maker ratification is recorded as an exception.** This ADR explicitly does **not** set a precedent for skipping team ratification on subsequent amendments. The audit trail is the mechanism that makes the exception visible; team ratification at the next ceremony restores the standard posture.

## Alternatives Considered

| Alternative | Reason rejected |
|---|---|
| **Expand the §10.9 amendment (#195)** to include the five gaps | The user-authored review on #195 explicitly recommends against this: §10.9 is correctly bounded to emulator-surface concerns; mixing in instruction loader + permission defaults + commit cadence + human-only matrix would explode the PR's scope and make review unworkable. The sibling-amendment pattern is the right shape. |
| **Defer ADR-012 until team is available** | The cost of waiting is more drift against a 6-day budget remainder. The §10.9-amendment experience demonstrates that AI agents without this contract accumulate drift at the rate of ~5 governance gaps per major AI-authored PR. Single-decision-maker ratification with audit trail (§12.7) is the lesser evil. |
| **Rely on prose-only governance** (current state) | The branch-protection-404 (#196 item 7) is the proof that prose is not enforcement. ADR-010 §10.7 has been on disk for 8 days with zero enforcement; CI does not fail because the workflow does not exist. Prose cannot substitute for `gh api` + `.github/workflows/`. |
| **Move all human-only actions to a separate `human-only.md` file** | Splits the contract. The human-only matrix belongs with the loader + permission + cadence + handoff-wording contract because they enforce each other. Splitting invites drift between the matrix and its enforcement. |
| **Allow blanket `"bash": "allow"` with a separate `deny` list** | Functionally equivalent for the deny-list but loses the "ask by default" posture. A new command that is not in the deny list would be allowed by default — the §12.4 matrix has rows the current deny list does not enumerate (e.g. secret rotation). The catch-all `ask` is the conservative default. |

## References

- **#196** — `[ADR-012] OpenCode execution + human-handoff contract (review items 2-5 + 7-enforcement)` — this ADR's tracking issue
- **#195** — `[ADR-010 amend]` sibling, where ADR-012 is recommended
- **#256** — `[Lane-A] Keep session instructions out of commit messages` — companion governance rule (the §12.8 normative text)
- **PR #194 / PR #199** — vehicle for the §10.9 amendment; surfaces the five gaps via comment [`IC_kwDOTYe2Vs8AAAABMJtfrw`](https://github.com/Team-Centinela/Centinela-Code/issues/195#issuecomment-5110456239)
- **PR #254** — last governance-PR merged before this ADR; demonstrates the consolidation-pass pattern this ADR formalizes
- `opencode.json:3-30` — current instructions + permission block (the surface §12.1 + §12.2 modify)
- `.github/agent-preflight.md` — three-rule AI preflight (cited by §12.1 enforcement + §12.5 handoff)
- `AGENTS.md` — root behavior rules + Commit Hygiene (cited by §12.3 + §12.8)
- `docs/best-practices/05-pr-and-issue-discipline.md` — operational companion to ADR-010 (cited by §12.4 + §12.6)
- `docs/decision-log/ADR-010-issue-pr-discipline.md` — base governance ADR (§10.7 branch protection; §10.4 closing-comment discipline)
- `docs/decision-log/ADR-011-local-emulator-stack.md` — emulator stack ADR (§11.2 verify-script gate; §11.7 hard-prereq)
- `.opencode/CONTEXT-MAP.md` — context map; updated to include ADR-012 in the read order

## Status

**ACCEPTED** (2026-07-29, single-decision-maker per Lane-A governance lane). Tracking issue #196; companion governance rule in #256. `matrices-build` workflow file is owed by Lane E (@Santiagodxz) before §12.6 enforcement is fully wired. Team ratification is owed at the next ceremony; until then this ADR is enforceable under the §12.7 audit-trail disclosure.