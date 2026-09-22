# PayNexus Component Boundaries

## Purpose

This document defines the responsibilities and dependency boundaries of the three main PayNexus components.

The goal is to prevent architectural drift as the repository grows.

## Merchant Android Application

### Owns

- Jetpack Compose user interface
- screen state
- user input
- payment initiation
- payment result presentation
- transaction presentation

### Does Not Own

- HTTP payment processing
- payment gateway communication
- server DTOs
- payment orchestration
- idempotency enforcement
- backend persistence

### Allowed Dependencies

The Merchant Application may depend on:

- payment domain abstractions
- IPC contract models
- design system modules
- merchant-specific feature modules
- shared platform utilities

### Forbidden Dependencies

The Merchant Application must not depend directly on:

- Payment Server client implementations
- Payment Service implementation modules
- server DTOs
- server persistence code

## Headless Android Payment Service

### Owns

- AIDL/Binder implementation
- payment orchestration
- service-side validation
- transaction lifecycle management
- network gateway integration
- local transaction persistence
- transport-to-domain error mapping
- payment retry safety
- idempotency coordination

### Does Not Own

- merchant-facing UI
- Compose screens
- server database schema ownership
- server-side business rules

### Allowed Dependencies

The Payment Service may depend on:

- payment domain abstractions
- IPC contract models
- network infrastructure
- local persistence infrastructure
- logging and observability modules
- shared testing utilities

### Forbidden Dependencies

The Payment Service must not depend on:

- Merchant UI modules
- Compose feature modules
- server implementation modules

## Kotlin Payment Server

### Owns

- HTTP API
- request validation
- idempotency enforcement
- transaction persistence
- deterministic payment simulation
- transaction lookup
- health endpoint
- server-side logging

### Does Not Own

- Android lifecycle
- AIDL contracts
- Compose UI
- Android-specific models

### Allowed Dependencies

The Payment Server may depend on:

- server domain modules
- server application modules
- server infrastructure modules
- shared pure Kotlin domain models where justified

### Forbidden Dependencies

The Payment Server must not depend on:

- Android framework classes
- Merchant UI modules
- Android IPC implementation classes

## Dependency Direction

The intended dependency direction is:

```text
Presentation
    |
    v
Application / Use Cases
    |
    v
Domain
    ^
    |
Infrastructure

```

## Payment Domain Foundation

`:payment:domain` owns `CurrencyCode`, `Money`, `PaymentAmount`, `PaymentId`,
`IdempotencyKey`, `DeclineReason`, `PaymentFailure`, `PaymentOutcome`,
`PaymentState`, and `IllegalPaymentTransitionException` in
`com.paynexus.payment.domain`. No demonstrated cross-domain use currently
justifies promoting these types into `:core:model` or `:core:domain`.
`:payment:contract` remains separate and gains no domain or transport models
from this foundation.

All domain models are immutable and framework-independent. `Money` stores
signed or zero `Long` minor units with explicit currency; `PaymentAmount`
requires a strictly positive quantity. TRY is initially the only supported
currency. `CurrencyCode.fromCode(code)` rejects unsupported, blank, or
noncanonical codes without normalization or fallback.

`PaymentId` and `IdempotencyKey` reject blank values and preserve caller-supplied
values exactly. They neither generate identifiers nor enforce uniqueness or
idempotency. Invalid value construction throws `IllegalArgumentException`
with fixed messages that do not expose supplied values. Constructor validation
also applies when copying a value.

The legal lifecycle transitions are:

- `Created -> Processing`
- `Processing -> Finished(Approved)`
- `Processing -> Finished(Declined(reason))`
- `Processing -> Finished(Failed(failure))`

Every other transition, including self-transitions and all transitions from
terminal states, throws `IllegalPaymentTransitionException` with source and
target states. Transitions return the next immutable state without mutating
the original. This is a domain rule, not a durable or concurrent orchestration
mechanism.

A decline is a confirmed business response; its initial reason is
`DeclineReason.UNSPECIFIED`. A technical failure uses
`PaymentFailure.PROCESSING_ERROR`. Neither contains transport exceptions or
raw error payloads. Validation and transition errors are distinct from these
terminal outcomes. Future integrations must not assume that a timeout or
connection loss proves a payment failed; uncertain remote outcomes require
an explicit design in a later task.

This foundation introduces no payment-card data, serialization, persistence,
networking, Android dependencies, retries, or runtime communication changes.

## Design System Foundation

`:design-system` owns reusable Compose theme configuration, spacing tokens,
and `PayNexusButton` in `com.paynexus.designsystem`. It has no project-module
dependencies. Merchant consumes it through an explicit production dependency.

The library must not depend on `:payment:domain`, `:payment:contract`, either
Android application, or any server module. It owns no screen state, ViewModel,
navigation, repository, IPC, network, persistence, or payment orchestration.
Features map business state to presentation outside the design system.
See the [Design System guide](../design/design-system.md) for its current APIs.

## Merchant Shell Foundation

`:apps:merchant` owns the launcher `MainActivity` in `com.paynexus.merchant`
and `MerchantApp`/`MerchantShell` in `com.paynexus.merchant.ui`. The Activity installs
`PayNexusTheme`; the root delegates to a stateless shell with resource-backed copy,
a themed surface, safe drawing insets, and local scrolling for constrained layouts.

Its only project dependency is `:design-system`. No payment feature, ViewModel,
navigation, service binding, network, or persistence is implemented. Design System
remains independent of Merchant and owns no application window/inset behavior.
The mandatory future payment path remains Merchant -> Payment Service -> Server.
