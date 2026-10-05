# PayNexus

PayNexus is a modular payment terminal simulation built as a
professional fintech engineering portfolio project.

The system consists of:

- Merchant Android Application
- Headless Android Payment Service
- Kotlin Payment Server

The required runtime communication path is:

`Merchant Application -> Payment Service -> Payment Server`

> Project status: foundational architecture, Gradle monorepo, agent governance, and code quality tooling are established. The pure Kotlin payment domain, Merchant cashier flow, strict V3 asynchronous payment IPC contract, versioned Ktor Payment Server payment API, bounded Payment Service-to-Server orchestration, and selected ambiguous-outcome resolution are implemented; Android runtime and end-to-end verification remain pending.

## Documentation

Project architecture and engineering decisions are documented under [`docs/`](docs/).

Key documents:

- [Project Charter](docs/project/project-charter.md)
- [System Context](docs/architecture/system-context.md)
- [Component Boundaries](docs/architecture/component-boundaries.md)
- [Engineering Principles](docs/engineering/engineering-principles.md)
- [Testing Strategy](docs/engineering/testing-strategy.md) — Domain, Merchant, and Payment Server JVM tests, with local runtime verification and guidance for future testing layers.
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
- `apps:payment-service` — Headless Android bound Service orchestrating V3 payments through the Payment Server client
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
- `server:application` — Runnable server process and application-composition layer
- `server:infrastructure` — Server infrastructure implementations

### Payment Server Foundation (PNX-017)

`:server:application` is a runnable Kotlin/Ktor application with an explicit
bootstrap entry point and reusable `Application.module()`. Its current HTTP
surface is deliberately limited to:

```text
GET /health
HTTP 200
Content-Type: application/json; charset=UTF-8
{"status":"ok","service":"paynexus-payment-server"}
```

The endpoint reports only that the application module is responding. It does not
probe external dependencies or claim production readiness. The application binds
to loopback port 8080 when run locally; production configuration and deployment
remain deferred.

Run its deterministic in-process JVM test with:

```bash
./gradlew :server:application:test
```

No external server process, socket, database, or downstream service is required by
the test. At the PNX-017 baseline there was no payment HTTP API, persistence,
idempotency enforcement, authentication, or Payment Service-to-Server transport.
`:server:domain` remained framework-independent, and the mandatory path remained
Merchant Application -> Payment Service -> Payment Server.

### Payment Server API (PNX-018)

`:server:application` exposes the first versioned Payment Server payment endpoint:

```text
POST /v1/payments
```

The JSON request carries caller-owned `paymentId` and `idempotencyKey` strings,
positive `Long` `amountMinorUnits`, and canonical `TRY` currency. Identifiers are
nonblank, limited to 256 UTF-16 code units, accepted without normalization, and
echoed exactly. Malformed JSON, missing/null/wrongly typed fields, unknown fields,
invalid identifiers, nonpositive amounts, and unsupported or noncanonical currency
return HTTP 400 with `{"error":"INVALID_REQUEST"}`.

Valid synthetic requests return HTTP 200. Positive minor units modulo three map
to `APPROVED`, `DECLINED` with `UNSPECIFIED`, or `FAILED` with
`PROCESSING_ERROR`. These are explicit wire strings, not enum ordinals, and are
only a deterministic transport demonstration—not bank or acquirer authorization.

The HTTP DTOs, boundary validation, and stateless processor are owned by
`:server:application`; at the PNX-018 baseline no Payment Service client or
Merchant server path existed.
`idempotencyKey` is transported but not durably enforced. There is no database,
persistence, transaction lookup, authentication, retry, or production endpoint.
`GET /health` remains unchanged. Deterministic tests use Ktor's in-process test
host and require no real port or external process.

### Payment Service HTTP Client Foundation (PNX-019)

`:apps:payment-service` now owns an internal typed Ktor client for the existing
`POST /v1/payments` API. Its input uses `PaymentId`, `IdempotencyKey`, and
`PaymentAmount`; separate Service-owned Kotlin serialization DTOs preserve exact
identifiers, `Long` minor units, and canonical TRY on the JSON boundary. Responses
must echo both identifiers exactly and use one of the three supported explicit
outcome/reason string combinations before they can become a domain outcome.

HTTP 400 invalid requests, unexpected status codes, malformed responses, invalid
outcome combinations, identifier mismatches, and transport failures remain distinct
from business outcomes. Deterministic Payment Service JVM tests use Ktor MockEngine
without a server process, real socket, Android runtime, or device.

