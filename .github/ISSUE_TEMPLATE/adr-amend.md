---
name: ADR Amendment
description: Tracks an amendment to an existing ADR. Pairs with `adr.md` draft and supersession chain.
title: "[ADR-<NNN> amend] "
labels: ["adr", "adr-amendment", "draft"]
assignees: []
---

## ADR being amended

- ADR-NNN (file: `docs/decision-log/...`)
- Section(s) affected: §…

## Drift observed

<!-- Describe the gap between ADR text and current code/PRs/code base.
     Use one or more of: PR/Issue references, link to file, link to conversation. -->

## Reason for amendment

<!-- Why now? Why is this an amendment and not a new ADR? -->

## Acceptance Criteria

- [ ] Decision accepted at the ADR review session (e.g. #16 follow-up)
- [ ] Text in `docs/decision-log/...` updated
- [ ] Cross-doc reconciliation done: any architecture page that summarises the ADR updates the summary line
- [ ] Closing comment references the commit SHA of the doc change
- [ ] **Compensation**: any code drift relative to the *previous* ADR text is recorded below

## Code drift recorded (if any)

| Source | Previous | Current | Resolution |
|---|---|---|---|
| (file or commit) | (old ADR-text interpretation) | (actual implementation) | (keep / amend / amend-and-revert) |

## Sprint

- sprint-<N>

## Labels

`adr`, `adr-amendment`

## Refs

- ADR-NNN
- docs/AGENTS.md (rules for cross-doc reconciliation)
