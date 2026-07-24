# ADR-010: Issue & PR Discipline (Traceability + Blocker Chain)

## Status

**ACCEPTED** (Sprint 1, 2026-07-25) — Tracker [#132](https://github.com/Team-Centinela/Centinela-Code/issues/132). Companion transitional guardrail issue: [#131](https://github.com/Team-Centinela/Centinela-Code/issues/131).

## Context

Centinela is a 4-person team carrying ~250–300h of Sprint 1 work with AI-assistance. The platform spans **code** (Spring/FastAPI/React) and **Azure** (ACA, Service Bus, PostgreSQL, Key Vault, App Insights), and the work is divided into **5 lanes** defined in [`../best-practices/05-pr-and-issue-discipline.md`](../best-practices/05-pr-and-issue-discipline.md) §"Role & Responsibility Matrix".

Two governance absences surfaced in the Sprint 0 retrospective:

1. **Cross-task blocker discipline is informal.** Issue descriptions referenced blockers; the underlying GitHub relationship was not enforced. The result was agents and humans claiming work with blockers still open. PR #120 (Spring Java, packages `com.centinela.ingestion.shared.outbox` + `com.centinela.corebackend.shared.outbox`) shipped before the Engine issues (#88 #89 #93 #94 #95 #96 #97 #102) it depended on were merged. PRs #121–#129 then arrived on `develop` while the Inbound lane (Ingestion) still owed its Outbox Publisher parity.
2. **Code ↔ Azure traceability is structural.** A Spring/Java dev changing an `application.yml` env-var that flips the Service Bus queue topology had no companion infra issue to record the change. PR #120 introduces a publisher that reaches `transactions-raw`; PR #127 (terraform bootstrap) lands the RG and state backend; the cross-pull-request cost-aggregation story is invisible in the audit trail.

Cost attribution also lacks an explicit convention. Per ASSIGNMENT.md cost ceiling, dollars must be traceable from completed epic back to dollars burned.

## Decision

Centinela adopts the following conventions:

### 10.1 Per-issue `Blocked by:` line on every Task

Every issue filed from the `task-managed.md` template MUST carry a `Blocked by:` line listing **only closed issue numbers**. Claiming a task before its blockers close is allowed only when the blocker is an `infra`-labelled companion issue (under §10.3); in any other case the agent or human MUST refuse to claim and surface a PR-style comment.

`Blocked by:` is enforced by:

- The `task-managed.md` issue template field is mandatory (template-level).
- The PR template cross-checks at PR-open: an `azure-impact` PR whose `Companion infra issue:` field is empty fails CI.
- AI agents (`/.github/agent-preflight.md`) refuse to write code until the line is satisfied.

### 10.2 `Has azure-impact: yes/no` declaration on every task

Every task-managed issue MUST declare whether a touch on the **deployment surface** is in-scope:

| Surface examples | In-scope because |
|---|---|
| `application.yml`, `application-*.properties` | env var, datasource, KEDA config |
| Dockerfile, container build context | image rebuild changes deploy cost |
| Secret accessor class or Key Vault ref config | secret rotation path |
| Service Bus binding / topic / queue code | runtime topology swap |
| `*Environment.class`, `application.name` annotation | observable deployment change |
| Terraform `*.tf` changes affecting a service | direct IaC change |

Yes triggers the `EXPECTED DELIVERY` and `COST-ATTRIBUTION` blocks (§§10.4–10.5).

### 10.3 Companion infra issue convention

Codeside tasks with `Has azure-impact: yes` MUST resolve in the same milestone as a paired `infra`-labelled companion issue. Both issues share sprint; one milestone each. The companion is filed via `infra-change.md` from the project backlog.

### 10.4 EXPECTED DELIVERY block (in companion infra issue)

```
EXPECTED DELIVERY (Azure side)

  RESOURCE DIFF
    <provider>.<resource_type>.<logical_name>   +n/+m/d
    Example:
      azurerm_container_app.ingestion              +12/+0/0  ACA App identity=SystemAssigned
      azurerm_role_assignment.ingestion_sb_sender  +1/+0/0   sb_data_sender on transactions-raw
      azurerm_container_app.ingestion.secret       +1/+0/0   svcbus-conn-str (KV ref)

  VERIFICATION EVIDENCE
    - terraform plan output → in PR thread
    - terraform apply log   → in issue comment
    - az <verify command> output
    - smoke test ID + commit SHA of code PR
    - checkov / tflint policy output

  DRIFT GAUGE
    Metric name + DCE source
    Example:
      azure.ingestion.aca_replica_count         (existing ADR-007 §7.7)
      azure.ingestion.sb_outbound_ops_count     (NEW per companion issue)

  BREAKAGE-PATH
    - terraform plan / apply fails     → Lane E (platform) hand-back path
    - role assignment not propagated   → re-run `az role assignment create …`
    - cost spike > 20% of lane ceiling → companion issue is source-of-record
```

### 10.5 COST-ATTRIBUTION schema (in companion infra issue)

Each Azure-impacting companion issue records its spend in one of two modes:

Mode (a) — **`infrastructure/README.md` doc-row**, one entry per issue:

```
| lane-b ingestion s1 | ACA App + SB Data Sender role | $0.30–0.50 |
   | imp. | #B.A1 (companion #B.9) | start 2026-07-29 | first run $0.17 |
```

Mode (b) — **Azure resource tags** with the schema:

```
centinela:lp       = "<lane letter>"         e.g. "lane-b"
centinela:epic     = "[X.0]"                  the lane's sprint epic
centinela:issue    = "<issue number>"         the companion issue, e.g. "[B.A1]"
centinela:sprint   = "<sprint-N>"
centinela:start    = "<yyyy-mm-dd>"
centinela:close    = "<yyyy-mm-dd, filled at close>"
centinela:action   = "create|update|delete"
```

Back-trace from Azure Cost Analysis:

```
Filter: Resource Tags.centinela:lp == <lane letter>
Group by: Resource Tags.centinela:issue
Date range: centinela:start — centinela:close
```

Either mode is the floor; **(a)+(b)** is the gold standard.

### 10.6 AI agent preflight

`/.github/agent-preflight.md` is committed; every AI session reads it before claiming work. Three rules:

1. Verify `Blocked by:` is satisfied (only closed issues).
2. Verify `Has azure-impact` and `Companion infra issue:` fields are present on the task being claimed.
3. Re-read the ADR(s), the pattern doc(s), and the service README of the touched file.

### 10.7 Branch protection on `develop`

Branch protection on `develop` requires **2 CI checks**:

- `link-check` (lychee) on every `.md` change.
- `matrices-build` for the Java/Python/Node matrices.

Discipline (§§10.1–10.5) is encoded in the **PR template** at PR-open time, not in `git push` time, to keep AI-velocity high, but **merge is blocked** if the template is incomplete and the `azure-impact` companion pair is unresolved.

### 10.8 Companion infra issues — creation rules

| Trigger | Companion opens |
|---|---|
| Code PR changes env var / secret / connection string / KEDA scaler / MI role / container build / queue topology | Same sprint, paired issue, `infra` label, references code issue |
| New Azure resource | Single, dedicated, "azurerm_*..." main `infra`-labelled issue |
| Drift detected by an existing gauge | `infra`: investigation, references the original companion |

## Alternatives Considered

1. **Time-tracking tool (Clockify / Toggl)** instead of issue discipline — rejected: re-introduces a side-tool loop; would create two parallel audit trails; out of project scope.
2. **Hard pre-commit hook** blocking uncategorised `application.yml` and `*.tf` writes — rejected: would slow AI-driven PR flow in Sprint 2 when velocity matters most; lyric review friction > benefit.
3. **Microsoft Project board** mapping every code/infra cell — rejected: too heavy for 5 lanes × ~12 issues/sprint; overkill for a 21-day project.
4. **Cargo-cult on every CLAUDE/opencode agent with a custom rule file** — rejected: rules without template-visible lines create drift between agents and humans. ADR-010 makes the rules visible in the issue body.

## Consequences

Positive:

- One extra issue per Azure-touching code PR (visible budget tag at audit).
- Lightweight doc-row audit at sprint close for each lane (Lane A owns).
- AI session cannot claim silently; reads `agent-preflight.md`.
- Branch protection gates merge, but PR-quality enforcement is at PR-template time.
- Cost-attribution is queryable both via Azure Console and via `infrastructure/README.md`.

Negative:

- Reviewer load grows slightly per PR (5 fields to fill); mitigated by ADR-010's rubric-style acceptance criteria.
- Cross-lane work requires two issues instead of one; mitigated by the new convention making companion pairing one-click at template time.

## Acceptance Criteria

- [ ] `/.github/agent-preflight.md` committed.
- [ ] `docs/best-practices/05-pr-and-issue-discipline.md` committed.
- [ ] `docs/patterns/07-azure-impact-companion-issue.md` committed.
- [ ] `.github/ISSUE_TEMPLATE/task-managed.md` committed.
- [ ] `.github/ISSUE_TEMPLATE/infra-change.md` committed.
- [ ] `.github/ISSUE_TEMPLATE/ceremony.md` committed.
- [ ] Tag schema documented in `infrastructure/README.md` §"Cost guardrails".
- [ ] Branch protection on `develop` updated.
- [ ] Lane workstream epics (Lane A, B, C, D, E) opened and `Blocked by:` field satisfied.
- [ ] Transitional guardrail (#131) acknowledged on PR #120 and PR #121.

## Related Documents

- AGENTS.md §"Always / Never" (rules binding above any AI agent)
- docs/AGENTS.md §"Coordination with code"
- docs/architecture/05-selective-extraction.md §"Per-lane ownership of Azure surface" (new subsection)
- docs/architecture/01-overview.md (diagram updated to colour-code by lane)
- docs/best-practices/05-pr-and-issue-discipline.md (full role & responsibility matrix)
- docs/patterns/07-azure-impact-companion-issue.md (worked example)
- /.github/agent-preflight.md (AI-session preflight)
