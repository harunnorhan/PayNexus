---
name: git-workflow
description: Use for branch naming, issue linkage, commits, push behavior, pull requests, review workflow, merge strategy, branch cleanup, and repository history discipline. Use whenever a task changes or executes the Git collaboration workflow.
---

# Git Workflow

## Use When

Use this skill for:

- branch creation
- branch naming
- issue linkage
- commit planning
- Conventional Commits
- push
- Pull Requests
- review
- squash merge
- branch cleanup
- Git history discipline

## Goals

- Keep every change traceable.
- Keep main clean and releasable.
- Keep branches short-lived.
- Keep Pull Requests focused.
- Preserve readable repository history.

## Main Branch

`main` is protected.

Never implement directly on `main`.

The only exception was the one-time PNX-000 repository bootstrap.

All normal work must use a dedicated branch.

## Task Traceability

Every change must map to a PNX task.

Normal workflow:

```text
GitHub Issue
-> Branch
-> Implementation
-> Tests
-> Commit
-> Push
-> Pull Request
-> CI
-> Review
-> Squash Merge
-> Branch Cleanup
