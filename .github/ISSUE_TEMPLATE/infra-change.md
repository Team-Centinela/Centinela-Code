---
name: Infra Change (lane-managed, azure-impact companion)
description: Per ADR-010 §10.3–§10.5, ADR-007 §7.7. Companion issue for a code-side task that touches the deployment surface. Pairs with `task-managed.md`.
title: "[infra][<Lane>.<A*>] "
labels: ["infra-change", "azure-impact-companion"]
assignees: []
---

## Goal

<!-- Single-line: what Azure resource, environment, or wiring this lands. -->

## Lane

- **Lane:** `<letter>`
- **Owner:** `@<user>`
- **Companion in code:** **#—** (must reference one)
- **Parent epic:** #link

## Acceptance Criteria

- [ ] `terraform plan` clean
- [ ] `terraform apply` log captured in issue comment
- [ ] `az <verify command>` output captured in issue comment
- [ ] Smoke test passing
- [ ] Cost row updated in `infrastructure/README.md` §"Cost guardrails"
- [ ] All resources under this companion carry the `centinela:*` tags below
- [ ] **Documentation touched:** below filled

## EXPECTED DELIVERY (Azure side)

```
RESOURCE DIFF
  azurerm_<resource>.<logical_name>   +n/+m/d   <human>

VERIFICATION EVIDENCE
  - commit SHA of paired PR: <link>
  - terraform plan/apply log: <in comment>
  - az <show command>: <in comment>
  - smoke test ID: <in comment>
  - checkov / tflint: <0 high findings>

DRIFT GAUGE
  <metric name>  (existing/new)  DCE <source>

BREAKAGE-PATH
  failure mode → hand-back path
```

## COST-ATTRIBUTION (required)

Pick one mode (a), (b), or (a+b):

### Mode (a) — `infrastructure/README.md` doc row

```
| <lp> <service> s<N> | <resource-delta>                | $<low>-$<high> |
   | imp. | #<companion-code>                             | start <yyyy-mm-dd> | first run $<X.YY> |
```

Add this row to `infrastructure/README.md` §"Cost guardrails" table and reference this issue.

### Mode (b) — Azure resource tags

```
centinela:lp       <lane letter>          e.g. "lane-b"
centinela:epic     [<epic-id>]           e.g. "[B.0]"
centinela:issue    [<companion-id>]      e.g. "[B.A1]"
centinela:sprint   sprint-<N>
centinela:start    <yyyy-mm-dd>
centinela:close    <yyyy-mm-dd>           (filled at close)
centinela:action   create|update|delete
```

### Mode (a+b) — gold standard

Both above.

## Blocked by

<!-- Required. List closed issues only. -->
- #— (closed)

## Has azure-impact

Yes (this is the infrastructure side of an azure-impact pair).

## Documentation Touches (mandatory)

- [ ] `infrastructure/README.md` — Cost guardrails row added
- [ ] `services/<service>/README.md` — Infra surface section updated
- [ ] `docs/architecture/0X-name.md` — if applicable
- [ ] `infrastructure/REGION-QUOTA-CHECK.md` — if applicable

## Back-trace guarantee

```
Azure Cost Analysis:
  Filter   Resource Tags.centinela:lp == <lane letter>
  Group by Resource Tags.centinela:issue
  Range    centinela:start — centinela:close

OR

infrastructure/README.md doc-row grep for issue# == #<companion>
```

## References

- ADR-010 (`docs/decision-log/ADR-010-issue-pr-discipline.md`)
- ADR-007 (`docs/decision-log/ADR-007-observability-cost-telemetry.md`) §7.7
- `docs/patterns/07-azure-impact-companion-issue.md`
- `docs/best-practices/05-pr-and-issue-discipline.md`
