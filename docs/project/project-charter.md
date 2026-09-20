# PayNexus Project Charter

## Purpose

PayNexus is a professional fintech engineering portfolio project that simulates a modular payment terminal architecture.

The project is designed to demonstrate production-minded Android engineering practices through a deliberately small payment domain.

The system consists of three independently running components:

1. Merchant Android Application
2. Headless Android Payment Service
3. Kotlin Payment Server

## Product Goal

The goal of PayNexus is to model a realistic payment flow in which a merchant-facing Android application delegates payment processing to a separate on-device payment service, which then communicates with a remote payment server.

The project prioritizes engineering quality over feature quantity.

## Primary Engineering Goals

PayNexus should demonstrate:

- Modular Android architecture
- Clean Architecture principles
- Dependency inversion
- Cross-application Android IPC
- Kotlin Coroutines and Flow
- Jetpack Compose
- Dependency Injection
- Local persistence
- HTTP networking
- Payment transaction state management
- Idempotency
- Error handling
- Responsive UI
- Accessibility
- Automated testing
- Static analysis
- CI/CD
- Git workflow discipline
- Security-aware engineering
- Technical documentation

## System Components

### Merchant Android Application

The Merchant Application is the user-facing Android application.

Responsibilities include:

- Displaying payment UI
- Accepting payment amounts
- Initiating payment requests
- Displaying payment progress
- Displaying approved or declined results
- Displaying transaction information

The Merchant Application must not communicate directly with the Payment Server.

### Payment Service

The Payment Service is a separate headless Android application.

Responsibilities include:

- Exposing a versioned IPC contract
- Receiving payment requests from the Merchant Application
- Validating payment requests
- Orchestrating payment transactions
- Communicating with the Payment Server
- Handling transport failures
- Preventing duplicate payment processing
- Returning stable payment results to the Merchant Application

### Payment Server

The Payment Server is a Kotlin-based backend application.

Responsibilities include:

- Receiving payment authorization requests
- Validating requests
- Applying deterministic payment outcomes
- Enforcing idempotency
- Returning payment results
- Providing transaction lookup
- Providing health information

## In Scope

The initial version of PayNexus includes:

- Payment amount entry
- Payment processing
- Approved payment result
- Declined payment result
- Timeout simulation
- Server failure simulation
- Payment transaction lookup
- Android Binder/AIDL communication
- HTTP communication
- Local transaction persistence
- Idempotent payment processing
- Automated tests
- CI/CD
- Architecture documentation

## Out of Scope

The following are explicitly outside the scope of the initial project:

- Real payment acquiring
- Real bank integrations
- Real card processing
- PAN storage
- CVV storage
- EMV kernel integration
- NFC card reading
- PCI DSS certification
- Production cryptographic key management
- Settlement
- Chargebacks
- Full merchant administration portal
- Real customer financial data

## Architectural Principle

The mandatory payment flow is:

Merchant Application
→ Payment Service
→ Payment Server

The Merchant Application must never bypass the Payment Service.

## Data Safety

Only synthetic payment data may be used.

The project must never require, store, log, or transmit real payment card information.

## Quality Principle

Every production change must be:

- traceable to a task
- implemented on a dedicated branch
- tested
- statically analyzed
- reviewed
- validated by CI
- merged through a Pull Request

## Documentation Language

All repository documentation, source code, commit messages, Pull Requests, Issues, architecture records, and public-facing project content must be written in English.

## Status

Initial architecture definition.