The client is not wired into `PaymentService.submitPayment()`. Binder execution
continues to use the local `SyntheticPaymentProcessor`, and Merchant remains unaware
of the Payment Server. No Internet permission, production endpoint, cleartext
configuration, persistence, retry, or durable idempotency is introduced. Runtime
Service-to-Server integration and device/network verification remain deferred.

### IPC Technical Failure Contract (PNX-020)

The Merchant-to-Service IPC contract is now strict V3: both `CURRENT_VERSION`
and `MIN_SUPPORTED_VERSION` are 3, with no V2 fallback or downgrade. The existing
Service and callback transaction positions remain stable, and the callback appends
one new one-way technical-failure operation after `onResult` and `onRejected`.

The contract now keeps three terminal meanings distinct: `onResult` carries a
confirmed business outcome, `onRejected(INVALID_REQUEST)` reports invalid
Merchant/IPC input, and the explicit `PAYMENT_OUTCOME_UNAVAILABLE` technical value
reports that no confirmed business outcome can be provided. Technical uncertainty
never becomes `PaymentOutcome`, does not prove whether remote processing occurred,
and never triggers automatic retry or replay.

Payment Service continues to use the local `SyntheticPaymentProcessor` production
path. The PNX-019 HTTP client remains implemented but unwired. PNX-021 owns future
Service-to-Server production integration; runtime, device, and end-to-end V3
verification remain deferred.

### Payment Service-to-Server Integration (PNX-021)

The Payment Service production path now connects accepted V3 Binder requests to
the existing typed `PaymentServerClient`. IPC validation remains synchronous and
bounded, while all client submission work runs on a Service-owned executor with
one worker and a queue capacity of one. Saturation is observable through the V3
technical-failure callback; it never runs HTTP on a Binder thread and never retries,
requeues, or replays a payment.

The Service creates at most one reusable `KtorPaymentServerClient` for its lifetime.
Confirmed server outcomes continue through the existing `PaymentTransportMapper`
and `onResult`; downstream, protocol, correlation, transport, unavailable-client,
and worker-admission failures use
`onTechnicalFailure(PAYMENT_OUTCOME_UNAVAILABLE)`. Invalid IPC input alone uses
`onRejected(INVALID_REQUEST)`. Exact identifiers and positive `Long` minor units
are preserved across both boundaries.

Debug builds use the development-only emulator endpoint
`http://10.0.2.2:8080`. Cleartext is denied generally and allowed only for that
debug emulator host. Release builds intentionally configure no endpoint and fail
closed without constructing an HTTP client. Merchant has no server endpoint,
network dependency, or Internet permission.

The Service-local synthetic processor has been removed; the server retains its
stateless synthetic demonstration. There is still no persistence, durable
idempotency, automatic retry/replay, or remote-cancellation guarantee. Runtime,
device, manual-network, and end-to-end verification remain deferred.

### Payment Server Persistence and Durable Idempotency (PNX-022)

The Payment Server now stores accepted synthetic payment records in a local
SQLite database through JDBC. `:server:domain` owns the framework-independent
payment intent, deterministic outcome policy, repository port, and replay/conflict
decision. `:server:infrastructure` owns schema bootstrap, parameterized SQL,
connection lifecycle, and database-enforced uniqueness. `:server:application`
owns HTTP validation, configuration, composition, blocking-IO dispatch, and
sanitized status mapping.

The database path is read from `PAYNEXUS_PAYMENT_DB_PATH`. When the variable is
absent, local development uses `./data/paynexus-payments.db`; an explicitly blank
value fails application initialization. Schema initialization must succeed before
routes become available, and there is no stateless fallback.

The first accepted idempotency key stores one authoritative response. Repeating
the same key with the same exact payment ID, `Long` minor-unit amount, and canonical
currency replays that response, including after reconstruction against the same
database file. Reusing the key for a different accepted intent returns HTTP 409
with `{"error":"IDEMPOTENCY_CONFLICT"}` and never changes the original row.
Existing HTTP 200 and HTTP 400 response contracts remain unchanged.

This is local, single-process API-level idempotency for the deterministic synthetic
processor. It is not distributed idempotency, a production database guarantee, or
exactly-once execution of future bank/acquirer side effects. No transaction lookup,
Payment Service retry/replay, or Android behavior is added. Runtime/manual
end-to-end verification remains deferred.

### Payment Server Durable Payment Lookup (PNX-023)

The Payment Server now exposes a read-only lookup on the same versioned payment
resource:

```text
GET /v1/payments
PayNexus-Idempotency-Key: <exact-key>
```

