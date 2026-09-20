---
name: android-architecture
description: Use for Android architecture, module boundaries, Clean Architecture, dependency direction, shared abstractions, application boundaries, and structural refactors. Do not use for isolated UI styling or backend-only changes.
---

# Android Architecture

## Use When

Use this skill when a task affects:

- Android module boundaries
- Clean Architecture
- dependency direction
- application boundaries
- shared abstractions
- Android layer responsibilities
- structural refactors
- cross-module dependencies

## Goals

- Keep domain logic framework-independent.
- Preserve explicit Merchant Application and Payment Service boundaries.
- Prevent infrastructure concerns from leaking into business logic.
- Keep dependencies directional, intentional, and reviewable.
- Preserve independent deployability of runtime components.

## Architectural Invariants

The mandatory payment path is:

```text
Merchant Application
-> Payment Service
-> Payment Server
