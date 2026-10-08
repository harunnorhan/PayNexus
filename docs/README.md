# PayNexus Documentation

This index points to the authoritative project, architecture, engineering, and
verification documents for PayNexus. The root [README](../README.md) remains the
concise portfolio entry point.

## Project

- [Project Charter](project/project-charter.md) — goals, scope, safety boundaries,
  and the synthetic-data policy.
- [Engineering Principles](engineering/engineering-principles.md) — repository-wide
  design, quality, security, and workflow rules.

## Architecture

- [System Context](architecture/system-context.md) — the three runtime components
  and mandatory communication path.
- [Component Boundaries](architecture/component-boundaries.md) — ownership,
  dependency direction, and detailed implementation boundaries.

## Architecture Decisions

- [ADR-0001: Monorepo Architecture](adr/ADR-0001-monorepo-architecture.md)
- [ADR-0002: Android Binder and AIDL IPC](adr/ADR-0002-android-binder-aidl-ipc.md)
- [ADR-0003: Integer Minor-Unit Money](adr/ADR-0003-money-minor-unit-representation.md)
- [ADR-0004: Versioned HTTP Communication](adr/ADR-0004-http-service-to-server-communication.md)
- [ADR-0005: SQLite Durable Idempotency](adr/ADR-0005-server-sqlite-durable-idempotency.md)

## API / IPC

- The [Binder/AIDL decision](adr/ADR-0002-android-binder-aidl-ipc.md) defines the
  Merchant-to-Service contract direction.
- The [HTTP decision](adr/ADR-0004-http-service-to-server-communication.md) defines
  the Service-to-Server boundary.
- [Component Boundaries](architecture/component-boundaries.md) records the current
  V3 IPC, HTTP API, timeout, redirect, persistence, and lookup semantics.

## Security / Scope

- The [Project Charter](project/project-charter.md) defines synthetic-data-only
  scope and excludes real card processing and PCI certification claims.
- [Engineering Principles](engineering/engineering-principles.md) covers trust
  boundaries, secrets, logging, validation, and payment-safety expectations.

## Verification

- [Testing Strategy](engineering/testing-strategy.md) contains automated,
  runtime, UI, and end-to-end evidence with explicit limitations.
- [CI workflow](../.github/workflows/ci.yml) runs the repository quality and build
  gates for pull requests targeting `main`.

## UI / Design System

- [Design System](design/design-system.md) documents theme tokens, reusable Compose
  components, previews, and verification guidance.
