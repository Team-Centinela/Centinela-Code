# ADR-004: Rule Engine — Pipeline Pattern & Deterministic Explainer

## Context

`ASSIGNMENT.md` §B mandates four heuristic rules:

| Code | Rule |
|---|---|
| FR-1 | Transaction Velocity (high transaction count in short window) |
| FR-2 | Atypical Amount (significant deviation from historical average) |
| FR-3 | Impossible Geographical Location (haversine + max-speed) |
| FR-4 | High-Risk Merchant/Category (pre-flagged) |

§B.6 also mandates that the system must save **the specific data payload that triggered each rule** (the raw evidence), not just a numerical score.

`ASSIGNMENT.md` §C explicitly forbids LLMs for explanation. The explanation must be deterministic, derived from the same `TriggeredRule` objects that produced the score.

[Historical `#5`](https://github.com/Team-Centinela/Centinela-docs/issues/5) proposed the **Strategy Pattern**. The persisted docs (`../patterns/04-pipeline-pattern.md`) propose the **Pipeline Pattern**.

The two are **semantically conflicting** and must be reconciled.

### Why the persisted docs chose Pipeline over Strategy

| Capability | Strategy | Pipeline |
|---|---|---|
| Rule execution semantics | Independent | Chained |
| Evaluation order | Irrelevant | Configurable and matters |
| Short-circuit on excessive score | Not possible | Supported |
| Cross-rule data sharing | None | Shared `EvaluationContext` |

For Centinela's four rules, **the order and intermediate sharing matters**:
1. `FR-1` defines the velocity window — running first lets `FR-2` reuse that window for an "outlier within recent activities" check.
2. `FR-3` *requires* the previous transaction of the same account; subsequent rules need to know if velocity was already flagged.
3. Short-circuiting on a single FR-3 trigger (>900 km/h implied speed) prevents needless evaluation of FR-2/FR-4 and reduces false-positive fan-out.
4. Pipeline allows future rules to consume the shared `EvaluationContext` (e.g., a future "CrossRuleAggregator" rule that uses the velocity from FR-1 to weight FR-2).

## Decision

### 4.1 Rule Engine uses a Two-Stage Pipeline

Adopt the implementation in `../patterns/04-pipeline-pattern.md`. The pipeline executes cheap rules first (FR-1 and FR-4) and only runs the expensive rules (FR-3, then FR-2 if still below threshold) when at least one cheap rule has fired:

```
Transaction
  → EvaluationContext
    → STAGE 1 (cheap, always):
        FR-1 (Velocity)  → FR-4 (High-Risk Merchant)
    → guard: any rule fired in Stage 1?
        ├── no  → record score=0, return, persist
        └── yes → continue to Stage 2
    → STAGE 2 (expensive, conditional):
        FR-3 (Impossible Geo) → FR-2 (Atypical Amount)
    → Aggregator (sum, clamp [0,100], compare to SCORE_THRESHOLD)
```

Each rule implements a `PipelineStage` interface and writes its `RuleResult` to the shared `EvaluationContext`. `RuleResult` carries the rule code, `scoreAdded`, `rawEvidence` map, and evaluation timestamp.

**Why this order (resolves #28 — pipeline ordering)**

The four rules differ markedly in evaluation cost on a B1ms PostgreSQL Flexible Server:

| Stage | Rule | Data access | Approx cost on B1ms |
|---|---|---|---|
| Stage 1 | FR-1 (Velocity) | Single indexed COUNT in `transactions` for the account, window-bounded | Low (~5–10 ms) |
| Stage 1 | FR-4 (High-Risk Merchant) | Point lookup in `flagged_merchants` by merchant_id | Low (~1–2 ms) |
| Stage 2 | FR-3 (Impossible Geo) | PostGIS `ST_Distance` join against the latest prior transaction for the account | **High (~20–80 ms)** |
| Stage 2 | FR-2 (Atypical Amount) | Aggregate AVG / STDDEV_POP over recent account history | Medium (~10–25 ms) |

FR-3 is by far the most expensive stage (PostGIS scan on top of an index with constrained IOPS). Running it last — after the chain has already failed to short-circuit — wastes the budget of every transaction that would never have produced a case anyway. By gating Stage 2 on a Stage 1 hit, FR-3 and FR-2 are only evaluated when there is already independent evidence that the transaction warrants deeper inspection.

The previous single-stage rationale ("FR-1 defines the velocity window for FR-2; FR-3 requires the previous transaction; short-circuit on FR-3 alone") was internally contradictory: those claims justify both early and late positioning of FR-3. The two-stage design reconciles them — FR-2 still benefits from FR-1's shared velocity window (FR-1 runs in Stage 1, its `velocityInWindow` payload is exposed via `EvaluationContext.sharedData`), and FR-3 still runs late *only when* anything else has fired.

**Short-circuit semantics (resolves #30 — short-circuit vs clamp)**

A single rule governs termination of either stage:

```
if (context.aggregateScore() >= SCORE_THRESHOLD) break;
```

- **`SCORE_THRESHOLD`** is the configured case-creation threshold from `system_config` (default 70 — see §4.4); it is **not** 100.
- The aggregate score is always clamped to `[0, 100]` for persistence and for the case-creation decision. Clamping happens once, at the aggregator, after the short-circuit evaluation.
- A rule that adds, say, 60 points and is the only rule to fire produces an aggregate of 60 and **does not** open a case by itself. This is a deliberate choice: the rules are evidence-level severity graders, and the configurable threshold gives analysts operator control over the false-positive / false-negative balance (§B.5). A single piece of one rule type is not, by design, sufficient to escalate.
- Short-circuit is a micro-optimization for the obvious cases (e.g., FR-4 alone firing at its maximum `score_value` already passes `SCORE_THRESHOLD`); it is not the mechanism that opens a case. The case-creation decision is `aggregateScore >= SCORE_THRESHOLD` evaluated at the end.

### 4.2 NL Explainer is deterministic, NOT LLM-based — and pinned to `rawEvidence` schema

Adopt the template engine pattern (one template per rule code) — deterministic template engine as recorded in historical [Team-Centinela/Centinela-docs#5](https://github.com/Team-Centinela/Centinela-docs/issues/5) ("no LLMs"). All templates live under `infra/explainer/templates/RuleExplanationTemplates.java`.

Rules:
- No external calls, no network, no model invocation.
- Final explanation = `cardHeader` + `concat(per-rule templated text)` + `total score footer`.
- Templates interpolate **only** from the `RuleResult.rawEvidence` and `Transaction` fields — never on free-text or environmental data, and **never** against a re-query of the database. Every value the template needs must already be present on the `RuleResult` that fired the rule.
- The implementation MUST NOT compute any new logic; it ONLY reuses evidence already captured.

**`rawEvidence` schema per rule code (resolves #29)**

To satisfy the no-LLM, no-DB-at-render-time contract, the table below pins the keys each rule MUST emit, and the explainer is allowed to consume. Implementations must reject any `RuleResult` whose payload is missing any of the listed keys for its rule code:

| Rule code | Required keys in `rawEvidence` (JSONB) |
|---|---|
| **FR-1 Velocity** | `window_seconds`, `txn_count`, `threshold`, `current_score_added` |
| **FR-2 Atypical Amount** | `historical_avg_usd`, `historical_sample_size`, `current_amount_usd`, `std_dev_usd`, `z_score`, `current_score_added` |
| **FR-3 Impossible Geo** | `last_txn_lat`, `last_txn_lon`, `last_txn_at`, `current_lat`, `current_lon`, `distance_km`, `elapsed_seconds`, `implied_speed_kmh`, `max_allowed_speed_kmh`, `current_score_added` |
| **FR-4 High-Risk Merchant** | `merchant_id`, `risk_label`, `current_score_added` |

`current_score_added` is the rule's own contribution for that transaction (it may be 0 if the rule did not fire). The explainer templates are pinned to the exact key names above — renaming a key in the implementation requires a corresponding ADR amendment and version bump in `infra/explainer/templates/RuleExplanationTemplates.java`.

The example in `ASSIGNMENT.md §B` ("normal activity ~$50,000, sudden attempt of $4,000,000") renders from `historical_avg_usd` and `current_amount_usd` on the FR-2 `RuleResult`. No database read at render time.

### 4.3 Rule Audit Telemetry — `TriggeredRule` is persisted

Aligned with §B.6: every triggered rule, with `rawEvidence` JSONB matching the §4.2 schema, is persisted alongside the transaction in `transactions.triggered_rules` (PostgreSQL `oltp` schema, JSONB column). Read access to this field is required by Reporting (per `../architecture/01-overview.md`).

### 4.4 Configurable threshold and rule parameters

Aligned with §B.5: the global `SCORE_THRESHOLD` and per-rule `score_value`, `enabled`, and `params` JSON live in PostgreSQL `rules_config` schema. Defaults seeded from IaC:

- `system_config.score_threshold` = **`70`** (case-creation threshold AND short-circuit pivot). Analysts can tune this in the Admin UI; the value is loaded by `EvaluationContextBuilder` on its refresh tick (configurable, default 60s).
- Per-rule `score_value` defaults, seeded in IaC so they are present before the rule runs:
  - FR-1 Velocity: 30
  - FR-2 Atypical Amount: 40
  - FR-3 Impossible Geo: 60
  - FR-4 High-Risk Merchant: 50

These defaults are not magic numbers in code or hardcoded in business logic — they live as data in `rules_config`. The unit of "default" here means "the seed value present in the freshly provisioned database", not "compile-time fallback". If the row is missing at runtime (corrupted tenant, failed migration), the pipeline fails closed — no case is opened — and logs a `RULE_CONFIG_MISSING` event.

**Case-creation decision is unambiguous**: `case_opens iff aggregateScore (clamped to [0,100]) >= SCORE_THRESHOLD`.

## Consequences

### Positive
- Pipeline matches the architectural intent expressed in `../patterns/04-pipeline-pattern.md`; Strategy was a historical draft proposal at [Team-Centinela/Centinela-docs#5](https://github.com/Team-Centinela/Centinela-docs/issues/5).
- Two-stage ordering puts the expensive PostGIS scan (FR-3) behind the cheap Stage-1 screen, keeping median evaluation time and B1ms IOPS pressure low (per #28).
- Short-circuit on `SCORE_THRESHOLD` (default 70) stops work the moment a case is guaranteed, and uses the same value the case-creation decision uses — no two-threshold drift (per #30).
- Shared `EvaluationContext` enables future rules without re-architecture.
- `rawEvidence` schema is pinned per FR code; the deterministic explainer can render every required sentence (`ASSIGNMENT.md §C`) without any database or model access at render time (per #29).
- Rule audit telemetry satisfies §B.6 with no extra storage layer.
- NL Explainer never has an opportunity to hallucinate — a hard requirement of §C.

### Negative
- Slightly more complex than a single-chain Pipeline (two stages + a guard rule).
- Rule ordering becomes a configuration concern that must be documented in `infrastructure/config/RulePipelineConfig.java`.
- Stage 2 only sees transactions where Stage 1 fired; a hypothetical future rule that *only* depends on FR-3's evidence (e.g., "geo anomaly without other heuristics") cannot run from Stage 1 and would need to be re-classified as Stage 1 or added behind the guard with explicit reasoning.
- Future rules need to be cautious about adding fields to `EvaluationContext.sharedData` — leakage risks tight coupling.

### Mitigation
- Validation in CI: each rule implementation must declare which context keys it reads (in a class-level annotation or unit test) so leaks can be detected.
- Stage assignment (Stage 1 vs Stage 2) and within-stage ordering are fixed at registration time and documented in `infrastructure/config/RulePipelineConfig.java`. Any change requires an ADR amendment.
- A local perf harness (`tests/.../RulePipelinePerfTest`) measures median + p99 evaluation time on B1ms-shaped data (#28 acceptance criterion) and is wired into CI as a soft gate.
- Per-rule `current_score_added ≤ score_value` is enforced in the rule unit tests (defense against accidental over-weighting that could undermine the threshold contract).

## Alternatives considered

| Alternative | Reason rejected |
|---|---|
| Strategy Pattern (rules independent) | Loses short-circuit + cross-rule sharing. Strategy alone is sufficient only when rules are truly independent, which Centinela's are not (FR-3 consumes history). |
| Chain of Responsibility | Heavy; explicit per-rule successor wiring; Pipeline expresses the same thing more cleanly with a context object. |
| Drools / business rules engine | Over-engineered, adds big dependency, non-trivial learning curve for 3 weeks. |
| ML scoring (LightGBM, etc.) | Forbidden by `ASSIGNMENT.md` §B (no ML for scoring). |

## References

- `ASSIGNMENT.md` §B, §C — rules and explainer
- `../architecture/01-overview.md` — module responsibilities
- `../patterns/04-pipeline-pattern.md` — Pipeline Pattern specification (mirror of the two-stage + short-circuit + rawEvidence contracts)
- Historical GitHub Issue [Team-Centinela/Centinela-docs#5](https://github.com/Team-Centinela/Centinela-docs/issues/5) (existing ADR draft) — superseded
- [#13](https://github.com/Team-Centinela/Centinela-Code/issues/13) ADR-004 issue tracker — Supersedes [Team-Centinela/Centinela-docs#5](https://github.com/Team-Centinela/Centinela-docs/issues/5)
- [#11](https://github.com/Team-Centinela/Centinela-Code/issues/11) ADR-002 (postgresql-only) — Postgres JSONB store for evidence
- Sprint 0 blockers resolved by this ADR: [#28](https://github.com/Team-Centinela/Centinela-Code/issues/28) pipeline ordering, [#29](https://github.com/Team-Centinela/Centinela-Code/issues/29) rawEvidence schema, [#30](https://github.com/Team-Centinela/Centinela-Code/issues/30) short-circuit vs clamp
- [#42](https://github.com/Team-Centinela/Centinela-Code/issues/42) Implementation sub-issue — Rule Engine Pipeline (FR-1..FR-4) coding work that consumes these contracts

## Status
**ACCEPTED** — Ratified during Sprint 0 ADR review (#16) after blockers #28 (pipeline ordering), #29 (rawEvidence schema), and #30 (short-circuit vs clamp semantics) were resolved. Implementation sub-issue #42 (Rule Engine Pipeline code) remains open and tracks the coding work that consumes the contracts pinned below.
