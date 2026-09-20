---
name: code-review
description: Use before declaring any implementation task complete and when reviewing Pull Requests. Focus on scope, architecture, payment safety, concurrency, security, testing, quality, documentation, and verification honesty.
---

# Code Review

## Use When

Use this skill:

- before declaring a task complete
- before opening a Pull Request
- when reviewing a Pull Request
- after resolving merge conflicts
- after significant architecture changes
- after security-sensitive changes
- after payment-domain changes

## Goals

- Catch architecture drift.
- Catch hidden scope expansion.
- Catch regressions.
- Catch missing failure handling.
- Catch security and payment-safety risks.
- Verify test and quality evidence.
- Ensure completion claims are honest.

## Review Order

Review in this order:

1. task scope
2. architecture
3. payment correctness
4. concurrency/lifecycle
5. security
6. testing
7. code quality
8. dependencies
9. documentation
10. verification evidence

Do not start with style nitpicks while correctness issues remain.

## Scope Review

Check:

- Does the diff match the linked PNX task?
- Are all acceptance criteria implemented?
- Are there unrelated refactors?
- Are unrelated files modified?
- Was extra scope introduced without discussion?
- Are required follow-up tasks being hidden as TODOs?

The Pull Request should represent one coherent change.

## Architecture Review

Verify:

- Merchant still delegates payment processing to Payment Service.
- Merchant does not call Payment Server directly.
- Merchant does not depend on Payment Service implementation classes.
- Domain remains framework-independent.
- HTTP DTOs do not leak into domain or presentation.
- AIDL models do not leak into domain unnecessarily.
- persistence entities do not leak outside infrastructure.
- module dependency direction remains valid.
- no circular dependency was introduced.
- no generic dumping-ground module was introduced.

Check whether an ADR is required.

## Payment Safety Review

Verify:

- money uses integer minor units
- currency is explicit
- no Float/Double monetary values
- payment state transitions are valid
- declines and technical failures remain distinct
- transaction identity remains stable
- retries preserve idempotency
- duplicate operations are prevented
- unknown outcomes are not incorrectly treated as declines/failures
- UI duplicate prevention is not the only financial safeguard

## Idempotency Review

Ask:

- Could the same logical payment execute twice?
- Does retry reuse the same idempotency key?
- Could Binder reconnect duplicate a payment?
- Could HTTP retry duplicate a payment?
- Could concurrent requests bypass duplicate protection?
- Is server-side enforcement authoritative?

If the answer is unclear, the review is not complete.

## Concurrency Review

Check:

- coroutine scope ownership
- cancellation behavior
- dispatcher usage
- shared mutable state
- race conditions
- duplicate submissions
- concurrent idempotency behavior
- database transaction boundaries

Do not approve concurrency behavior solely because tests happen to pass once.

## Android Lifecycle Review

When relevant, verify:

- ViewModel ownership
- lifecycle-aware Flow collection
- Service lifecycle
- bind/unbind behavior
- Binder disconnect behavior
- process recreation behavior
- Activity/Context leak risk
- main-thread blocking

## Compose Review

When UI changes exist, verify:

- business logic is outside Composables
- important states are explicit
- recomposition cannot duplicate payment side effects
- state is immutable where practical
- loading/error/result states exist
- dark mode works
- large font works
- responsive layout works
- accessibility is considered
- reusable components are not duplicated

## IPC Review

When AIDL/Binder changes exist, verify:

- contract version impact
- request validation
- asynchronous behavior
- service unavailable handling
- Binder death handling
- permissions/exported configuration
- payload size
- retry/idempotency interaction

## Backend Review

When Ktor/server changes exist, verify:

- routes remain thin
- endpoints are versioned
- DTO/domain separation
- request validation
- status code semantics
- declines are not generic 500 errors
- idempotency enforcement
- transaction lookup behavior
- server exceptions are sanitized
- persistence is outside routing

## Persistence Review

When database changes exist, verify:

- entity/domain separation
- migration strategy
- migration tests
- money representation
- transaction integrity
- idempotency persistence
- concurrency constraints
- sensitive data absence
- main-thread database access absence

## Security Review

Check:

- secrets
- credentials
- signing files
- real payment data
- sensitive logs
- exported Android components
- permissions
- network security
- input validation
- raw exception exposure
- debug-only security behavior

Any accidental secret is a blocking issue.

## Logging Review

Verify logs do not contain:

- PAN
- CVV
- PIN
- tokens
- passwords
- private keys
- raw sensitive payloads

Prefer safe identifiers and structured events.

## Testing Review

Check:

- behavior changes have automated coverage
- important failure paths are tested
- tests are deterministic
- tests do not rely on arbitrary sleeps
- tests are not coupled to private implementation details
- test doubles are appropriate
- test names describe behavior
- critical payment logic has strong unit coverage

## Verification Evidence

Do not accept claims such as:

```text
all tests pass
lint is clean
build succeeds
