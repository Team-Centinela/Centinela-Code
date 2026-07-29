# ADR-010: Issue & PR Discipline (Traceability + Blocker Chain)

## Context

Centinela is a 4-person team carrying ~250–300h of Sprint 1 work with AI-assistance. The platform spans **code** (Spring/FastAPI/React) and **Azure** (ACA, Service Bus, PostgreSQL, Key Vault, App Insights), and the work is divided into **5 lanes** defined in [`../best-practices/05-pr-and-issue-discipline.md`](../best-practices/05-pr-and-issue-discipline.md) §"Role & Responsibility Matrix".

Two governance absences surfaced in the Sprint 0 retrospective:

1. **Cross-task blocker discipline is informal.** Issue descriptions referenced blockers; the underlying GitHub relationship was not enforced. The result was agents and humans claiming work with blockers still open. PR #120 (Spring Java, packages `com.centinela.ingestion.shared.outbox` + `com.centinela.corebackend.shared.outbox`) shipped before the Engine issues (#88 #89 #93 #94 #95 #96 #97 #102) it depended on were merged. PRs #121–#129 then arrived on `develop` while the Inbound lane (Ingestion) still owed its Outbox Publisher parity.
2. **Code ↔ Azure traceability is structural.** A Spring/Java dev changing an `application.yml` env-var that flips the Service Bus queue topology had no companion infra issue to record the change. PR #120 introduces a publisher that reaches `transactions-raw`; PR #127 (terraform bootstrap) lands the RG and state backend; the cross-pull-request cost-aggregation story is invisible in the audit trail.

Cost attribution also lacks an explicit convention. Per ASSIGNMENT.md cost ceiling, dollars must be traceable from completed epic back to dollars burned.

After the §10.9 Emulation Amendment: ADR-011 named the **emulator surface** (PostGIS + Microsoft SB Emulator + Floci-AZ + `mssql/server:2022-latest` + the three Spring Boot services on `local-emulator` profile) as an architectural first-class concern — Phase 0 / local dev / future CI all pre-validate through it. ADR-010's original text predates that framing; §10.9 reconciles the two ADRs.

## Decision

Centinela adopts the following conventions:

### 10.1 Per-issue `Blocked by:` line on every Task

Every issue filed from the `task-managed.md` template MUST carry a `Blocked by:` line listing **only closed issue numbers**. Claiming a task before its blockers close is allowed only when the blocker is an `infra`-labelled companion issue (under §10.3); in any other case the agent or human MUST refuse to claim and surface a PR-style comment. **§10.9.2** defines a Phase 0 exception to this rule.

`Blocked by:` is enforced by:

- The `task-managed.md` issue template field is mandatory (template-level).
- The PR template cross-checks at PR-open: an `azure-impact` PR whose `Companion infra issue:` field is empty fails CI.
- AI agents (`/.github/agent-preflight.md`) refuse to write code until the line is satisfied.

### 10.2 `Has azure-impact: yes/no` declaration on every task

Every task-managed issue MUST declare whether a touch on the **deployment surface** is in-scope. There are **two deployment surfaces** (§10.9.1) — local emulator (Phase 0 / local / CI) and real Azure (Phase 2 / Phase 3) — and *yes* triggers both for either:

| Surface examples | In-scope because |
|---|---|
| `application.yml`, `application-*.properties` | env var, datasource, KEDA config |
| Dockerfile, container build context | image rebuild changes deploy cost |
| `docker-compose.yml`, `docker/**` (Postgres init, SB Config, SQL Edge) | emulator surface; ADR-011 §11.2 |
| `application-local-emulator.yml` | Spring profile binding to emulator surface |
| Secret accessor class or Key Vault ref config | secret rotation path |
| Service Bus binding / topic / queue code | runtime topology swap |
| `*Environment.class`, `application.name` annotation | observable deployment change |
| Terraform `*.tf` changes affecting a service | direct IaC change |

Yes triggers the `EXPECTED DELIVERY` and `COST-ATTRIBUTION` blocks (§§10.4–10.5), with **Mode (c) — Emulator Commitment** (§10.9.3) for emulator-surface only changes and **Mode (a)+(b)** for real-Azure surface changes.

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

