# PayNexus

PayNexus is a modular payment terminal simulation built as a
professional fintech engineering portfolio project.

The system consists of:

- Merchant Android Application
- Headless Android Payment Service
- Kotlin Payment Server

The required runtime communication path is:

`Merchant Application -> Payment Service -> Payment Server`

> Project status: foundational architecture, Gradle monorepo, agent governance, and code quality tooling are established. The pure Kotlin payment domain, Merchant amount entry, and V1 IPC contract foundation are implemented; runtime payment integration remains future work.

## Documentation

Project architecture and engineering decisions are documented under [`docs/`](docs/).

Key documents:

- [Project Charter](docs/project/project-charter.md)
- [System Context](docs/architecture/system-context.md)
- [Component Boundaries](docs/architecture/component-boundaries.md)
- [Engineering Principles](docs/engineering/engineering-principles.md)
- [Testing Strategy](docs/engineering/testing-strategy.md) — Domain and Merchant JVM tests, with local runtime verification and guidance for future testing layers.
- [Architecture Decision Records](docs/adr/)

## Project Structure

PayNexus is organized as a Gradle monorepo.

```text
PayNexus/
├── apps/
│   ├── merchant/
│   └── payment-service/
├── design-system/
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
- `apps:payment-service` — Headless Android bound Service exposing the V1 contract-version query
- `server:*` — Kotlin/JVM payment server foundation

The mandatory runtime communication path is:

`Merchant Application -> Payment Service -> Payment Server`

The Merchant Application must never communicate with the Payment Server directly.

### Foundation Modules

- `core:model` — Framework-independent shared core models
- `core:domain` — Framework-independent core domain logic
- `payment:contract` — Android-specific versioned AIDL/Binder contract
- `payment:domain` — Payment-specific domain logic
- `server:domain` — Server-side domain layer
- `server:application` — Server application/use-case layer
- `server:infrastructure` — Server infrastructure implementations

### Design System Foundation

`design-system` provides the provisional Compose theme, compact spacing tokens,
and `PayNexusButton`, with debug-only previews. It is independent of payment
models and is consumed by Merchant amount entry. See the
[Design System guide](docs/design/design-system.md) for APIs and verification.

### Payment Domain Foundation

`payment:domain` owns the immutable payment value types, identifiers, outcomes,
and lifecycle in `com.paynexus.payment.domain`. Money uses `Long` minor units
and explicit TRY currency; `PaymentAmount` requires a positive quantity.
Parse supported currency codes with `CurrencyCode.fromCode(code)`.

The lifecycle is `Created -> Processing -> Finished(outcome)`, with explicit
approved, declined, and technically failed outcomes. Illegal transitions throw
a domain transition exception, and terminal states cannot transition further.
These types provide no transport, persistence, retry, or idempotency enforcement.

Run the deterministic JVM domain tests with:

```bash
./gradlew :payment:domain:test
```

### IPC Contract Foundation

`:payment:contract` owns the versioned AIDL/Binder contract in
`com.paynexus.payment.contract`. V1 exposes only `IPaymentService.getContractVersion()`.
`PaymentIpcContract.supports(remoteVersion)` accepts version 1 and rejects all
other versions, including unknown future versions. Both current and minimum
supported versions are 1. Compatibility negotiation is not authentication or
authorization.

The Android library enables AIDL locally and has no payment-domain dependency.
`:apps:payment-service` now depends on the contract and contains
`com.paynexus.paymentservice.PaymentService`, an Android bound Service owning a
private generated `IPaymentService.Stub` implementation. `getContractVersion()`
returns `PaymentIpcContract.CURRENT_VERSION` without side effects.

The Service is explicitly exported for future cross-application binding and
protected by `com.paynexus.paymentservice.permission.BIND_PAYMENT_SERVICE`, a
signature-level permission defined only by Payment Service. It has no intent
filter; future clients must target the component explicitly and request the
permission with a compatible signing identity. Compatibility is not authorization.

Payment Service remains headless, with no custom process, started/foreground
service behavior, payment-domain dependency, networking, or persistence. Merchant
remains unbound and does not consume the contract or request the permission yet.
No payment request/result transport exists. Cross-application Binder integration
and runtime permission-enforcement tests remain pending the client boundary.
Domain models remain framework-independent. This uses ordinary Android Gradle
Plugin AIDL, with repository-owned version metadata. See
[Service shell verification](docs/engineering/testing-strategy.md#payment-service-shell-verification).

```bash
./gradlew :payment:contract:test
./gradlew :payment:contract:assembleDebug
./gradlew :payment:contract:lint
```

JVM policy tests and generated API/artifact inspection verify this foundation;
Binder runtime instrumentation remains pending. See
[IPC verification](docs/engineering/testing-strategy.md#ipc-contract-foundation-verification)
and [contract boundaries](docs/architecture/component-boundaries.md#ipc-contract-foundation).

## Build Logic

Reusable Gradle configuration is centralized under `build-logic`.

Current convention plugins:

- `paynexus.android.application`
- `paynexus.android.library`
- `paynexus.android.compose`
- `paynexus.kotlin.jvm.library`
- `paynexus.code.quality`

Dependency and plugin versions are centralized in `gradle/libs.versions.toml`.

## Code Quality

PayNexus uses the same repository-wide quality checks locally and in CI.

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

## Continuous Integration

The [CI workflow](.github/workflows/ci.yml) runs on Pull Requests targeting `main`.
Its `Quality and Build` job runs on Ubuntu 24.04 with Eclipse Temurin JDK 17
and uses the committed Gradle Wrapper. The `gradle/actions/setup-gradle` action
validates Gradle Wrapper JARs before verification; snapshot wrappers are disallowed.
The Wrapper also verifies the downloaded Gradle distribution using its committed checksum.

CI runs these commands as separate, sequential steps. Use JDK 17 and the same
commands for local verification:

```bash
./gradlew qualityCheck
./gradlew build
```

Gradle setup uses basic, read-only caching for Pull Requests. This workflow does
not populate the shared cache, so cache misses are expected until suitable entries
exist. Cache availability is not required for verification.

The workflow uses SHA-pinned official GitHub and Gradle actions with read-only
repository permissions. It requires no configured secrets and does not publish
Build Scans or submit dependency graphs.

`Quality and Build` is the stable required-status-check candidate. Required status
checks for `main` will only be configured after the first successful remote run
has been verified as stable. Adding this workflow does not configure repository
protection settings or establish that remote CI has passed.

## Build

Use the Gradle Wrapper:

```bash
./gradlew projects
./gradlew tasks
./gradlew build
```

A globally installed Gradle distribution is not required.

## Merchant Amount Entry

Merchant launches through
`MainActivity -> PayNexusTheme -> MerchantApp -> AmountEntryRoute -> AmountEntryScreen`.
The feature belongs to `com.paynexus.merchant.feature.amountentry` inside
`:apps:merchant`; no extra feature module is needed. Merchant explicitly depends
on `:payment:domain` to construct `PaymentAmount(Money(minorUnits, CurrencyCode.TRY))`.
Design System remains independent and supplies theme, spacing, and the confirmation button.

Input uses ASCII digits and either a dot or comma as a decimal separator, with
at most two fractional digits. Leading separators and leading zeros are accepted.
Empty input and trailing separators are intermediate, nonconfirmable states.
Whitespace, signs, unsupported characters, mixed/repeated separators, excess
fractional digits, and technical Long overflow cannot be confirmed. Text is
preserved exactly: nothing is trimmed, filtered, rounded, or silently truncated.
Parsing and formatting use string/integer operations only; no transaction business
maximum is imposed. Zero parses numerically but cannot create a `PaymentAmount`.

`AmountEntryViewModel` owns synchronous Compose state. The candidate is stored only
as a canonical `PaymentAmount`; parsed minor units are transient. Every edit event,
including identical text, clears local confirmation. Confirming displays
**Amount ready: TRY 12.34** and **No payment has been started.** State survives
configuration recreation but starts empty after process recreation. Payment Service
remains unconnected: there is no payment initiation, identifier generation,
networking, Binder, or persistence.

Android conventions use compile SDK 37, minimum SDK 26, and Java 17. Install SDK
Platform 37. Applications retain target SDK 36. Merchant directly declares stable
Lifecycle ViewModel and ViewModel Compose 2.11.0. There is no feature StateFlow,
coroutine scheduling, or direct coroutine dependency.

Build, test, and install on a connected local emulator/device:

```bash
./gradlew :apps:merchant:test
./gradlew :apps:merchant:assembleDebug
./gradlew :apps:merchant:installDebug
```

Open **PayNexus Merchant** from the launcher. Follow the
[Merchant verification checklist](docs/engineering/testing-strategy.md#merchant-amount-entry-verification).
Parser, formatter, and ViewModel behavior have JVM tests. PNX-010 intentionally
adds no Compose instrumentation infrastructure; platform interaction still needs
local runtime verification. Compilation does not establish visual correctness.
