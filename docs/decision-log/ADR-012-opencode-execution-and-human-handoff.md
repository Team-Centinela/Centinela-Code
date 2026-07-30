# ADR-012: OpenCode Execution + Human-Handoff Contract

## Context

Centinela is a 5-person team where **Code development is performed by AI agents; humans focus on orchestration, architectural judgment, and the rare decisions the AI cannot make on its own.** PRs #120–#130, the §10.9 amendment (#195 / PR #199), and ADR-011 (PR #199) were produced under this model — the LOC committed by `feat/week-one-consolidation` against `main` (≈ 12,754 / −533 lines across 225 files at the time of writing) is **>90 % AI-authored**. This is not a future-state aspiration; it is the current operating posture.

An AI agent operating on this repository needs **three coordinated things** to make correct, audit-traceable decisions:

1. **Instructions** — the rules it must follow (preflight, AGENTS.md, ADRs, lane matrix). Without the instructions in context, the agent re-derives them from scratch and frequently diverges.
2. **Context** — the durable repository state (what was decided, what is merged, what is in flight, what is blocked, which ADR applies). Without context, the agent treats the repository as a blank slate and ships plausible-but-wrong changes.
3. **Algorithms** — the decision procedures the instructions and context feed (preflight checks, blocker resolution, cost attribution, commit-cadence decisions). Without algorithms, the agent has rules but no procedure for applying them and skips steps silently.

A failure of these three degrades into the same symptom: a PR that "looks fine" but contradicts an ADR, an issue closed without the closing comment, a `git push` without human authorization, a `terraform apply` against a wrong subscription. **The repository's compliance posture is the cumulative output of the AI agents operating on it, not an after-the-fact review step.**

## Decision

Centinela adopts the following OpenCode execution + human-handoff contract. **Every AI agent and every human collaborator follows the same rules; no privileged path exists for either.** §12.1–§12.3 are infrastructure-level (config + workflow), §12.4–§12.5 are normative contract, §12.6 is enforcement.

### 12.1 Instruction loader contract

`opencode.json` `instructions` MUST enumerate every file the agent must read on session start, **or** declare a recursive pattern (paths/globs). Both forms are valid; **silently listing a subset of files while the docs imply broader loading is forbidden.**

| Form | When valid | Example |
|---|---|---|
| **Recursive pattern** | When the OpenCode loader supports glob expansion for `instructions` | `"instructions/docs/decision-log/**/*.md"` |
| **Explicit enumeration** | Always valid; required when the loader does not support globs | `"docs/decision-log/ADR-010-...md"`, `"docs/decision-log/ADR-011-...md"`, `"docs/decision-log/ADR-012-...md"` |

The CONTEXT-MAP (`.opencode/CONTEXT-MAP.md`) MUST accurately describe what the loader actually does. Phrases like "loaded automatically" or "auto-load" are forbidden if the loader does not auto-load; the CONTEXT-MAP must say so explicitly (the existing note in §"Note on what opencode.json loads (review item 2 of #195)" is the canonical wording).

**CI gate**: a `link-check`-adjacent job (or a dedicated `instruction-loader-check` job) asserts that every `docs/decision-log/ADR-*.md` file appears in `opencode.json:instructions` (in the enumeration form) OR is covered by a recursive pattern. The job fails CI when a new ADR lands and the instruction list is not updated.

**No skipping existing ADRs.** The only legitimate ADR-number skip is one whose file does not exist on disk. Skipping an existing ADR is forbidden — it is the failure mode §12.1 was designed to prevent.

### 12.2 Permission defaults

`opencode.json` `permission` block MUST declare an explicit catch-all (`"edit": "ask"` is the default; `"bash": "ask"` for unlisted commands) **and** explicit `deny` entries for the human-only actions enumerated in §12.4.

**Schema verification.** The OpenCode `permission` schema is documented at `https://opencode.ai/docs/permissions/`. Verified facts (verified on 2026-07-29 against the live schema at `https://opencode.ai/config.json` + the docs page):

- `permission` accepts either a string (`"allow" | "ask" | "deny"` — applies globally) or an object with tool-keyed rules.
- Object form: each top-level key (`edit`, `bash`, `read`, `webfetch`, `websearch`, `glob`, `grep`, `list`, `task`, `lsp`, `skill`, `question`, `external_directory`, `doom_loop`, `todowrite`) is a tool name. **The key `"*"` is a special catch-all** that matches any tool not explicitly named.
- Per-tool rule values: either a string action, or an object where keys are **wildcard patterns** and values are actions.
- **Wildcard semantics** (verified against the docs): `*` matches zero or more of any character; `?` matches exactly one character; everything else is literal.
- **"Last matching rule wins"** — order matters. The catch-all `"*"` is typically placed first; more specific rules override it.
- **Bash patterns match the parsed command name, not the full shell line.** Per the docs tip: *"Commands like `git status` work for default behavior but require explicit permission (like `git status *`) when arguments are passed."* So `"git push"` matches `git push` (no args) but **not** `git push origin main`; `"git push *"` matches `git push origin main` but **not** `git push` (no args). The cleanest single-rule form is `"git push*"` (no space before `*`), which matches both.
- **OpenCode defaults**: most permissions default to `"allow"` if unset. `doom_loop` and `external_directory` default to `"ask"`. Without an explicit top-level `"*": "ask"`, unlisted tools (e.g., `webfetch`, `websearch`) default to `allow` and bypass §12.4.

