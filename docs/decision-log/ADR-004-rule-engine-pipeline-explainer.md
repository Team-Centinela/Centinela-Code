# ADR-004: Rule Engine — Pipeline Pattern & Deterministic Explainer

## Status
**DRAFT** — Pending team review and approval.

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

[Historical `#5`](https://github.com/Team-Centinela/Centinela-docs/issues/5) proposed the **Strategy Pattern**. The persisted docs (`patterns/04-pipeline-pattern.md`) propose the **Pipeline Pattern**.

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

### 4.1 Rule Engine uses the Pipeline Pattern

Adopt the implementation in `patterns/04-pipeline-pattern.md`:

```
Transaction → EvaluationContext → FR-1 → FR-2 → FR-3 → FR-4 → AggregatedFraudScore
                                       │      │      │      │
                                       └──────┴──────┴──────┘
                                              shared state
```

Implementation rules:
- Each rule implements a `PipelineStage` interface and writes its `RuleResult` to the shared `EvaluationContext`.
- The pipeline supports short-circuit `if (aggregateScore() >= 100.0) break;`.
- Each `RuleResult` carries the rule code, score added, raw evidence map, and evaluation timestamp.
- Total score is the sum of `scoreAdded` across all triggered rules, clamped to `[0, 100]`.
- A configured threshold (`system_config.SCORE_THRESHOLD`) gates case creation.
- All rule parameters (windows, score values, thresholds) are **data** in the `rule_configs` table; no rule accepts hardcoded constants outside its compile-time.

### 4.2 NL Explainer is deterministic, NOT LLM-based

Adopt the template engine pattern (one template per rule code) — deterministic template engine as recorded in historical [Team-Centinela/Centinela-docs#5](https://github.com/Team-Centinela/Centinela-docs/issues/5) ("no LLMs"). All templates live under `infra/explainer/templates/RuleExplanationTemplates.java`.

Rules:
- No external calls, no network, no model invocation.
- Final explanation = `cardHeader` + `concat(per-rule templated text)` + `total score footer`.
- Templates interpolate only from the `RuleResult.rawEvidence` and `Transaction` fields — never on free-text or environmental data.
- The implementation MUST NOT compute any new logic; it ONLY reuses evidence already captured.

### 4.3 Rule Audit Telemetry — `TriggeredRule` is persisted

Aligned with §B.6: every triggered rule, with `rawEvidence` JSONB, is persisted alongside the transaction in `transactions.triggered_rules` (PostgreSQL `oltp` schema, JSONB column). Read access to this field is required by Reporting (per `architecture/01-overview.md`).

### 4.4 Configurable threshold and rule parameters

Aligned with §B.5: the global `SCORE_THRESHOLD` and per-rule `score_value`, `enabled`, and `params` JSON live in PostgreSQL `rules_config` schema. Admin changes take effect at the next `EvaluationContextBuilder` refresh (configurable, default 60s).

## Consequences

### Positive
- Pipeline matches the architectural intent expressed in `patterns/04-pipeline-pattern.md`; Strategy was a historical draft proposal at [Team-Centinela/Centinela-docs#5](https://github.com/Team-Centinela/Centinela-docs/issues/5).
- Short-circuit reduces evaluation cost on hot paths.
- Shared `EvaluationContext` enables future rules without re-architecture.
- Rule audit telemetry satisfies §B.6 with no extra storage layer.
- NL Explainer never has an opportunity to hallucinate — a hard requirement of §C.

### Negative
- Slightly more complex than Strategy (Pipeline needs context plumbing + ordering discipline).
- Rule ordering becomes a configuration concern that must be documented.
- Future rules need to be cautious about adding fields to `EvaluationContext.sharedData` — leakage risks tight coupling.

### Mitigation
- Validation in CI: each rule implementation must declare which context keys it reads (in a class-level annotation or unit test) so leaks can be detected.
- Rule ordering is fixed at registration time and documented in `infrastructure/config/RulePipelineConfig.java`.

## Alternatives considered

| Alternative | Reason rejected |
|---|---|
| Strategy Pattern (rules independent) | Loses short-circuit + cross-rule sharing. Strategy alone is sufficient only when rules are truly independent, which Centinela's are not (FR-3 consumes history). |
| Chain of Responsibility | Heavy; explicit per-rule successor wiring; Pipeline expresses the same thing more cleanly with a context object. |
| Drools / business rules engine | Over-engineered, adds big dependency, non-trivial learning curve for 3 weeks. |
| ML scoring (LightGBM, etc.) | Forbidden by `ASSIGNMENT.md` §B (no ML for scoring). |

## References

- `ASSIGNMENT.md` §B, §C — rules and explainer
- `architecture/01-overview.md` — module responsibilities
- `patterns/04-pipeline-pattern.md` — Pipeline Pattern specification
- Historical GitHub Issue [Team-Centinela/Centinela-docs#5](https://github.com/Team-Centinela/Centinela-docs/issues/5) (existing ADR draft) — superseded
- [#13](https://github.com/Team-Centinela/Centinela-Code/issues/13) ADR-004 issue tracker — Supersedes [Team-Centinela/Centinela-docs#5](https://github.com/Team-Centinela/Centinela-docs/issues/5)
- [#11](https://github.com/Team-Centinela/Centinela-Code/issues/11) ADR-002 (postgresql-only) — Postgres JSONB store for evidence

## Status

**DRAFT** — pending Sprint 0 review on [`gh issue list --label adr --state open`](https://github.com/Team-Centinela/Centinela-Code/issues?q=is%3Aopen+label%3Aadr). If accepted, [#13](https://github.com/Team-Centinela/Centinela-Code/issues/13) ADR-004 issue moves from *draft* label.
