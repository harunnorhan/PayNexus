---
name: dependency-injection
description: Use for Hilt modules, constructor injection, provider/bindings, scopes, dependency composition, implementation replacement, and server-side dependency wiring. Do not use for ordinary object creation that does not involve dependency composition or lifecycle ownership.
---

# Dependency Injection

## Use When

Use this skill when a task affects:

- Hilt setup
- constructor injection
- `@Provides`
- `@Binds`
- component scopes
- application composition
- dependency replacement
- test doubles
- service wiring
- repository implementation binding
- server dependency wiring
- lifecycle-based dependency ownership

## Goals

- Make dependencies explicit.
- Make implementations replaceable.
- Keep composition at clear application boundaries.
- Align dependency lifetime with real ownership.
- Avoid service locator patterns.
- Keep domain logic independent from DI frameworks.

## Core Principle

Prefer constructor injection.

Example:

```kotlin
class ProcessPaymentUseCase(
    private val repository: PaymentRepository,
)
