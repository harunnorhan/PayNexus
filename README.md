# PayNexus

PayNexus is a modular payment terminal simulation built as a
professional fintech engineering portfolio project.

The system consists of:

- Merchant Android Application
- Headless Android Payment Service
- Kotlin Payment Server

The required runtime communication path is:

`Merchant Application -> Payment Service -> Payment Server`

> Project status: foundational architecture, Gradle monorepo, agent governance, and code quality tooling are established. Feature implementation is in progress.

## Documentation

Project architecture and engineering decisions are documented under [`docs/`](docs/).

Key documents:

- [Project Charter](docs/project/project-charter.md)
- [System Context](docs/architecture/system-context.md)
- [Component Boundaries](docs/architecture/component-boundaries.md)
- [Engineering Principles](docs/engineering/engineering-principles.md)
- [Architecture Decision Records](docs/adr/)

## Project Structure

PayNexus is organized as a Gradle monorepo.

```text
PayNexus/
├── apps/
│   ├── merchant/
│   └── payment-service/
├── core/
│   ├── model/
│   └── domain/
├── payment/
│   ├── contract/
│   └── domain/
├── server/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
├── build-logic/
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

### Runtime Components

- `apps:merchant` — Merchant-facing Android application
- `apps:payment-service` — Headless Android payment service
- `server:*` — Kotlin/JVM payment server foundation

The mandatory runtime communication path is:

`Merchant Application -> Payment Service -> Payment Server`

The Merchant Application must never communicate with the Payment Server directly.

### Foundation Modules

- `core:model` — Framework-independent shared core models
- `core:domain` — Framework-independent core domain logic
- `payment:contract` — Payment boundary contracts
- `payment:domain` — Payment-specific domain logic
- `server:domain` — Server-side domain layer
- `server:application` — Server application/use-case layer
- `server:infrastructure` — Server infrastructure implementations

## Build Logic

Reusable Gradle configuration is centralized under `build-logic`.

Current convention plugins:

- `paynexus.android.application`
- `paynexus.android.library`
- `paynexus.kotlin.jvm.library`
- `paynexus.code.quality`

Dependency and plugin versions are centralized in `gradle/libs.versions.toml`.

## Code Quality

PayNexus uses repository-wide quality checks that can be executed locally and later reused by CI.

The current quality toolchain includes:

- Spotless
- ktlint
- Detekt
- Android Lint

Run all repository quality checks with:

```bash
./gradlew qualityCheck
```

Individual checks can also be executed with:

```bash
./gradlew spotlessCheck
./gradlew detekt
./gradlew lint
```

Run the full project build with:

```bash
./gradlew build
```

Quality rules must not be disabled, broadly suppressed, or hidden behind baselines only to make verification pass.

Version-availability lint checks are intentionally excluded from the strict quality gate because external dependency releases must not make an otherwise unchanged build nondeterministically fail.

## Build

Use the Gradle Wrapper:

```bash
./gradlew projects
./gradlew tasks
./gradlew build
```

A globally installed Gradle distribution is not required.
