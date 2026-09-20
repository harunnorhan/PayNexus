# ADR-0002: Use Android Binder and AIDL for Merchant-to-Service IPC

## Status

Accepted

## Context

The Merchant Android Application and Payment Service must run as separate Android applications.

The Merchant Application needs to initiate payment operations and receive results from the Payment Service.

The communication mechanism must represent a realistic Android cross-process boundary and expose a stable, explicit contract.

## Decision

PayNexus will use Android Binder with AIDL for communication between the Merchant Application and the Payment Service.

The IPC contract will be:

- explicit
- versioned
- minimal
- asynchronous
- transport-focused
- independent from UI implementation

The Payment Service must validate every incoming request.

The Merchant Application must handle:

- service unavailable
- bind failure
- Binder disconnection
- API version mismatch

The Payment Service implementation must remain hidden behind the IPC contract.

## Alternatives Considered

### Direct In-Process Kotlin Interface

This would be simpler but would not represent cross-application or cross-process communication.

It is rejected.

### BroadcastReceiver

Broadcasts are useful for loosely coupled events but are not appropriate as the main request-response mechanism for payment processing.

It is rejected for the core payment contract.

### ContentProvider

ContentProvider is designed primarily for structured data sharing rather than payment command orchestration.

It is rejected.

### Deep Links or Intents Only

Explicit intents may be useful for launching flows but do not provide the same typed, long-lived service contract required for this architecture.

They are not used as the primary IPC mechanism.

## Consequences

### Positive

- Demonstrates real Android IPC knowledge.
- Enforces a clear application boundary.
- Makes the payment contract explicit.
- Supports versioning.
- Allows the Payment Service to remain headless.
- Provides realistic failure modes such as Binder death and service unavailability.

### Negative

- AIDL adds complexity.
- IPC models require careful versioning.
- Binder calls must avoid blocking the main thread.
- Instrumentation testing is more complex than in-process unit testing.
- Backward compatibility must be considered when the contract evolves.

## Architectural Constraint

The Merchant Application must never depend on Payment Service implementation classes.

Communication must occur only through the defined IPC contract.
