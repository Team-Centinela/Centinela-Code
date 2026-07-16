---
name: context-compression
description: Use ONLY when a long opencode session has accumulated noise and the model is regressing — losing track of decisions, repeating earlier statements, or ignoring AGENTS.md. Triggers when a session passes ~30 turns OR the user says "compress" / "snapshot". Writes a context snapshot file (gitignored) and tells the user how to start the next session.
---

# Skill: Context Compression

When an opencode session grows too long to keep the model grounded,
follow this ritual exactly.

## When to invoke

Use this skill **as soon as any of these signals appear**, not later:

- Session has passed 30 turns and you can no longer recall an earlier
  decision without re-reading the file.
- User says "compress", "snapshot", "the context is dirty", or asks
  you to prepare a session handoff.
- You start contradicting yourself across turns.
- You find yourself re-asking a question whose answer is already in
  the repo.
- The model begins improvising on facts that the ADR/pattern docs
  already define.

If in doubt, snapshot earlier rather than later. A 30-turn session
costs the user less than a 60-turn session.

## What to write

Create a markdown file at the repository working directory using the
following structure. **Use the repo's `.gitignore`-friendly location**
(`.context-snapshots/<YYYYMMDD-HHMM>-<topic>.md`), since this path is
gitignored by `infrastructure/*.gitignore*`-style entries and the
root `.gitignore`.

```markdown
---
title: Context Snapshot — <topic>
captured: 2026-07-15 18:42 local
session: <anomaly session id if available>
working-branch: developer
sprint: sprint-1
---
# Context Snapshot — <topic>

## State of the work

- **Goal**: <short statement, ≤ 1 sentence>
- **Current TODO state**:
  - [x] Item 1 — done
  - [ ] Item 2 — in progress (reason: waiting on user approval of X)
  - [ ] Item 3 — blocked
- **Acceptance criteria remaining**: copy from the original task
  description.

## Decisions taken (with file references)

- Decision A — see `docs/decision-log/ADR-NNN-name.md#section`
- Picked option X because of Y (link the ADR).

## Files modified or created this session

- `path/to/file.java` — what changed (1 line each)
- `path/to/other.md` — what changed

## Open questions

- <question 1 — needs user input>
- <question 2 — needs user input>

## Next concrete step

The single next thing the next session should do, in imperative form.

## Prompt for the next session

A 3- to 5-line prompt the user can paste into the new session:

```
Continue from .context-snapshots/<file>.md. Read AGENTS.md first,
then this snapshot, then docs/architecture/01-overview.md. Resume
work on <file or task>, starting from the open question above.
```
```

## What NOT to do

- Do not put the snapshot in `docs/`. It is temporal.
- Do not paste the entire session log. Summarize.
- Do not invent links. Link to real files in the repo.
- Do not assume the next model will read this snapshot without being
  asked.

## Communicating the snapshot to the user

After writing the file, output a one-paragraph message in chat:
- Path of the snapshot.
- Recap of the next concrete step.
- Suggested prompt for the user to start the next session.
- Ask if the user wants the session to continue here too (often
  they will, in parallel).

## Anti-pattern: compressing without a snapshot

Do not "summarize" by paraphrasing in chat alone. Compressed
chat-only summaries are re-derived from the original noisy context
the next session loads. Snapshot to disk. Always.
