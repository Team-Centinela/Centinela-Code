---
name: Task (lane-managed)
description: Per ADR-010. A lane-managed task with explicit blockers, Azure-impact declaration, and documentation touch points.
title: "[<Lane>.<N>] "
labels: ["task", "lane-managed"]
assignees: []
---

## Goal

<!-- One-line outcome. The branch name should match this. -->

## Lane

<!-- Lane letter and owner. Examples: "B — Ingestion Service — @3105jero" -->
- **Lane:**
- **Owner:**
- **Parent epic:** #link

## Acceptance Criteria

- [ ] …
- [ ] …
- [ ] **Documentation touched:** `path/to/file.md` — one-line summary

## Documentation Touches (mandatory)

<!-- REQUIRED: list every documentation file this task lands. -->
- [ ] `<file path>` — `<change summary>`
- [ ] `<file path>` — `<change summary>`

## ADRs (required)

<!-- REQUIRED: link at least one ADR. If this task is purely housekeeping,
     link the parent epic / sprint tracker. -->
- ADR-NNN (`docs/decision-log/...`)

## Blocked by

<!-- List ONLY closed issue numbers, OR explicitly state "no blockers".
     Pattern disqualifies outdated references — open blockers cannot appear here. -->
- #— (closed)
- #— (closed)

## Has azure-impact (required)

<!-- Pick exactly one. Yes triggers §Companion infra issue and the EXPECTED DELIVERY block. -->
- [ ] **No**
- [ ] **Yes** (then §Companion infra issue and §EXPECTED DELIVERY below become mandatory)

### If yes — EXPECTED DELIVERY (mirrored in companion)

```
RESOURCE DIFF
  <provider>.<resource_type>.<logical_name>   +n/+m/d   <human>

VERIFICATION EVIDENCE
  - commit SHA: <link>
  - terraform plan/apply log: <link>
  - az <verify command>: <output>
  - smoke test: <id>

DRIFT GAUGE
  <metric name>   <existing or new>    DCE <source>

BREAKAGE-PATH
  failure mode → hand-back path
```

## Companion infra issue (required if azure-impact: yes)

- **#—** (paired companion issue, opened in **same sprint**)

## Does / Does Not (scope fences)

### Does
- …

### Does Not
- … (Link tasks it could be confused with; for example, "out-of-scope here: B.5 Idempotency-Key filter")

## Rubric (auto-grade hint)

<!-- What makes the AI review / Santiago's review fire green lights. -->
- …
- `mvn verify -pl <service>` passes
- ≥ N unit tests + ≥ 1 integration test
- Documented cross-refs intact

## References

- docs/architecture/<file.md>
- docs/patterns/<file.md>
- docs/decision-log/ADR-N-<name>.md
- `services/<service>/README.md`
