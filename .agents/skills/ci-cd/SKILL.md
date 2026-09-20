---
name: ci-cd
description: Use for GitHub Actions, pull-request verification, required checks, build artifacts, Docker image builds, release automation, signing workflows, branch protection integration, and deployment automation. Do not use for local-only build logic that does not affect automation.
---

# CI/CD

## Use When

Use this skill for:

- GitHub Actions
- PR verification
- required status checks
- build pipelines
- test pipelines
- Android artifacts
- server artifacts
- Docker image builds
- release workflows
- tags
- GitHub Releases
- signing workflows
- branch protection integration
- deployment automation

## Goals

- Make verification reproducible.
- Make CI a real merge gate.
- Keep local and remote verification aligned.
- Keep secrets protected.
- Produce useful artifacts.
- Make releases repeatable.

## CI Principle

CI must validate the repository, not merely compile one module.

The pipeline should evolve with the project.

Typical PR checks may include:

```text
repository validation
formatting
static analysis
Android Lint
unit tests
server tests
integration tests
Android builds
UI/instrumentation tests where practical
dependency/security review
