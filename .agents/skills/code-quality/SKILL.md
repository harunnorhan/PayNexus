---
name: code-quality
description: Use for formatting, ktlint, Spotless, Detekt, Android Lint, compiler warnings, coverage configuration, quality baselines, and static-analysis rule changes. Use whenever repository quality gates or code-health rules are modified.
---

# Code Quality

## Use When

Use this skill for:

- formatting
- ktlint
- Spotless
- Detekt
- Android Lint
- compiler warnings
- code coverage
- quality baselines
- suppression rules
- static analysis
- CI quality gates
- code-health cleanup

## Goals

- Keep the repository consistently formatted.
- Keep static-analysis debt near zero.
- Treat warnings intentionally.
- Make quality checks reproducible locally and in CI.
- Prevent quality tools from becoming cosmetic.

## Formatting

Use one canonical formatting workflow.

Prefer a repository-level formatter such as:

```text
Spotless
