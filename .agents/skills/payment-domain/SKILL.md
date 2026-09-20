---
name: payment-domain
description: Use for monetary values, payment requests, payment results, transaction states, idempotency, payment validation, retries, domain errors, and core payment business rules. Do not use for UI-only styling or infrastructure-only changes that do not affect payment semantics.
---

# Payment Domain

## Use When

Use this skill when a task affects:

- Money
- Currency
- PaymentRequest
- PaymentResult
- payment validation
- payment state transitions
- transaction identifiers
- idempotency
- duplicate prevention
- payment retries
- decline outcomes
- technical failures
- domain errors
- payment lifecycle rules
- domain-level invariants

## Goals

- Keep payment rules exact and deterministic.
- Keep financial values safe.
- Keep payment state transitions explicit.
- Prevent duplicate financial operations.
- Keep domain logic independent from Android, network, persistence, and server frameworks.
- Make failure behavior testable and understandable.

## Domain Independence

Payment domain code must not depend on:

- Android framework classes
- Jetpack Compose
- Room
- Retrofit
- OkHttp
- Ktor client
- Ktor server
- Binder implementation details
- database entities
- HTTP DTOs
- UI models

Domain logic should be testable using plain Kotlin.

## Money Representation

All monetary values must use integer minor units.

Examples:

```text
TRY 10.00 -> 1000
USD 12.50 -> 1250
EUR 99.99 -> 9999
