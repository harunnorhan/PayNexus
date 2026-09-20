# Codex Task Template

## Task

PNX-XXX - <task title>

## Branch

<type>/PNX-XXX-<slug>

## Read First

- `AGENTS.md`
- linked GitHub issue
- relevant files under `docs/`
- relevant ADRs
- relevant `.agents/skills/*/SKILL.md`

## Goal

## Acceptance Criteria

- [ ] ...ß
- [ ] ...

## Constraints

- Do not modify unrelated files.
- Preserve architecture boundaries.
- Preserve public contracts unless explicitly changed by the task.
- Do not add dependencies without justification.
- Add or update tests for behavior changes.
- Do not weaken verification.

## Before Coding

1. Inspect the current repository state.
2. List affected modules/files.
3. Provide a concise file-level plan.
4. Identify required skills.
5. Identify test cases.
6. Identify architecture/security risks.

## During Implementation

- Keep changes focused.
- Prefer explicit mapping between layers.
- Do not introduce speculative abstractions.
- Do not mix cleanup with the requested behavior.

## Before Finishing

1. Run relevant formatting/static analysis.
2. Run relevant tests.
3. Run relevant build checks.
4. Review `git diff`.
5. Check documentation impact.
6. Check security/logging impact.
7. Check architecture impact.

## Final Report

Report:

- summary
- files changed
- tests
- commands executed
- results
- architecture impact
- security impact
- documentation impact
- risks
- follow-ups