For **emulator-surface** changes (§10.9.1 row 2), the `EXPECTED DELIVERY` block is replaced by the §"Phase 0 Close" subsection of Pattern 07 (`docs/patterns/07-azure-impact-companion-issue.md`). The two are not interchangeable — the verify-script `18 PASS / 0 FAIL` evidence substitutes for `terraform apply log` only during Phase 0.

### 10.5 COST-ATTRIBUTION schema (in companion infra issue)

Each Azure-impacting companion issue records its spend in one of **three** modes:

**Mode (a)** — **`infrastructure/README.md` doc-row**, one entry per issue:

```
| lane-b ingestion s1 | ACA App + SB Data Sender role | $0.30–0.50 |
   | imp. | #B.A1 (companion #B.9) | start 2026-07-29 | first run $0.17 |
```

**Mode (b)** — **Azure resource tags** with the schema:

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

**Mode (c)** — **Emulator Commitment** (§10.9.3). For emulator-surface only closes, where Mode (b) is inert. Doc-row uses `$0.00–0.00`; the `centinela:*` schema is unrecorded against the resource (MCR / SB-Emulator / Floci-AZ do not support external tags) and is **inferred retrospectively** on the first `terraform apply` of Phase 2.

Either Mode (a) or Mode (b) is the floor for real-Azure changes; **(a)+(b)** is the gold standard. **Mode (c)** is the only valid mode for emulator-surface only closes.

### 10.6 AI agent preflight

`/.github/agent-preflight.md` is committed; every AI session reads it before claiming work. Three rules:

1. Verify `Blocked by:` is satisfied (only closed issues — or §10.9.2 Phase 0 exception).
2. Verify `Has azure-impact` and `Companion infra issue:` fields are present on the task being claimed.
3. Re-read the ADR(s), the pattern doc(s), and the service README of the touched file (with ADR-011 added when the touch is on the emulator surface per §10.9.1).

### 10.7 Branch protection on `develop`

Branch protection on `develop` requires **2 CI checks**:

- `link-check` (lychee) on every `.md` change.
- `matrices-build` for the Java/Python/Node matrices.

