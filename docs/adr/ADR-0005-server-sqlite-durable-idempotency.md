# ADR-0005: Use SQLite for Local Payment Server Idempotency

## Status

Accepted

## Context

The Payment Server accepts an idempotency key but previously retained no payment
state. Repeated, concurrent, or post-restart requests could not recover an
authoritative synthetic result. The existing server domain and infrastructure
modules also had placeholder dependencies with the wrong infrastructure-to-
application direction.

## Decision

The Payment Server will persist accepted synthetic payment records in a local
SQLite database through JDBC.

`:server:domain` owns the framework-independent payment intent, deterministic
outcome policy, repository port, and created/replayed/conflict decision.
`:server:infrastructure` owns SQLite/JDBC, schema bootstrap, parameterized SQL,
and connection-per-operation resource lifecycle. `:server:application` owns Ktor,
configuration, composition, blocking-IO dispatch, and HTTP mapping.

The idempotency key is the database primary key. Persistence uses an atomic insert
with `ON CONFLICT(idempotency_key) DO NOTHING`; a losing caller reads the durable
row and the domain compares exact payment ID, `Long` minor-unit amount, and
canonical currency. Conflicts never update the stored row.

The database path is externally configurable for local execution. Startup fails
if schema initialization fails; there is no stateless fallback.

## Alternatives Considered

An in-memory map was rejected because it does not survive restart or provide a
database uniqueness boundary. A select-before-insert sequence was rejected because
it is race-prone. ORM, migration, and connection-pool frameworks were rejected as
unnecessary for the single explicit schema and operation. A production database
and distributed coordination were deferred.

## Consequences

The current deterministic server gains durable local replay and conflict behavior
across repository/process reconstruction. JDBC resources remain short-lived and
thread-safe without a shared mutable connection.

SQLite remains a local, single-process persistence choice. This decision does not
provide distributed idempotency, production database durability, or exactly-once
execution of future acquiring or bank side effects. Such integration would require
additional transactional, outbox, and reconciliation design.