| Command class | Default | Rationale |
|---|---|---|
| `gh issue view/list/comment*` (read) | `allow` | Issue reading is an information-gathering action, not state-changing. |
| `gh issue close*` | **allow** | Per §12.4 the human-only condition is narrower ("AC includes a doc change"); for ordinary task-completion closures the agent knows the doc-commit SHA from its own work and can write a closing comment per ADR-010 §10.4. |
| `gh issue edit*` | **ask** | Issue-body edits carry *contextual debt* — the developer doesn't see silent edits, and the issue body is the audit trail. The agent must propose the edit text in chat and let the developer accept it manually. |
| `gh pr view/list/diff*` (read) | `allow` | PR reading is information-gathering. |
| `gh pr merge*` | **deny** | Merge is a state-changing action requiring human authorization per §12.4 + §12.5. |
| `gh pr close*` | **deny** | PR closure requires human authorization. |
| `git push*` | **allow** | `git push` is non-destructive (no remote history loss unless `--force`), is the natural completion of a `final`-mode session, and denying it caused the developer to lose local work when sessions ended with the laptop closed. §12.3 `final` mode already commits the developer to push at session end with per-push confirmation; that contract is the gate, not the permission system. **Force-push is denied separately** — see `git push --force*` / `git push -f*` row below. |
| `git commit*` (with prior `checkpoint` standing authorization) | `allow` | Standing authorization must be explicitly granted per §12.3; the agent must cite the grant in its preamble. |
| `terraform apply*` / `terraform destroy*` | **deny** | Real-Azure state mutation requires human authorization per §12.4. |
| `az*` | **deny** | Real-Azure CLI mutation requires human authorization per §12.4. |
| `gh api*` (mutation) | `ask` | Surface for review; the response can include state mutations. |
| `webfetch` / `websearch` | `ask` | External network access requires authorization; no standing allowance. |

The catch-all `permission: {"*": "ask"}` MUST be at the top level of the `permission` block (not just inside `bash`). Without it, unlisted tools default to `allow` per the OpenCode defaults — defeating §12.4. An agent that needs a new command or tool requests authorization at the boundary per §12.5 and stops until granted. **This is the "stop at boundary" contract.**

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
| **Issue closure on a tracked item whose AC includes a doc change AND the agent did not make the doc commit** | The closing-comment discipline (ADR-010 §10.4) requires the SHA of the doc commit; if the agent did not make the commit (e.g., another lane did), it cannot synthesize the SHA. **For ordinary task-completion closures where the agent *did* the work, this row does not apply** and the agent may close per §12.2. | ADR-010 §10.4 |
| **Issue-body edit (any change to issue title / body / labels beyond `gh issue close` and `gh issue comment`)** | Silent edits carry *contextual debt* — the developer doesn't see the edit, and the issue body is the audit trail. The agent proposes the edit text in chat; the developer accepts it manually. | ADR-010 governance |
| **Push to `main`** | Released branch; one-maintainer + CI-green rule | ADR-005 monorepo model |
| **`git push --force*` / `git push -f*`** | Force-push rewrites remote history; recovery is destructive. Denied in `opencode.json:permission.bash`. **Legitimate human use case**: legitimate use is rare (months between occurrences) and the AI-forgetting-upstream scenario — force-pushing over collaborators' work — is the real risk. The developer can run `git push --force` manually when credential-mistake case arises; the AI is not the right actor for history-rewriting commands. | User review 2026-07-29 |

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

### 12.6 Matrices build

1. Run Java 21 (`mvn verify`), Python 3.12 (`pytest`), Node 22 (`npm run build && npm test`) on every PR touching `services/`, `infrastructure/`, or `.github/`. (Inform the user if any required tool is missing in the sandbox)
2. Run the `verify-emulators.{ps1,sh}` script (per ADR-011 §11.2) on every PR touching `docker-compose.yml`, `docker/**`, `application-local-emulator.yml`, or any `application*.yml` profile.
3. Fail the build on any `❌ FAIL` or `[FAIL]` from either gate.

### 12.8 Commit-message content rule

Repository commits MUST contain only durable change context useful to repository history. They MUST NOT contain:

- Session-only instruction text the AI agent received in the current session (e.g. quoted AGENTS.md clauses, preflight-rule recitations, prompt fragments).
- Compliance filler ("per ADR-012 §12.4", "as required by the agent preflight", etc.).
- `USER ACTION REQUIRED` blocks (per §12.5).
- Reasoning trace from the AI's session ("I noticed…", "the user said…", "based on the conversation…").

