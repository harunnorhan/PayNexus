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