Discipline (§§10.1–10.5) is encoded in the **PR template** at PR-open time, not in `git push` time, to keep AI-velocity high, but **merge is blocked** if the template is incomplete and the `azure-impact` companion pair is unresolved. **Phase 2 `terraform apply` (#169 step 2.4) is itself gated on this branch protection plus ADR-011 §11.7.**

### 10.8 Companion infra issues — creation rules

| Trigger | Companion opens |
|---|---|
| Code PR changes env var / secret / connection string / KEDA scaler / MI role / container build / queue topology | Same sprint, paired issue, `infra` label, references code issue |
| New Azure resource | Single, dedicated, "azurerm_*..." main `infra`-labelled issue |
| Drift detected by an existing gauge | `infra`: investigation, references the original companion |
| Code PR changes `docker-compose.yml`, `docker/**`, or `application-local-emulator.yml` | Same sprint, paired issue, `infra` label + `azure-impact-companion` label, uses Mode (c) close |

### 10.9 Phase-0 / Emulator Pre-Validation

Per **ADR-011 §11.7**, ADR-010 applies the same discipline to **emulator-surface** code/PR changes as to real-Azure changes. The local emulator stack (PostGIS + Microsoft Service Bus Emulator + Floci-AZ + `mssql/server:2022-latest` + the three Spring Boot services on `local-emulator` profile) is the canonical pre-validation surface for local dev, Phase 0, and CI per ADR-011 §11.1.

#### 10.9.1 Two deployment surfaces

A `Has azure-impact: yes` declaration holds for any change that touches either surface:

| Surface | Files | Evidence format | Cost mode |
|---|---|---|---|
| **Real Azure** (Phase 2 / Phase 3) | `*.tf`, Key Vault refs, Service Bus binding, KEDA scaler, MI role assignments, container build context for ACA | `terraform plan/apply` + `az <show>` + checkov/tflint | Mode (a)+(b), gold standard |
| **Emulator** (Phase 0 / local / CI) | `docker-compose.yml`, `docker/**`, `application-local-emulator.yml`, `docker/postgres/init.sql`, `docker/servicebus/Config.json` | `scripts/verify-emulators.{ps1,sh}` reporting `18 PASS / 0 FAIL` + Testcontainers log + image-digest reference | **Mode (c) — Emulator Commitment** |

A single PR that touches both surfaces must declare **two companion issues**: one per surface. The `Blocked by:` chain may span both surfaces (e.g. `[B.9]` outbox publisher claims on the same milestone as `[B.A1]` real-Azure parcel and `[B.A1-l]` emulator-side wiring on `docker-compose.yml`).

#### 10.9.2 BLOCKED-BY Phase-0 exception

Phase 0 work pre-validates already-merged code per §21 strategic-pivot comment [`issuecomment-5084940075`](https://github.com/Team-Centinela/Centinela-Code/pull/127#issuecomment-5084940075). A Phase 0 claim may proceed even when technical-blocker issues are still open, **gated by**:

1. `scripts/verify-emulators.{ps1,sh}` reports `18 PASS / 0 FAIL` after `docker compose down -v && up -d --wait`.
2. Phase 0.3 cross-lane E2E is `BUILD SUCCESS` per #167 §0.3.

The verify-script gate substitutes for the closed-blockers rule of §10.1 during Phase 0.

#### 10.9.3 Mode (c) — Emulator Commitment

When `Has azure-impact: yes` is paired to the **emulator surface** (§10.9.1 row 2), the companion issue closes with **Mode (c)** instead of Mode (a)/(b):

```
COST-ATTRIBUTION (Phase 0 / Emulator Commitment)

  cost-row (Mode a — $0.00–0.00):
    | lane-X <service> s1 | emulator-side wiring | $0.00–0.00 |
       | imp. | #<companion> | start <yyyy-mm-dd> | verify-script <18 PASS / 0 FAIL> |

  commit-evidence (replaces Mode b — no `centinela:*` tags on MCR images):
    - image digest(s): <mcr.microsoft.com/azure-messaging/servicebus-emulator@sha256:...>
    - verify-script log: <scripts/verify-emulators.{ps1,sh} capture>
    - testcontainers run: <test class + method names>
```

Mode (c) is the only `centinela:*` convention that allows the schema to be inferred retrospectively on the **first** `terraform apply` (Phase 2 step 2.4) that converts emulator wiring into real Azure resources.

## Alternatives Considered

1. **Time-tracking tool (Clockify / Toggl)** instead of issue discipline — rejected: re-introduces a side-tool loop; would create two parallel audit trails; out of project scope.
2. **Hard pre-commit hook** blocking uncategorised `application.yml` and `*.tf` writes — rejected: would slow AI-driven PR flow in Sprint 2 when velocity matters most; lyric review friction > benefit.
3. **Microsoft Project board** mapping every code/infra cell — rejected: too heavy for 5 lanes × ~12 issues/sprint; overkill for a 21-day project.
4. **Cargo-cult on every CLAUDE/opencode agent with a custom rule file** — rejected: rules without template-visible lines create drift between agents and humans. ADR-010 makes the rules visible in the issue body.
5. **Two parallel ADRs (ADR-010a emulator, ADR-010b real-Azure)** — rejected: doubles the surface area for §10.1–10.5 rule discipline without adding semantic value; amendment-in-place preserves the "foundation ADR" status of #132 §"Will Be Superseded By".
6. **Mode (a) only — no Mode (c)** — rejected: real-Azure doc-rows require real Azure burn; Phase 0 closes have nothing to record except the verify-script receipt. Forcing `$0.00–0.00` into the real-Azure table conflates two ledgers.

## Consequences

Positive:

- One extra issue per Azure-touching code PR (visible budget tag at audit).
- Lightweight doc-row audit at sprint close for each lane (Lane A owns).
- AI session cannot claim silently; reads `agent-preflight.md`.
- Branch protection gates merge, but PR-quality enforcement is at PR-template time.
- Cost-attribution is queryable both via Azure Console and via `infrastructure/README.md`.
- **§10.9 amendment** — emulator-surface closes now have a canonical evidence format (verify-script + image digest + Testcontainers log) and a doc-row ledger cell that does not require real Azure resources to exist.
- **§10.9 amendment** — Phase 0 claims can proceed under the emulator-gate (§10.9.2), unblocking the §21 strategic-pivot pre-validation path.

Negative:

- Reviewer load grows slightly per PR (5 fields to fill); mitigated by ADR-010's rubric-style acceptance criteria.
- Cross-lane work requires two issues instead of one; mitigated by the new convention making companion pairing one-click at template time.
- **§10.9 amendment** — dual-surface PRs require two companion issues, which doubles the discipline cost on full-stack changes. Mitigated by §10.9.1's "two surfaces" guidance and the shared sprint constraint.
- **§10.9 amendment** — Mode (c) introduces a row format that no current close uses yet; first few Phase 0 closes will be rolled out under the new format with a follow-up migration if any prior real-Azure close happens without Mode (c) consideration.

## Acceptance Criteria

- [ ] `/.github/agent-preflight.md` committed.
- [ ] `docs/best-practices/05-pr-and-issue-discipline.md` committed.
- [ ] `docs/patterns/07-azure-impact-companion-issue.md` committed.
- [ ] `.github/ISSUE_TEMPLATE/task-managed.md` committed.
- [ ] `.github/ISSUE_TEMPLATE/infra-change.md` committed.
- [ ] `.github/ISSUE_TEMPLATE/ceremony.md` committed.
- [ ] Tag schema documented in `infrastructure/README.md` §"Cost guardrails".
- [ ] **§10.9 Emulation Amendment** — `infrastructure/README.md` Mode (c) doc-row convention committed; `docs/patterns/07-azure-impact-companion-issue.md` §"Phase 0 Close" committed; `.github/agent-preflight.md` Rule 3 reads ADR-011 for emulator surfaces.
- [ ] Branch protection on `develop` updated.
- [ ] Lane workstream epics (Lane A, B, C, D, E) opened and `Blocked by:` field satisfied.
- [ ] Transitional guardrail (#131) acknowledged on PR #120 and PR #121.
- [ ] **§10.9 acceptance** — PR #194 retargeted to `phase-0/0.1-validation`; ADR-011 §11.7 hard prereq gate honored in the amendment text.

## Related Documents

- **ADR-011** — Local Emulator Stack (peer ADR; §10.9 cross-references §11.5 acceptance gate and §11.7 hard prerequisite gate)
- AGENTS.md §"Always / Never" (rules binding above any AI agent)
- docs/AGENTS.md §"Coordination with code"
- docs/architecture/05-selective-extraction.md §"Per-lane ownership of Azure surface" (new subsection)
- docs/architecture/01-overview.md (diagram updated to colour-code by lane)
- docs/best-practices/05-pr-and-issue-discipline.md (full role & responsibility matrix)
- docs/patterns/07-azure-impact-companion-issue.md (worked example including §"Phase 0 Close")
- /.github/agent-preflight.md (AI-session preflight, with ADR-011 awareness in Rule 3 per §10.9)

## Status

**ACCEPTED with §10.9 Emulation Amendment** (Sprint 1, 2026-07-28) — Tracker [#132](https://github.com/Team-Centinela/Centinela-Code/issues/132) closed when PR #194 merges. Companion transitional guardrail: [#131](https://github.com/Team-Centinela/Centinela-Code/issues/131). Hard prerequisite for Phase 2 apply per [ADR-011 §11.7](https://github.com/Team-Centinela/Centinela-Code/blob/phase-0/0.1-validation/docs/decision-log/ADR-011-local-emulator-stack.md#117-adr-010-hard-prerequisite-gate). Amendment text added in commit [`2ebd216`](https://github.com/Team-Centinela/Centinela-Code/commit/2ebd2161b52e7486f48bab8ba57c6366fe1fe384); tracked under amendment issue [#195](https://github.com/Team-Centinela/Centinela-Code/issues/195).