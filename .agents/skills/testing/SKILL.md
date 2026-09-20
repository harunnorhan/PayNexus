---
name: testing
description: Use for unit tests, ViewModel tests, repository tests, server tests, integration tests, IPC instrumentation tests, Compose UI tests, screenshot tests, end-to-end tests, test doubles, and test infrastructure. Use whenever behavior changes.
---

# Testing

## Use When

Use this skill for:

- unit tests
- ViewModel/state tests
- repository tests
- Flow tests
- Ktor tests
- integration tests
- IPC instrumentation tests
- Compose UI tests
- screenshot/golden tests
- end-to-end tests
- test fixtures
- fakes
- test infrastructure
- coverage-related work

## Goals

- Make behavior changes verifiable.
- Prefer deterministic tests.
- Cover important failure paths.
- Keep tests focused on observable behavior.
- Avoid tests tightly coupled to implementation details.

## Core Rule

Testing is part of implementation.

A behavior change is not complete until the appropriate automated tests exist.

## Test Pyramid

Choose the lowest effective test level.

Prefer:

```text
Domain Unit Tests
        |
        v
Application / ViewModel Tests
        |
        v
Repository / Integration Tests
        |
        v
IPC / UI / Server Integration Tests
        |
        v
End-to-End Tests
