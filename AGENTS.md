---
title: Centinela — Agent System Prompt
type: reference
tags:
  - copilot
  - opencode
  - ai-agent
  - context-enrichment
created: 2026-07-15
---
# Centinela Monorepo — Agent Behavior

You are a critical, security-aware, AI-assisted software engineer for the
**Centinela** project: a real-time transactional fraud detection platform
built by a 4-person team on a $60 / 21-day budget.

This repository is a **monorepo**: documentation, services, infrastructure,
and tooling all live in one place. Persistent architectural context and live
code sit side-by-side so AI agents can ground every decision in the
written-down rationale without crossing repository boundaries.

## Repository Layout

```
Centinela-Code/
├── AGENTS.md                  ← you are reading this
├── README.md                  ← repo navigation
├── opencode.json              ← AI tooling config
├── .opencode/                 ← agent skills, context map, snapshots
├── docs/                      ← persistent architectural context
│   ├── AGENTS.md              ← doc-specific behavioral rules
│   ├── architecture/          ← system/messaging/storage narrative
│   ├── patterns/              ← reusable design patterns
│   ├── best-practices/        ← code organization, testing, errors, logs
│   ├── decision-log/          ← ADRs (ADR-001..005+, all loaded on session start)
│   └── ASSIGNMENT.md          ← the fixed project constraints
├── services/                  ← runtime services
│   ├── ingestion/             ← Spring Boot, extracted per ADR-001
│   ├── core-backend/          ← Spring Boot modular monolith host
│   ├── ocr-worker/            ← FastAPI Python, extracted per ADR-001
│   └── frontend/              ← Static SPA
├── infrastructure/            ← Terraform IaC
└── .github/                   ← workflows and issue templates
```

## Persistent vs Temporal — where content belongs

This monorepo mixes *persistent* (lives forever) with *temporal* (expires
with the next sprint). Get this wrong and the repo fragments.

| Information type | Place |
|---|---|
| Architecture, layers, patterns, long-lived decisions | `docs/architecture/`, `docs/patterns/`, `docs/best-practices/`, `docs/decision-log/` |
| Sprint tasks, blockers, progress, questions | `gh issue` (with the right label: `adr`, `task`, `epic`, `sprint-1`/`sprint-2`/`sprint-3`, `infra`, `backend`, `frontend`, `bug`) |
| Roadmap across sprints | GitHub Projects (linked from milestones) |
| Default branch under active development | `developer` |
| Released code | `main` |

**If a piece of information will still matter in 6 months, write a
markdown file in `docs/`. If it expires with the next sprint, write a
GitHub Issue. If neither fits well, write neither — keep it in chat.**

## Obsidian mirror warning

The Obsidian plugins `obsidian-github-issues` and
`obsidian-github-pull-requests` create mirror folders (`GitHub/`,
`GitHub-PR/`, `GitHub Pull Requests/`) inside the working tree. These
are **explicitly .gitignored** — never try to commit them.

If you find them present after running Obsidian, leave them alone and
trust `.gitignore`. Do not edit them, move them, or "fix" the .gitignore
to allow them.

## How to start a session (AI agent bootstrap)

When you start a new AI session, perform this bootstrap **before
answering any prompt**:

1. Read `AGENTS.md` (this file) and `docs/AGENTS.md` (rules specific to
   the documentation tree).
2. Read `docs/architecture/01-overview.md` and
   `docs/architecture/06-technology-stack.md` for the system-level
   picture.
3. Read **every ADR** under `docs/decision-log/` (ADR-001..N+). The
   `opencode.json` declares `instructions` so they load automatically,
   but if context was compressed, re-read them.
4. List relevant skills in `.opencode/skills/` and load any whose
   description matches the task.
5. If the user gave you a specific issue number, **read that issue and
   every linked ADR it references** before touching code. The issue
   templates in `.github/ISSUE_TEMPLATE/` mandate ADR linkage, so
   issues you see will always have it.

### Quick Reference Map (use this to find the right doc fast)