The header must contain exactly one nonblank value of at most 256 UTF-16 code
units. Accepted text is used exactly as supplied without trimming, normalization,
or case folding. An existing committed row returns HTTP 200 with the existing
payment response representation. A valid unknown key returns HTTP 404 with
`{"error":"PAYMENT_NOT_FOUND"}`, invalid lookup input returns HTTP 400 with
`{"error":"INVALID_REQUEST"}`, and persistence failure returns the existing
sanitized HTTP 500 `{"error":"INTERNAL_ERROR"}` response.

Lookup reads the authoritative stored record and never recomputes an outcome,
inserts, updates, retries, replays, polls, or waits for an in-flight POST. A 404
means only that no committed row was visible at lookup time. Existing POST
creation, replay, conflict, validation, and persistence-failure behavior is
unchanged. Lookup is by durable idempotency key only; there is no payment-ID
lookup, list, history, search, or Android lookup consumer. Authentication and
authorization remain outside this portfolio task, and runtime/manual end-to-end
verification remains deferred.

### Payment Service Outcome Resolution (PNX-024)

After exactly one admitted `POST /v1/payments`, selected ambiguous failures may
trigger exactly one read-only `GET /v1/payments` using the original exact
idempotency key. Transport failure, HTTP 5xx, and selected malformed, invalid, or
mis-correlated HTTP 200 responses are eligible. A strictly decoded and exactly
correlated lookup response can restore the confirmed business outcome through the
existing IPC V3 result path.

The Payment Service never automatically retries or replays the POST. Invalid
requests, unexpected non-5xx statuses, and every HTTP 409 response are not eligible
for lookup. This includes malformed or unexpected 409 bodies: the lookup response
does not contain the original amount, so it cannot prove that a stored result
belongs to a conflicting request intent. Lookup `NotFound` and every unsuccessful
lookup remain `PAYMENT_OUTCOME_UNAVAILABLE`; there is no polling or repeated GET.

The existing Service-owned worker, bounded queue, callback ownership, reusable
Ktor client, and fail-closed release configuration remain unchanged. IPC V3,
Merchant production code, Payment Server production code and schema, Android
network configuration, and Gradle dependencies are unchanged. No explicit HTTP
timeout policy is introduced. Runtime/manual end-to-end verification remains
deferred.

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

### Asynchronous Payment IPC (PNX-015)

`:payment:contract` owns the ordinary AGP AIDL interfaces, manual Parcelable
request/result models, and version metadata. Both `CURRENT_VERSION` and
`MIN_SUPPORTED_VERSION` are **2**. V1 peers and unknown future versions are
rejected without fallback. Both applications must be upgraded together.
The original synchronous `getContractVersion()` query retains its transaction
position; the one-way `submitPayment(request, callback)` operation is appended.
Results and request rejections return through a one-way per-request callback.
Dispatch returning does not acknowledge processing or guarantee result delivery.

Merchant's internal `PaymentServiceClient.submitPayment(id, key, amount)` accepts
caller-owned domain values only after V2 readiness. Its `submissionState` exposes
an Android-free local observation. At most one request is active; connection
attempt plus local request identity and exact echoed identifiers prevent stale
or duplicate callbacks from completing another request. Connection recovery never
replays payments. Stop/close abandons local ownership and detaches callbacks.
Transport failure, request rejection, malformed results, and abandonment do not
manufacture a domain decline or processing failure.

Request fields preserve exact Payment ID, idempotency key, `Long` minor units,
and canonical TRY currency. Each identifier is limited to 256 UTF-16 code units;
invalid values are rejected without normalization or truncation. The contract
has no domain dependency. Mapping belongs to each application; Payment Service
now depends on the existing pure Kotlin payment domain for validation/outcomes.
No domain type gains Android or Parcelable behavior.

Payment Service performs a stateless synthetic demonstration: positive minor units
modulo 3 select approved (`0`), declined/unspecified (`1`), or failed/processing
error (`2`). For example, 300, 301, and 302 minor units exercise the three branches.
These are not bank/acquirer operations. The Service makes one callback delivery
attempt and retains no payment state. There is no networking, persistence,
idempotency enforcement, retry framework, or Service-to-Server integration.

The existing explicit component and Service-owned signature bind permission remain
unchanged. Merchant requests that permission without defining it. Compatibility
is not authorization. Activity start/stop/destroy still own bind/unbind/close.
The bounded worker now dispatches both version queries and payment submissions
on `paynexus-ipc`; main owns state and Binder callbacks post events to it.
PNX-014's one connection recovery allowance per started interval is preserved.

Amount entry and local confirmation remain unchanged: confirming an amount never
submits a payment. At the PNX-015 baseline, human runtime verification used the
internal client through the debugger without a payment UI or automatic submission.
There is no handshake or payment callback deadline. A hung synchronous query can
hold the worker; a silent live Service can leave a request pending until cleanup.
Cancellation does not guarantee cancellation of remote work.

