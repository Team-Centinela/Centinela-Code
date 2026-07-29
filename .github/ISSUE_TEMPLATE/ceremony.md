---
name: Ceremony (lightweight)
description: SCRUM ceremonies — standup, retro, refinement. Comment-only, no code or doc change.
title: "[ceremony] "
labels: ["ceremony"]
assignees: []
---

## Type

- [ ] standup
- [ ] retrospective
- [ ] refinement

## Sprint

- sprint-<N>

## Date

<!-- yyyy-mm-dd -->

## Standup body (if standup)

```
@<user — Lane <letter>:
- Yesterday: …
- Today: …
- Blockers: …
```

## Retrospective body (if retro)

```
What went well:
- …

What didn't:
- …

Action items (each becomes a Task-managed issue or ADR amendment):
- [ ] … (#companion issue)
```

## Refinement body (if refinement)

```
Workstream: <Lane letter + service>
Sprint: <N came from> → Sprint <N+1 going to>

Carrying-over (close-this-sprint):
- [ ] …
- [ ] …

Picked-up (open-new-sprint):
- [ ] …
- [ ] …
```

## Docs touched

- [ ] `AGENTS.md` (process update) — only if a process rule changed
- [ ] `docs/AGENTS.md` (process update) — only if documentation process changed

## Linked

- #sprint epic
- #action items