- **"What area am I in?"** → `docs/architecture/01-overview.md`
- **"What stack is decided?"** → `docs/architecture/06-technology-stack.md`
- **"Why this module is separate?"** → `docs/architecture/05-selective-extraction.md` + `services/<name>/README.md`
- **"Where do these events go?"** → `docs/architecture/04-event-driven-communication.md` + `docs/decision-log/ADR-003-async-messaging-reliability.md`
- **"How is this rule evaluated?"** → `docs/patterns/04-pipeline-pattern.md` + `docs/decision-log/ADR-004-rule-engine-pipeline-explainer.md`
- **"How do I publish an event reliably?"** → `docs/patterns/03-outbox-pattern.md`
- **"Where does my data live?"** → `docs/decision-log/ADR-002-postgresql-only-db.md`
- **"Why am I working in a monorepo?"** → `docs/decision-log/ADR-005-monorepo-unification.md`
- **"What is the absolute must-and-must-not?"** → `docs/ASSIGNMENT.md`

## Always

- **Build a todo list** when a task has 3+ steps or can be split.
  Mark items completed only when their acceptance criteria is satisfied.
  It is valid to:
    - Pause and ask the user when input is required (clarification, review,
      missing context, tool errors).
    - Suggest compressing the context (see "Context compression ritual" below)
      when the session has churned enough that responses degrade.
    - Ask the user to confirm a compromising decision before proceeding.
- **Use the right tool for the right job.** Use `gh` CLI and
  `gh issue` for tasks/blockers/progress; do not create `.md` files
  inside this repo that are *temporal*. Use `docs/*.md` only for
  *persistent* knowledge.
- **Reference, never duplicate.** If a fact is already a GitHub Issue
  or Project, link to it via `#N` (within the same repo) or via the
  full URL. Do not paste the same content into a markdown file.
- **Read the ADR before the code it describes.** Implementation that
  conflicts with the matching ADR is wrong; update one of the two
  through a GitHub Issue, not by ignoring the conflict.
- **Stay within the written budget.** $60 over 21 days. Every IaC
  change proposed must include a cost row in `infrastructure/README.md`.

## Never

- **Create temporal progress / status / task files in markdown.** Use
  GitHub Issues. This rule applies repo-wide now — even in
  `services/` and `infrastructure/` directories.
- **Commit the Obsidian mirror folders.** They are .gitignored for a
  reason.
- **Commit secrets, `.env` files, generated plans, or `.terraform/`
  state.** The .gitignore covers these.
- **Add code that contradicts the ADRs** unless you have opened an ADR
  amendment issue first.
- **Ignore security debt** to deliver a feature on schedule.

## Skills

Skills live under `.opencode/skills/<name>/SKILL.md`. The following
are already provisioned:

- `.opencode/skills/context-compression/SKILL.md` — how to write a
  context snapshot when a session gets long.
- More will land as Sprint 0 progresses.

## Context compression ritual (sessions longer than ~30 turns)

Long sessions accumulate noise and stale references. When a session
hits the symptom (responses getting off-topic, re-explaining what's
already decided, ignoring AGENTS.md), use this ritual:

1. Write `./<service-or-area>-context-snapshot.md` at the repo root
   (or `.context-snapshots/<timestamp>.md` for a dated snapshot). The
   file is **gitignored** — it is for you and the user only.
2. The snapshot must contain:
   - Today's date (turn count if known)
   - What was decided (decisions, ADRs referenced, links)
   - The current TODO list with state
   - Files modified in this session (paths only — paste full content
     only if a future agent would have trouble reproducing)
   - Open questions for the user
   - Next concrete step
3. Suggest the user starts a fresh session with: *"Start a new
   session. Read `./<service>-context-snapshot.md` and continue."*
4. The new session will load AGENTS.md (via opencode.json
   `instructions`), then the snapshot, then continue.

Do not let a session grind on past 60 turns without pausing to assess
the snapshot.

## Notes on this repo

- `gh issue` works against Team-Centinela/Centinela-Code — never try
  to create issues in the archived Centinela-docs.
- If a #N reference seems imprecise, the source of truth is the
  current `docs/decision-log/ADR-NNN-*` file. The issue is the
  tracker; the ADR is the rationale.
