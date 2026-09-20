# ADR-0001: Use a Monorepo Architecture

## Status

Accepted

## Context

PayNexus consists of three independently running components:

- Merchant Android Application
- Headless Android Payment Service
- Kotlin Payment Server

The project also includes shared domain contracts, testing utilities, build logic, CI/CD configuration, documentation, and Codex governance files.

A repository strategy is required that keeps the components independently runnable while allowing architecture, documentation, CI, and cross-component changes to remain coordinated.

## Decision

PayNexus will use a single Git repository.

The repository will contain:

- Android application modules
- shared Kotlin modules
- payment contract modules
- Ktor server modules
- Gradle build logic
- documentation
- CI/CD workflows
- Codex governance files
- Docker configuration

The repository structure must preserve independent deployability between the main runtime components.

The monorepo must not be used as an excuse to create tight coupling between components.

## Alternatives Considered

### Separate Repository per Component

This would provide stronger physical separation between the Merchant App, Payment Service, and Payment Server.

However, it would increase overhead for:

- synchronized changes
- shared architecture documentation
- CI configuration
- issue tracking
- portfolio review
- local development

For the current scope of PayNexus, this complexity is not justified.

### Single Android Application

This would simplify implementation but would fail to represent the intended payment architecture and cross-application IPC boundary.

It is therefore rejected.

## Consequences

### Positive

- Cross-component changes can be reviewed atomically.
- A single CI system can validate the whole platform.
- Documentation is centralized.
- The project is easier to clone and evaluate as a portfolio repository.
- Build logic and quality rules can be standardized.
- Codex can operate under one repository-level governance model.

### Negative

- Module boundaries must be enforced intentionally.
- Shared modules can become dumping grounds if not reviewed carefully.
- Large unrelated changes must still be split into focused Pull Requests.
- Independent deployability must be preserved despite repository proximity.

## Architectural Constraint

The monorepo must contain independently buildable runtime components.

The Merchant Application, Payment Service, and Payment Server must not collapse into a single application or process.