The commit message describes **what was changed and why the change is durable**, not **how the AI arrived at it**. The history reader is a future human or AI who did not participate in the session.

This rule reconciles with the existing Commit Hygiene (§"Single concern per commit, with cross-referenced docs") — the cross-reference is by issue/PR number and ADR filename, not by quoted prose.

The rule is normative; Commit messages that violate the rule are amended in a follow-up commit, not silently accepted.

### 12.9 Compaction reality and the fail-safe commit-cadence rule

**Observed problem.** OpenCode auto-compaction keeps only the most recent N user turns verbatim in active context. Everything before is summarized. Two consequences:

1. The `opencode.json` content read at session start (and any §12.x contract derived from it) is summarized away. The agent may forget which commands are `deny` / `ask` / `allow` even though OpenCode itself still enforces them.
2. The §12.3 commit-cadence decision (`none` / `checkpoint` / `final`) was declared in the session preamble — the first user turn. For a long session (>50 turns), the cadence declaration is in the summarized part. The agent may default to *no remembered mode* and either ask repeatedly or pick the wrong mode.

**Mitigation layer 1 — config.** `compaction.tail_turns: 50` keeps the cadence preamble and most of the working context verbatim. The empirical upper bound on turn count for a single-session ADR-012 rollout (~30 turns) is well under 50, so the cadence is preserved for the working session.

**Mitigation layer 2 — agent self-check (normative).** When the agent is uncertain about the commit cadence, it MUST default to `none` (the conservative mode) and emit `USER ACTION REQUIRED` per §12.5 to reconfirm cadence with the user. It MUST NOT assume `final` and push, and MUST NOT assume `checkpoint` and commit without user confirmation. This rule is codified in `AGENTS.md` §"Always" as the fail-safe commit-cadence clause.

**Mitigation layer 3 — instructions survive compaction.** The `opencode.json:instructions` array (AGENTS.md + ADRs + CONTEXT-MAP) is part of the agent's system-level prompt, not user-turn content. Compaction does not prune system-prompt content. The §12.x normative contract therefore remains in the agent's effective context even after compaction of the user-turn layer. The risk is the agent's *working memory* (recent turns) losing the cadence decision, not the *instructions* themselves.

**Known limitation.** If `tail_turns: 50` proves insufficient for very long sessions (>100 turns), the mitigation is to (a) raise `tail_turns` further, or (b) require the user to re-state cadence at session start as a matter of course (a developer-counseling norm, not an ADR-enforced rule). The first is a one-line config change; the second is documented in the developer-handoff guide (TBD — Lane A owes the guide).

## Consequences

### Positive

- **Predictable AI behavior.** Instructions, context, and algorithms are co-located in the agent's effective context; the failure modes in the §Context table are structurally closed.
- **Matrices build enforces the prose.** ADR-010 §10.7's "always-green" guarantee becomes a CI reality, not a roadmap.
- **Commit-history hygiene.** Repository `git log` reads as durable change history, not AI session noise; future agents reading the log get signal, not compliance filler.

### Negative

- **More ceremony per session.** Every session declares a commit cadence (§12.3); every boundary emits `USER ACTION REQUIRED` (§12.5); For a 30-turn session this is a few hundred tokens of overhead. **Acceptable cost for the audit clarity.**
- **Permission block is restrictive.** New commands require `ask` authorization rather than default-allow. This is the explicit trade-off vs. the prior blanket-allow posture — fewer surprises, more chat overhead.
- **Single-decision-maker ratification is recorded as an exception.** This ADR explicitly does **not** set a precedent for skipping team ratification on subsequent amendments. The audit trail is the mechanism that makes the exception visible; team ratification at the next ceremony restores the standard posture.

## Alternatives Considered

| Alternative | Reason rejected |
|---|---|
| **Expand the §10.9 amendment (#195)** to include the five gaps | The user-authored review on #195 explicitly recommends against this: §10.9 is correctly bounded to emulator-surface concerns; mixing in instruction loader + permission defaults + commit cadence + human-only matrix would explode the PR's scope and make review unworkable. The sibling-amendment pattern is the right shape. |
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
- `docs/decision-log/ADR-005-monorepo-unification.md` — peer ADR; canonical source for the Information Routing table + monorepo layout (peer reference for §12.1; mandatory in the read order per §12.1 "No skipping existing ADRs")
- `docs/decision-log/ADR-010-issue-pr-discipline.md` — base governance ADR (§10.7 branch protection; §10.4 closing-comment discipline)
- `docs/decision-log/ADR-011-local-emulator-stack.md` — emulator stack ADR (§11.2 verify-script gate; §11.7 hard-prereq)
- `.opencode/CONTEXT-MAP.md` — context map; updated to include ADR-012 and ADR-005 in the read order

## Status

**ACCEPTED** (2026-07-29, single-decision-maker per Lane-A governance lane). Tracking issue #196; companion governance rule in #256. `matrices-build` workflow file is owed by Lane E (@Santiagodxz) before §12.6 enforcement is fully wired. Team ratification is owed at the next ceremony; until then this ADR is enforceable.