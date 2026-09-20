---
name: ktor-backend
description: Use for Ktor server routes, plugins, application wiring, HTTP APIs, request validation, serialization, server-side use cases, error handling, and API versioning. Do not use for Android-only changes or domain-only changes that do not touch backend behavior.
---

# Ktor Backend

## Use When

Use this skill when a task affects:

- Ktor server setup
- routing
- HTTP endpoints
- request/response DTOs
- serialization
- request validation
- server application wiring
- server-side use cases
- error handling
- API versioning
- health endpoints
- server plugins
- content negotiation
- status code mapping
- server-side idempotency behavior

## Goals

- Keep transport concerns separate from business logic.
- Keep APIs explicit, versioned, and testable.
- Keep request validation close to the server boundary.
- Preserve stable domain semantics behind HTTP transport.
- Make backend behavior deterministic and easy to inspect.

## Architectural Role

The Payment Server is a separate runtime component.

Its responsibilities include:

- receiving payment requests
- validating transport input
- enforcing idempotency
- applying deterministic payment simulation
- persisting transaction state where required
- returning stable responses
- exposing transaction lookup
- exposing health information

The server must not depend on Android-specific code.

## Layering

Prefer a structure conceptually similar to:

```text
Routing / Transport
        |
        v
Application / Use Cases
        |
        v
Domain
        ^
        |
Infrastructure
