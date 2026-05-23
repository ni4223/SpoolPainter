# SpoolPainter — Claude Code Entry Point

This project uses **AI-DLC** (AI Development Lifecycle) as its development
methodology. The AIDLC rules live under `.kiro/` and are the source of truth
for *how* we build features here.

## Always-loaded steering (read at session start)
- @.kiro/steering/product.md — what SpoolPainter is and who it's for
- @.kiro/steering/tech.md — stack, build, conventions
- @.kiro/steering/structure.md — where files go
- @.kiro/steering/aws-aidlc-rules/core-workflow.md — the AIDLC workflow you
  drive when the user says "Using AI-DLC, ..."

## On-demand: AIDLC stage details
When in an AIDLC phase, load the matching detail file from
`.kiro/aws-aidlc-rule-details/`:
- `inception/` — requirements, user stories, workflow plan, application
  design, units generation
- `construction/` — per-unit loop, build & test
- `operations/` — observability, learnings
- `common/` — shared rules (audit log, state file, approval gates)
- `extensions/` — optional layered rules (e.g., security baseline)

## How to start an AIDLC session
The user types something like:
> Using AI-DLC, I want to build &lt;feature&gt;.

When they do:
1. Read `core-workflow.md` end to end before responding.
2. Detect workspace state (brownfield — existing code under `app/`).
3. Walk through Inception → Construction → Operations stages, asking the
   structured clarifying questions defined in the rule-details files.
4. Persist all artifacts under `aidlc-docs/` (state, audit log,
   requirements, designs, plans, units). Never inline AIDLC artifacts into
   the app source tree.
5. Wait for explicit user approval at each stage gate before proceeding.

## Project status
- **v1.x ships today.** Same package (`com.spoolpainter.app`), versionCode
  8, versionName 1.7.
- **v2 is a planned rewrite** — in-place update, no migration. Use the
  `debug` build variant (`com.spoolpainter.app.debug`) to test alongside
  the prod app during development.

## Don't
- Don't edit files under `.kiro/steering/aws-aidlc-rules/` or
  `.kiro/aws-aidlc-rule-details/` — they're vendored from
  awslabs/aidlc-workflows.
- Don't write app code into `aidlc-docs/`.
- Don't change the package id (`com.spoolpainter.app`) — that's an
  irreversible Play Store decision, already made.
