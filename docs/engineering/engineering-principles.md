# PayNexus Engineering Principles

## Purpose

This document defines the engineering principles that guide all implementation decisions in PayNexus.

These principles apply to Android applications, backend services, tests, CI/CD, documentation, and Codex-assisted development.

## 1. Prefer Simplicity Over Accidental Complexity

PayNexus intentionally models a small payment domain.

Complexity must only be introduced when it provides a clear architectural, reliability, security, or testing benefit.

The project should remain understandable to a reviewer within a short amount of time.

## 2. Preserve Explicit Component Boundaries

The system consists of three independently running components:

- Merchant Android Application
- Headless Android Payment Service
- Kotlin Payment Server

These boundaries must remain explicit.

The Merchant Application must never bypass the Payment Service.

## 3. Business Logic Must Be Framework Independent

Core payment rules must not depend on:

- Android framework classes
- Jetpack Compose
- Room
- Retrofit
- Ktor client
- Binder implementation details
- server framework classes

Business logic should remain testable using plain Kotlin.

## 4. Use Dependency Inversion

Higher-level business rules must not depend directly on lower-level infrastructure implementations.

Infrastructure should implement abstractions defined closer to the domain or application layer.

Examples:

- repositories expose interfaces
- network clients remain implementation details
- persistence implementations remain replaceable
- Binder clients remain behind abstractions where appropriate

## 5. Keep Transport Models Separate From Domain Models

HTTP DTOs, AIDL models, database entities, and domain models represent different concerns.

They must not be treated as interchangeable types.

Explicit mapping is preferred over leaking transport or persistence concerns across layers.

## 6. Represent Money Safely

Payment amounts must use integer minor units.

Examples:

- TRY 10.00 -> 1000
- USD 12.50 -> 1250

`Double` and `Float` must never be used for monetary values.

Currency must be represented explicitly.

## 7. Design for Failure

Payment software must assume that failures will happen.

Implementation must consider:

- network timeout
- server errors
- Binder disconnect
- unavailable Payment Service
- malformed responses
- duplicated requests
- process recreation
- persistence failures

Failure states must be modeled explicitly rather than hidden behind generic exceptions.

## 8. Protect Against Duplicate Financial Operations

Payment retries must be safe.

Idempotency must be considered whenever a payment request can be repeated because of:

- retry
- timeout
- network interruption
- process restart
- user interaction

Duplicate requests must not silently create duplicate payment transactions.

## 9. Prefer Immutable State

Immutable data structures and unidirectional state flow are preferred.

UI state should be represented explicitly and updated predictably.

Mutable shared global state must be avoided.

## 10. Make Concurrency Explicit

Asynchronous work must use structured concurrency.

Kotlin Coroutines should be used intentionally.

Long-running or blocking work must never run on the Android main thread.

Coroutine scopes must have clear ownership and lifecycle.

## 11. Treat UI State as a Product Contract

Every important screen must define its states explicitly.

Typical states include:

- idle
- loading
- processing
- approved
- declined
- technical failure
- unavailable service
- empty
- disabled

UI behavior must remain deterministic for each state.

## 12. Build Responsive and Accessible Interfaces

Pixel-perfect implementation must not depend on hard-coded device dimensions.

UI must support:

- different Android screen sizes
- portrait and landscape where relevant
- dark theme
- increased font scale
- text wrapping
- accessible touch targets
- semantic descriptions where appropriate

## 13. Testing Is Part of Implementation

Tests are not a separate final phase.

Every feature must include the appropriate level of automated testing.

Depending on the change, this may include:

- unit tests
- ViewModel tests
- Flow tests
- repository tests
- server tests
- integration tests
- IPC instrumentation tests
- Compose UI tests
- screenshot tests
- end-to-end tests

## 14. Prefer Behavioral Tests

Tests should validate observable behavior rather than internal implementation details.

Refactoring should not require rewriting unrelated tests.

## 15. Static Analysis Is a Merge Gate

Formatting and static analysis are mandatory.

The project will use automated quality tools such as:

- ktlint or Spotless
- Detekt
- Android Lint
- compiler warnings
- coverage reporting where meaningful

Warnings and rule violations must not be ignored without explicit justification.

## 16. CI Must Reproduce Local Verification

Developers and Codex should run the same core commands locally that CI runs remotely.

A Pull Request must not be mergeable when required verification fails.

## 17. Never Hide Failing Checks

Disabling tests, suppressing lint rules, weakening quality gates, or removing assertions only to make CI pass is forbidden.

The root cause must be addressed.

## 18. Every Change Must Be Traceable

Production changes must be linked to a task.

The normal workflow is:

Issue
-> Branch
-> Implementation
-> Tests
-> Commit
-> Push
-> Pull Request
-> CI
-> Review
-> Squash Merge

## 19. Keep Branches Short-Lived

Feature and maintenance branches should remain focused and short-lived.

Unrelated changes must not be mixed into the same branch.

## 20. Use Conventional Commits

Commit messages must follow Conventional Commit style.

Examples:

- `feat(merchant): implement amount entry`
- `feat(service): add payment binder contract`
- `test(server): cover idempotent requests`
- `docs(adr): record IPC decision`
- `ci: add pull request verification`

## 21. Prefer Small Pull Requests

Pull Requests should represent one coherent change.

Large refactors and unrelated cleanup should be split into separate tasks.

## 22. Document Architectural Decisions

Significant architectural choices must be recorded as Architecture Decision Records.

ADRs must explain:

- context
- decision
- alternatives
- consequences

## 23. Security Must Be Considered at Boundaries

Every trust boundary must validate incoming data.

Secrets must never be committed.

Logs must not contain sensitive financial data.

Only synthetic payment data is allowed.

## 24. Observability Must Be Useful and Safe

Logs should help trace payment behavior without exposing sensitive information.

Use stable identifiers such as:

- transaction ID
- correlation ID
- terminal ID
- merchant ID

Sensitive payloads must not be logged.

## 25. No Real Payment Card Data

PayNexus is a simulation.

The project must never require or process real:

- PAN
- CVV
- PIN
- customer banking credentials
- production payment keys

## 26. Dependencies Must Be Intentional

Every external dependency adds maintenance and security cost.

New dependencies must have a clear purpose.

Dependencies must not be introduced solely to avoid writing small, understandable code.

## 27. Shared Code Requires a Clear Reason

A shared module must exist because multiple components genuinely share stable behavior or contracts.

Shared modules must not become generic dumping grounds.

## 28. Codex Must Follow Repository Rules

Codex is an implementation assistant, not the architectural authority.

Codex must:

- read repository instructions before changing code
- respect component boundaries
- avoid unrelated changes
- add tests
- run verification
- report uncertainty
- never claim a command passed if it was not executed

Codex-specific rules will be defined separately under project governance files.

## 29. Documentation Is Part of the Product

Public-facing repository documentation must remain accurate.

Architecture documentation, ADRs, setup instructions, and test instructions must be updated when implementation changes make them stale.

## 30. Optimize for Reviewability

A reviewer should be able to understand:

- why a change exists
- what changed
- how it was tested
- what risks remain
- how to reproduce the behavior

without reverse-engineering the codebase.

## Final Principle

PayNexus should remain deliberately small in product scope and deliberately strong in engineering discipline.

Feature count is not the primary measure of quality.

Clarity, correctness, testability, reliability, security awareness, and architectural consistency are.
