# ADR-0004: Use Versioned HTTP Communication Between Payment Service and Payment Server

## Status

Accepted

## Context

The Payment Service must communicate with the Payment Server over a network boundary.

The project requires a transport that is easy to inspect, test, document, and simulate while remaining realistic for fintech backend integration.

The original case allows either TCP or HTTP communication.

## Decision

PayNexus will use versioned HTTP communication between the Payment Service and Payment Server.

The initial API will use JSON payloads.

Endpoints will be versioned under a path such as:

`/v1/...`

The Payment Service will own the HTTP client implementation.

The Merchant Application must not communicate with the Payment Server directly.

Transport DTOs must remain separate from domain models.

Network failures must be mapped into stable domain-level results.

## Initial API Direction

The initial server contract is expected to include operations such as:

- create or authorize payment
- retrieve payment by transaction ID
- health check

Exact endpoint design will be defined in a later implementation task.

## Alternatives Considered

### Raw TCP Socket Protocol

Raw TCP would more closely resemble some terminal communication protocols and would provide lower-level networking experience.

However, it would require additional work for:

- framing
- message parsing
- protocol versioning
- diagnostics
- test tooling

For the initial portfolio scope, this complexity does not provide enough additional value.

### WebSocket

WebSocket is useful for long-lived bidirectional communication.

The initial PayNexus payment flow does not require this communication model.

It is rejected for the initial version.

### gRPC

gRPC provides strongly typed contracts and efficient transport.

It is intentionally not selected for the first version because HTTP/JSON is easier to inspect, demonstrate, and evaluate in a portfolio project.

## Consequences

### Positive

- Easy to test with standard tools.
- Easy to document.
- Easy to inspect during demonstrations.
- Straightforward Ktor server implementation.
- Familiar failure semantics.
- Good fit for versioned REST-style contracts.

### Negative

- JSON has larger payload overhead than binary protocols.
- HTTP semantics must be designed carefully.
- Retry behavior must be coordinated with idempotency.
- Transport errors must not leak directly into UI logic.

## Architectural Constraint

Only the Payment Service may communicate with the Payment Server.

The Merchant Application must not contain server client implementations.
