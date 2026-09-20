# PayNexus Codex Governance

This directory contains project-local instructions for Codex-assisted
development.

The repository root `AGENTS.md` defines mandatory global rules.

The `skills/` directory contains focused engineering procedures that must be
read when a task touches the relevant area.

## Operating Model

Codex work must follow:

Issue
-> Branch
-> Read governance
-> Plan
-> Implement
-> Test
-> Self-review
-> Commit
-> Push
-> Pull Request
-> CI
-> Review
-> Squash Merge

## Rule Precedence

When instructions conflict, use the following precedence:

1. Explicit task acceptance criteria
2. Root `AGENTS.md`
3. Architecture Decision Records
4. Architecture/component documentation
5. Relevant project skills
6. Existing implementation patterns

If a conflict remains, stop and report it instead of guessing.

## Skill Selection

Use every skill relevant to the requested change.

A task may require multiple skills.

Examples:

- Compose payment screen:
  `compose-ui`, `design-system`, `payment-domain`, `testing`, `code-review`

- AIDL payment contract:
  `android-ipc`, `payment-domain`, `security`, `testing`, `code-review`

- Ktor payment endpoint:
  `ktor-backend`, `payment-domain`, `security`, `observability`, `testing`

- GitHub Actions:
  `ci-cd`, `code-quality`, `testing`, `git-workflow`

## Completion Standard

A task is not complete because code was generated.

A task is complete only when:

- acceptance criteria are satisfied
- architecture rules are preserved
- relevant tests exist
- relevant verification has been executed
- documentation impact is handled
- self-review is complete