See [PNX-015 verification](docs/engineering/testing-strategy.md#asynchronous-payment-transport-verification-pnx-015)
for local test/build evidence and the separate human runtime checklist.
**PNX-015 runtime/device verification is pending and was not performed by Codex.**
Historical [PNX-013](docs/engineering/testing-strategy.md#pnx-013-local-verification-record)
and [PNX-014](docs/engineering/testing-strategy.md#pnx-014-local-verification-record)
runtime records do not verify V2 payment transport. Remote CI is also separate.

### Merchant Payment Flow (PNX-016)

Merchant now connects its confirmed canonical `PaymentAmount` to the existing V2
client through a second, explicit cashier action. The single state-driven screen
represents editing, confirmation, processing, approved, declined, failed, and
transport/protocol-failure states. Confirming remains entirely local; only
**Start payment** creates and submits a request. **New payment** clears the previous
amount, identifiers, result, and local flow ownership without submitting anything.

`AmountEntryViewModel` owns the confirmed amount, caller-generated identifiers,
processing state, terminal domain outcome, and reset behavior. It does not retain
the Android client, Binder objects, AIDL interfaces, Parcelables, or Context.
`PaymentServiceClient` remains Activity-owned and independently owns connection
readiness, request tokens, Binder callbacks, and dispatch resources. Production
identifiers use two exact UUID strings per explicit attempt; deterministic tests
inject fixed values. The accepted identifier pair is retained through processing
and terminal display. Terminal observations must match that pair and the current
processing phase before they can change UI state.

PNX-015 token/connection correlation remains authoritative. Duplicate callbacks,
old request tokens, mismatched identifiers, and callbacks detached during cleanup
cannot complete a later request. PNX-014 recovery may restore V2 connectivity but
never replays a payment. Local not-ready admission stays at confirmation. Transport
loss, malformed results, request rejection, or lifecycle abandonment are displayed
as non-business failures and never become a decline or processing-error outcome.

Activity stop abandons local callback ownership. If it occurs during processing,
Merchant reports an unknown/abandoned local result and does not claim remote
cancellation. Ordinary configuration recreation retains the ViewModel state, but
the stopped client cannot continue waiting for its abandoned request. Process death
starts a fresh flow. There is no persistence, timeout, cancellation protocol,
durable retry, or durable idempotency enforcement.

The Payment Service remains a stateless synthetic demonstration: positive minor
units modulo three produce approved, declined/unspecified, or failed/processing
error. No bank/acquirer authorization occurs. Service-to-Server HTTP integration,
networking, and persistence remain deferred. See the
[PNX-016 verification strategy](docs/engineering/testing-strategy.md#merchant-payment-flow-verification-pnx-016).
Runtime/device verification is pending and reserved for Harun + ChatGPT.

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

## Merchant Cashier Flow

Merchant launches through
`MainActivity -> PayNexusTheme -> MerchantApp -> AmountEntryRoute -> AmountEntryScreen`.
The feature belongs to `com.paynexus.merchant.feature.amountentry` inside
`:apps:merchant`; no extra feature module is needed. Merchant explicitly depends
on `:payment:domain` to construct `PaymentAmount(Money(minorUnits, CurrencyCode.TRY))`.
Design System remains independent and supplies theme, spacing, and action buttons.

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
including identical text, clears local confirmation. Confirming displays the exact
canonical amount but does not invoke Binder. A separate **Start payment** action
generates caller-owned IDs and delegates to the Activity-owned client. Accepted
submissions enter Processing; validated results remain distinct from local
transport/protocol failure. **New payment** returns to empty amount entry.

State survives ordinary configuration recreation but starts empty after process
recreation. Leaving the foreground during Processing abandons local callback
ownership and reports transport uncertainty without replay. There is no networking,
persistence, durable retry, or automatic payment replay in Merchant.

Android conventions use compile SDK 37, minimum SDK 26, and Java 17. Install SDK
Platform 37. Applications retain target SDK 36. Merchant directly declares stable
Lifecycle ViewModel and ViewModel Compose 2.11.0. There is no feature StateFlow,
coroutine scheduling, or direct coroutine dependency.

Build and test locally with:

```bash
./gradlew :apps:merchant:test
./gradlew :apps:merchant:assembleDebug
```

Parser, formatter, ViewModel flow, identifier ownership, and stale-event behavior
have JVM tests. Follow the PNX-016 manual checklist only during the separate
Harun + ChatGPT runtime pass. Compilation does not establish visual or runtime
Binder correctness.
