# PayNexus Agent Instructions

## Purpose

You are contributing to PayNexus, a professional fintech engineering
portfolio project.

You are an implementation assistant. You are not the architectural authority.

The repository architecture, ADRs, engineering principles, task scope,
and project skills are authoritative.

## Mandatory Reading Before Any Change

Before modifying repository files:

1. Read the linked GitHub issue or task description.
2. Read `docs/project/project-charter.md`.
3. Read `docs/architecture/system-context.md`.
4. Read `docs/architecture/component-boundaries.md`.
5. Read `docs/engineering/engineering-principles.md`.
6. Read all ADRs relevant to the requested change.
7. Read the relevant files under `.agents/skills/`.

Do not implement until the applicable boundaries are understood.

## Git Rules

Never implement directly on `main`.

Every change must:

- map to a PNX task
- use a dedicated short-lived branch
- remain focused on the issue scope
- avoid unrelated refactors
- use Conventional Commits
- be submitted through a Pull Request

Do not create or modify Git history unless explicitly instructed.

Do not force push.

## Architecture Invariants

The mandatory payment path is:

Merchant Application
-> Payment Service
-> Payment Server

Never introduce direct Merchant Application -> Payment Server communication.

The Merchant Application must not depend on Payment Service implementation code.

Transport DTOs, persistence entities, IPC models, and domain models must remain
separate concerns.

Domain logic must remain framework-independent.

## Money Rules

Never use `Float` or `Double` for monetary domain values.

Money must use integer minor units and explicit currency.

## Security Rules

Never:

- introduce real cardholder data
- commit secrets
- log sensitive payment data
- add production credentials
- weaken validation at trust boundaries

Use synthetic payment data only.

## Testing Rules

Testing is part of implementation.

For every behavior change:

- identify the appropriate test level
- add or update tests
- cover relevant failure states
- prefer behavioral tests over implementation-detail tests

Never delete, disable, weaken, or skip tests solely to make verification pass.

## Quality Rules

Do not suppress lint/static-analysis findings without documented justification.

Do not weaken compiler, lint, Detekt, formatting, coverage, or CI rules
to make a task pass.

Fix the root cause.

## Dependency Rules

Do not add a dependency without a clear reason.

Before adding a dependency:

1. explain the need
2. verify existing dependencies cannot solve the problem cleanly
3. consider maintenance and security cost

## Documentation Rules

Update documentation when a change affects:

- architecture
- public contracts
- setup instructions
- testing workflow
- security assumptions
- module responsibilities

Significant architecture changes require an ADR.

## Implementation Workflow

Before coding:

1. Inspect the current repository state.
2. Identify affected modules/files.
3. Read applicable skills.
4. Produce a concise file-level plan.
5. Identify test cases and risks.
6. Confirm the requested change does not violate ADRs.

During implementation:

- make the smallest coherent change
- keep boundaries explicit
- avoid speculative abstraction
- avoid unrelated cleanup
- preserve existing public contracts unless the task requires a change

Before finishing:

1. Run relevant formatting/static-analysis commands.
2. Run relevant tests.
3. Run relevant build/compile checks.
4. Review `git diff`.
5. Check for secrets and sensitive logs.
6. Check documentation impact.
7. Report verification honestly.

## Verification Honesty

Never claim that a command, test, build, lint check, or CI job passed
unless it was actually executed and its result was observed.

If a command cannot be run, state that explicitly.

If verification is partial, state exactly what was and was not verified.

## Completion Report

At the end of a task, report:

- summary of the change
- files changed
- tests added or updated
- commands executed
- verification results
- architecture impact
- security impact
- documentation impact
- known risks
- follow-up work

## Forbidden Actions

Never:

- work directly on `main`
- bypass the Payment Service from the Merchant Application
- use Float/Double for money
- commit secrets or real payment data
- disable tests to make CI pass
- remove assertions without justification
- suppress quality rules without justification
- introduce unrelated refactors
- silently change architecture
- invent successful command results
- claim production security or PCI compliance
- introduce a dependency without justification

## Skill Usage

Use all skills relevant to the task.

At minimum:

- architecture changes -> `android-architecture`
- module/build changes -> `gradle-modularization`
- Compose work -> `compose-ui`
- reusable UI/tokens -> `design-system`
- Binder/AIDL -> `android-ipc`
- payment rules -> `payment-domain`
- server work -> `ktor-backend`
- DI wiring -> `dependency-injection`
- Room/database work -> `persistence`
- tests -> `testing`
- trust boundaries/secrets -> `security`
- logs/correlation -> `observability`
- lint/format/static analysis -> `code-quality`
- branches/commits/PR -> `git-workflow`
- GitHub Actions/releases -> `ci-cd`
- self-review -> `code-review`
- docs/ADRs -> `documentation`
