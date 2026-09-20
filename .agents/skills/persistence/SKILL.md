---
name: persistence
description: Use for Room, Android local persistence, transaction ledgers, DAOs, database entities, migrations, server-side persistence, repository persistence mapping, and durable payment state. Do not use for pure domain changes that do not affect stored data.
---

# Persistence

## Use When

Use this skill when a task affects:

- Room
- Android local database
- DAOs
- database entities
- transaction ledger
- local payment history
- schema design
- migrations
- persistence mapping
- server persistence
- repository persistence implementations
- durable transaction state
- idempotency storage
- process-restart recovery

## Goals

- Keep persistence concerns separate from domain models.
- Preserve transaction integrity.
- Make schema changes explicit and migratable.
- Keep stored payment state deterministic and recoverable.
- Prevent sensitive data from being persisted.
- Make persistence behavior testable.

## Architectural Role

Persistence is infrastructure.

It must not define business rules.

Prefer:

```text
Domain / Application
        |
        v
Repository abstraction
        ^
        |
Persistence implementation
