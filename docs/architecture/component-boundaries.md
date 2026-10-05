# PayNexus Component Boundaries

## Purpose

This document defines the responsibilities and dependency boundaries of the three main PayNexus components.

The goal is to prevent architectural drift as the repository grows.

## Merchant Android Application

### Owns

- Jetpack Compose user interface
- screen state
- user input
- payment initiation
- payment result presentation
- transaction presentation

### Does Not Own

- HTTP payment processing
- payment gateway communication
- server DTOs
- payment orchestration
- idempotency enforcement
- backend persistence

### Allowed Dependencies

The Merchant Application may depend on:

- payment domain abstractions
- IPC contract models
- design system modules
- merchant-specific feature modules
- shared platform utilities

### Forbidden Dependencies

The Merchant Application must not depend directly on:

- Payment Server client implementations
- Payment Service implementation modules
- server DTOs
- server persistence code

## Headless Android Payment Service

### Owns

- AIDL/Binder implementation
- payment orchestration
- service-side validation
- transaction lifecycle management
- network gateway integration
- local transaction persistence
- transport-to-domain error mapping
- payment retry safety
- idempotency coordination

### Does Not Own

- merchant-facing UI
- Compose screens
- server database schema ownership
- server-side business rules

### Allowed Dependencies

The Payment Service may depend on:

- payment domain abstractions
- IPC contract models
- network infrastructure
- local persistence infrastructure
- logging and observability modules
- shared testing utilities

### Forbidden Dependencies

The Payment Service must not depend on:

- Merchant UI modules
- Compose feature modules
- server implementation modules

## Kotlin Payment Server

### Owns

- HTTP API
- request validation
- idempotency enforcement
- transaction persistence
- deterministic payment simulation
- transaction lookup
- health endpoint
- server-side logging

### Does Not Own

- Android lifecycle
- AIDL contracts
- Compose UI
- Android-specific models

### Allowed Dependencies

The Payment Server may depend on:

- server domain modules
- server application modules
- server infrastructure modules
- shared pure Kotlin domain models where justified

### Forbidden Dependencies

The Payment Server must not depend on:

- Android framework classes
- Merchant UI modules
- Android IPC implementation classes

### Payment Server Application Foundation (PNX-017)

`:server:application` owns the runnable Ktor process bootstrap, application
composition, and HTTP routing. Its initial surface is the unversioned
`GET /health` foundation endpoint, which returns a fixed healthy response without
consulting payment state or external dependencies. The route is intentionally
outside the future versioned payment API.

`:server:domain` remains framework-independent and has no Ktor or Android
dependency. `:server:infrastructure` remains the boundary for future external
adapters; it is not wired into the health endpoint. PNX-017 introduces no payment
DTO, persistence, idempotency, Payment Service client, or Merchant-to-Server path.
Versioned payment transport under ADR-0004 remains deferred.

### Versioned Payment Server API (PNX-018)

`:server:application` owns `POST /v1/payments`, JSON Content Negotiation, transport
DTOs, trust-boundary validation, explicit response mapping, controlled HTTP error
mapping, and the stateless deterministic synthetic processor. Transport models are
separate from payment-domain models. Accepted identifiers remain exact and are
bounded to 256 UTF-16 code units; amounts use positive `Long` minor units and
canonical TRY only.

The application-local accepted request and synthetic outcome types have no Ktor or
serialization dependency. `:server:application` deliberately does not depend on
`:payment:domain` or `:server:domain`: this small API foundation does not justify
reintroducing the existing default `domain.jar` application-distribution collision.
`:server:domain` remains framework-independent and unchanged. Archive restructuring
is outside PNX-018.

The API echoes `paymentId` and `idempotencyKey`, but introduces no durable
idempotency enforcement, persistence, transaction lookup, authentication, retry,
or external integration. `GET /health` remains unchanged. At the PNX-018 baseline,
Payment Service did not own an HTTP client and Merchant remained unaware of the
server API. The only permitted runtime direction remains Merchant -> Payment
Service -> Payment Server.

### Payment Service-to-Server HTTP Client Foundation (PNX-019)

`:apps:payment-service` owns the internal `PaymentServerClient` boundary and its
Ktor implementation. The boundary accepts only Service/domain-owned `PaymentId`,
`IdempotencyKey`, and `PaymentAmount` values. Service-owned HTTP DTOs and explicit
mappers keep JSON separate from the payment domain, Android IPC contract,
Parcelables, and server implementation classes. No new module or server project
dependency is introduced.

The implementation targets exactly `POST /v1/payments`, preserves caller-owned
identifiers and `Long` minor units, and emits canonical TRY. A decoded success must
echo both identifiers exactly before its explicit outcome/reason strings can map to
`PaymentOutcome`. Unknown or contradictory values and correlation mismatches are
protocol failures. HTTP invalid requests, unexpected status codes, decoding
failures, protocol failures, and transport failures remain distinct from confirmed
business outcomes; none manufactures a decline or processing-error outcome.

The client owns one reusable Ktor `HttpClient` created through an explicit factory
and closes only that owned client. Its base URL is supplied by a caller; no
production endpoint is embedded. PNX-019 does not instantiate the client from the
Android Service. `PaymentService.submitPayment()` continues to use the existing
local `SyntheticPaymentProcessor`, so Binder behavior, IPC V2, Merchant correlation,
and connection recovery remain unchanged.

There is no Internet permission, runtime network configuration, retry, replay,
timeout policy, persistence, or durable idempotency. Runtime/device/network
integration is deferred to later Service orchestration work.

### IPC Technical Failure Contract (PNX-020)

`:payment:contract`, Merchant, and Payment Service now use strict IPC V3 with both
version bounds set to 3. The Service transaction order remains version query then
one-way submission. The per-request one-way callback preserves `onResult` and
`onRejected` at their existing positions and appends `onTechnicalFailure`.

The three callback categories have separate semantics. `onResult` carries a
confirmed `PaymentOutcome`; `onRejected(INVALID_REQUEST)` remains reserved for an
invalid Merchant/IPC request; and `onTechnicalFailure(PAYMENT_OUTCOME_UNAVAILABLE)`
means the Service cannot provide a confirmed business outcome. The technical value
does not prove whether downstream processing occurred and never becomes a decline
or `PaymentOutcome.Failed`. Unknown technical codes remain protocol failures.

Merchant routes the technical terminal through its existing request token,
connection ownership, and first-terminal-wins callback receiver. Stale and
duplicate events remain ignored, stop/close still abandons local ownership, and
connection recovery never replays a payment. The existing transport-failure UI
reports a neutral unknown outcome without introducing a payment result.

Payment Service still executes the local `SyntheticPaymentProcessor`; PNX-019's
typed HTTP client remains unwired. PNX-020 introduces no networking, permission,
endpoint, persistence, retry, or durable idempotency behavior. PNX-021 owns future
Service-to-Server production integration. Runtime/device/end-to-end verification
remains deferred.

### Payment Service-to-Server Integration (PNX-021)

`:apps:payment-service` now connects validated V3 Binder requests to its existing
internal `PaymentServerClient`. `PaymentExecutionCoordinator` owns a bounded
single-worker executor with one queued request, exact domain-to-client request
construction, one client invocation per admitted request, terminal mapping,
callback ownership, and shutdown. `PaymentService` owns coordinator creation and
destruction and creates at most one reusable `KtorPaymentServerClient` for its
lifetime. No HTTP work runs on a Binder transaction thread.

Invalid IPC input remains `onRejected(INVALID_REQUEST)`. Only a confirmed
`PaymentServerCallResult.Completed` becomes an IPC result through the existing
`PaymentTransportMapper`. Every unsuccessful client result, missing configured
client, unexpected non-cancellation execution failure, or worker-admission
rejection uses `onTechnicalFailure(PAYMENT_OUTCOME_UNAVAILABLE)`. Callback
ownership is consumed before one terminal attempt; callback failure does not
change category or replay work.

Payment ID, idempotency key, and `PaymentAmount` domain objects pass into the
client unchanged. Money remains positive `Long` minor units with explicit TRY.
The Service performs no automatic retry, replay, durable queuing, persistence, or
durable idempotency enforcement.

Debug builds alone configure `http://10.0.2.2:8080` and a Network Security Config
that denies general cleartext while allowing the emulator host. Release has no
endpoint and constructs no HTTP client, so valid requests fail closed through the
V3 technical terminal. `INTERNET` belongs only to Payment Service; Merchant remains
free of endpoints, HTTP dependencies, server DTOs, and network permission. The
server API and server-side synthetic processor are unchanged.

Service shutdown stops admission, detaches active and queued callback ownership,
removes queued work, interrupts the worker, and closes the reusable client without
waiting indefinitely on Android main. Local interruption or client closure does
not prove that the Payment Server did not receive or process an active request.
Runtime/device/end-to-end verification remains deferred.

### Payment Server Persistence and Durable Idempotency (PNX-022)

`:server:application` now depends on `:server:domain` and
`:server:infrastructure` for Payment Server composition. It continues to own Ktor,
strict HTTP/JSON validation, DTO mapping, database-path configuration, blocking-IO
dispatch, and sanitized HTTP error responses.

`:server:domain` owns the framework-independent accepted payment intent,
deterministic synthetic outcome rule, stored-record model, purpose-specific
repository port, and created/replayed/conflict policy. It has no project, Ktor,
JDBC, SQLite, or Android dependency.

`:server:infrastructure` implements the repository port with SQLite/JDBC. It owns
parent-directory creation, schema bootstrap, connection-per-operation resource
management, a bounded SQLite lock wait, parameterized insert/read statements, and
mapping of durable rows back into validated server-domain records. It depends only
on `:server:domain` among PayNexus projects and no longer depends on
`:server:application`.

The database primary key is the final authority for idempotency-key uniqueness.
The first accepted intent and outcome remain authoritative; equivalent requests
replay the stored record, while a different intent receives HTTP 409 without an
update. This provides local, single-process durability for the current synthetic
processor only. It does not provide distributed idempotency or exactly-once future
external financial execution. Transaction lookup and Android retry/replay remain
outside this boundary.

### Payment Server Durable Payment Lookup (PNX-023)

`:server:application` owns `GET /v1/payments`, extraction and validation of the
single `PayNexus-Idempotency-Key` header value, and stable HTTP mapping. It reuses
the existing payment response DTO and stored-record mapping. Found committed rows
return HTTP 200, valid unknown keys return HTTP 404 `PAYMENT_NOT_FOUND`, invalid
headers return HTTP 400 `INVALID_REQUEST`, and repository failures return the
sanitized HTTP 500 `INTERNAL_ERROR` response.

`:server:domain` extends its purpose-specific repository port with an exact
idempotency-key lookup returning a nullable stored record. It adds no Ktor, JDBC,
SQLite, Android, transport DTO, or speculative lookup-use-case dependency.
`:server:infrastructure` implements the read with parameterized SQLite SQL and the
existing authoritative row reconstruction. The read performs no insert, update,
or outcome calculation, and the schema and payment-ID uniqueness semantics remain
unchanged.

Lookup observes committed state only. It does not wait for an in-flight POST,
poll, retry, replay, or introduce an application-level lock. HTTP 404 means only
that no committed row was visible when the read occurred. POST idempotency
semantics remain unchanged. The endpoint has no Android consumer, and no payment-ID
lookup, list, history, search, authentication, or authorization is introduced.
Runtime/manual end-to-end verification remains deferred.

### Payment Service Outcome Resolution (PNX-024)

`:apps:payment-service` extends its internal `PaymentServerClient` with a typed
lookup that uses the existing client, engine, endpoint configuration, and lifetime.
For one admitted work item, `PaymentExecutionCoordinator` still performs exactly
one POST maximum. Only a typed ambiguous POST failure may lead to one exact-key
`GET /v1/payments`; no path performs another POST, a repeated GET, polling, or
backoff.

Transport failure, HTTP 5xx, and malformed, invalid-outcome, or identifier-
mismatched HTTP 200 responses are lookup-eligible. Invalid request, unexpected
non-5xx status, and every HTTP 409 response are not. Malformed responses retain
their HTTP status so malformed 400 and 409 bodies cannot become eligible through a
generic protocol or transport category. Exact HTTP 409 `IDEMPOTENCY_CONFLICT`
receives a dedicated internal classification. Because lookup omits the original
amount, no 409 response can safely resolve a conflicting intent.

Lookup HTTP 200 reuses the POST response mapper for strict JSON, exact payment-ID
and idempotency-key correlation, and outcome/reason validation. Only exact HTTP 404
`PAYMENT_NOT_FOUND` becomes typed `NotFound`; malformed 404, HTTP failures, and
transport failures remain unsuccessful. `Found` can use the existing V3 result
callback, while every unresolved lookup uses the existing outcome-unavailable
technical terminal.

The single bounded Service worker executes POST and optional GET sequentially.
Callback ownership is checked before lookup and remains authoritative during
shutdown and late completion. IPC V3, Merchant, server production code and schema,
Android networking configuration, and dependencies are unchanged. No explicit
HTTP timeout exists; timeout policy and runtime/manual end-to-end verification
remain deferred.

## Dependency Direction

The intended dependency direction is:

```text
Presentation
    |
    v
Application / Use Cases
    |
    v
Domain
    ^
    |
Infrastructure

```

## Payment Domain Foundation

`:payment:domain` owns `CurrencyCode`, `Money`, `PaymentAmount`, `PaymentId`,
`IdempotencyKey`, `DeclineReason`, `PaymentFailure`, `PaymentOutcome`,
`PaymentState`, and `IllegalPaymentTransitionException` in
`com.paynexus.payment.domain`. No demonstrated cross-domain use currently
justifies promoting these types into `:core:model` or `:core:domain`.
`:payment:contract` remains separate and gains no domain or transport models
from this foundation.

All domain models are immutable and framework-independent. `Money` stores
signed or zero `Long` minor units with explicit currency; `PaymentAmount`
requires a strictly positive quantity. TRY is initially the only supported
currency. `CurrencyCode.fromCode(code)` rejects unsupported, blank, or
noncanonical codes without normalization or fallback.

`PaymentId` and `IdempotencyKey` reject blank values and preserve caller-supplied
values exactly. They neither generate identifiers nor enforce uniqueness or
idempotency. Invalid value construction throws `IllegalArgumentException`
with fixed messages that do not expose supplied values. Constructor validation
also applies when copying a value.

The legal lifecycle transitions are:

- `Created -> Processing`
- `Processing -> Finished(Approved)`
- `Processing -> Finished(Declined(reason))`
- `Processing -> Finished(Failed(failure))`

Every other transition, including self-transitions and all transitions from
terminal states, throws `IllegalPaymentTransitionException` with source and
target states. Transitions return the next immutable state without mutating
the original. This is a domain rule, not a durable or concurrent orchestration
mechanism.

A decline is a confirmed business response; its initial reason is
`DeclineReason.UNSPECIFIED`. A technical failure uses
`PaymentFailure.PROCESSING_ERROR`. Neither contains transport exceptions or
raw error payloads. Validation and transition errors are distinct from these
terminal outcomes. Future integrations must not assume that a timeout or
connection loss proves a payment failed; uncertain remote outcomes require
an explicit design in a later task.

This foundation introduces no payment-card data, serialization, persistence,
networking, Android dependencies, retries, or runtime communication changes.

## IPC Contract Foundation

The following foundation describes PNX-011 through PNX-014. The V2 additions
and current compatibility range are specified in the PNX-015 section below.

`:payment:contract` is an Android library owning the versioned AIDL/Binder contract
in `com.paynexus.payment.contract`. It owns AIDL definitions, generated Binder
APIs, and repository-owned compatibility metadata only. AIDL is enabled locally
in this module. It has no payment-domain or other project-module dependency.
It owns no application component, permission, Service/client implementation,
payment orchestration, networking, persistence, or UI.

V1 exposes only `int getContractVersion()`. `PaymentIpcContract` defines
`CURRENT_VERSION = 1` and `MIN_SUPPORTED_VERSION = 1`; `supports(remoteVersion)`
accepts the inclusive supported range. Version 1 is supported; 0, 2, negative
values, and all other unknown versions are rejected without clamping or direct
server fallback. Future version bumps must explicitly reconsider both constants,
backward compatibility, method availability, and client/service negotiation.
Compatibility negotiation is not authentication or authorization.

The Service returns `PaymentIpcContract.CURRENT_VERSION` from the synchronous
version query without side effects, file IO, network, database, or payment work.
Remote calls still have IPC latency and must not block the client main thread.
Future payment execution remains asynchronous under ADR-0002.

PNX-012 established `:apps:payment-service -> :payment:contract` as the only
application dependency on the contract at that baseline.
`com.paynexus.paymentservice.PaymentService` extends Android `Service`, owns one private immutable anonymous
`IPaymentService.Stub`, and returns it from `onBind()`. The trivial version query
can execute on Binder threads without mutable state or asynchronous work.

The manifest explicitly exports the Service and protects it with
`com.paynexus.paymentservice.permission.BIND_PAYMENT_SERVICE`, defined once by
Payment Service with `signature` protection. This is the shell's caller-admission
boundary for clients with a compatible signing identity. It does not replace
future payment-input validation. No intent filter, custom process, or additional
lifecycle overrides are needed. The application remains headless and bound-only,
without UI, foreground-service behavior, domain operations, networking,
persistence, DI, or direct coroutine infrastructure.

PNX-013 adds `:apps:merchant -> :payment:contract <- :apps:payment-service`.
Merchant requests the existing signature permission without redefining it.
`com.paynexus.merchant.ipc.PaymentServiceClient` centralizes the explicit package
`com.paynexus.paymentservice` and class `com.paynexus.paymentservice.PaymentService`.
The Activity creates one client with application Context, binds in `onStart()`,
and unbinds in `onStop()`. Compose and amount entry do not own the connection.

PNX-014 keeps `PaymentServiceClient` internal and introduces a Merchant-local,
framework-independent `PaymentConnectionPolicy`. The policy owns lifecycle demand,
opaque attempt/session identities, compatibility outcomes, and a single recovery
allowance. The client owns Android registration, Binder/proxy references, death
recipients, and worker resources. None of these Android objects are exposed to UI.
There is no observable Compose connection state or connection UI.

The internal states are `Disconnected`, `Binding`, `CheckingCompatibility`,
`Ready`, `Unavailable(reason)`, and `Incompatible(remoteVersion)`. Acquiring a
Binder is not readiness: the client links a death recipient, then submits exactly
one `getContractVersion()` query for that attempt. `PaymentIpcContract.supports`
remains the compatibility authority. Unsupported versions are retained as an
explicit outcome after unbinding, with no downgrade or automatic retry.

All policy and resource ownership mutations occur on the main thread. A private
Java `ThreadPoolExecutor` has one worker and a queue bounded to one pending query;
its only remote work is the synchronous version transaction. Results are posted
to `Handler(Looper.getMainLooper())` with attempt identity. Binder death callbacks
only mark atomic attempt invalidation/death flags and post to the main owner.
Late results, old ServiceConnection callbacks, and stale recovery actions cannot
replace a newer attempt/session. Repeated connection delivery cannot register a
second death recipient or submit another handshake.

Normal release invalidates the attempt, clears the proxy/Binder, cancels and
removes queued query work, unlinks a successfully registered recipient, and
unbinds the owned ServiceConnection. Confirmed death does not require unlinking
the dead Binder. A death racing normal unlink may return false. Unexpected
registration-ownership exceptions are not broadly swallowed. As in PNX-013,
false bind results and binding SecurityException require cleanup; only rejected
bind cleanup tolerates Android's specific "Service not registered:" exception.

An uninterrupted started lifecycle interval permits one initial attempt and at
most one automatic recovery attempt. `binderDied`, `onServiceDisconnected`,
`onBindingDied`, already-dead Binder during linking, and `DeadObjectException`
during the query share this loss path. Recovery consumes its allowance before
posting a fresh bind and does not replenish it on `Ready`. There is no delay,
backoff, polling, or third attempt. False bind results/missing Service, permission
failures, null binding, incompatible versions, generic query failures, and worker
rejection are terminal for that interval. A genuine stop/start permits retry.

`onStop()` clears demand before cleanup and invalidates pending recovery/results.
Repeated bind within the interval and repeated unbind are harmless. `onDestroy()`
closes the client idempotently and shuts down the worker without waiting on main;
recreation creates a new client. Only application Context is retained. Cancelling
local work does not guarantee interruption of a synchronous Binder transaction.
There is no handshake deadline; a hung call can occupy the worker and delay a
later query. `Ready` does not guarantee future process liveness.

## Asynchronous Payment Transport (PNX-015)

The required runtime direction remains Merchant -> Payment Service -> Payment
Server. This task implements only the first boundary. ADR-0002 remains unchanged;
ordinary AGP AIDL is used, with no Stable AIDL/frozen-interface framework.

The contract owns `PaymentRequestParcel`, `PaymentResultParcel`, their AIDL
parcelable declarations, generated interfaces, and explicit integer wire values.
It has no domain or application dependency. Both apps map at their own boundary;
Payment Service adds a dependency on `:payment:domain`, which remains pure Kotlin.
Neither app depends on the other application's implementation.

V2 preserves the original query descriptor and transaction offset 0, then appends
one-way `submitPayment`. The callback exposes one-way `onResult` and `onRejected`.
Both version constants are 2. V1 Merchant rejects V2 Service, and V2 Merchant
rejects V1 Service. V2 accepts only V2; future versions require explicit review.
Readiness after the existing off-main handshake is the payment method gate.
There is no downgrade, registered default fallback, or client-version authentication.

Requests carry exact caller Payment ID, idempotency key, Long minor units, and
canonical currency. Identifiers are nonblank and at most 256 UTF-16 code units;
rejection never trims, replaces, normalizes, or truncates. Domain constructors
validate positive `PaymentAmount` and canonical TRY. The IPC size limit does not
change domain identifier semantics. Result fields echo both identifiers and carry
explicit outcome/reason pairs: approved `(1, 0)`, declined/unspecified `(2, 1)`,
failed/processing error `(3, 2)`. Enum ordinals are never serialized. Missing,
unknown, contradictory, or mismatched values cannot become valid domain outcomes.
Request rejection code 1 means invalid request, separately from processing failure.
Manual readers check each string prefix and padded UTF-16 payload size, then verify
decoded length/position. Primitive reads require their full byte width. Truncated
fields throw a fixed-message BadParcelableException before a default integer can
be accepted. Explicit null strings remain invalid inputs for application mapping.
These checks preserve the existing field order and writeString/readString format;
they do not add a new wire envelope or domain rule.

Merchant's `PaymentRequestPolicy` owns one active request token and local state.
`PaymentServiceClient` owns the generated callback and dispatch resources; the
same bounded worker dispatches version queries and payment submission off main.
The callback captures request and connection identities, consumes only its first
terminal event, and posts to main for ownership and payload validation. The active
request exists before dispatch, so an immediate callback is safe. Exact returned
identifiers must match, but identifiers alone never establish callback ownership.
A later submission may reuse the same strings without accepting an old callback.
The callback's receiver is detached during cleanup even if a peer retains its Binder.

A second active request is rejected without queuing. Not-ready or incompatible
connections cannot submit. Worker rejection is a local pre-dispatch transport
failure; generic dispatch failure may have uncertain delivery. Binder death or
loss terminates local waiting without manufacturing a domain result. PNX-014's
bounded connection recovery is unchanged and never replays payments. Stop/close
abandons pending ownership before connection cleanup; late events cannot affect
new requests. No timeout or durable idempotency is provided. A silent live peer can
leave one request pending until cleanup; remote execution cannot be cancelled by
local abandonment. A parcel decoding failure before callback dispatch can also
prevent delivery and is not a confirmed payment failure.

The Service validates/maps the request, computes positive minor units modulo 3,
and returns approved for 0, declined/unspecified for 1, failed/processing error
for 2. This is only deterministic synthetic transport behavior. Stateless bounded
validation/arithmetic execute on the Binder dispatch thread, with one callback
attempt and no callback registry, executor, persistence, or retained payment job.
Missing callbacks cannot receive rejection; invalid requests with a usable callback
receive rejection. Callback RemoteException ends delivery without retry.

The signature permission and explicit component remain unchanged. No payment
payload logging, network permissions, HTTP client, server processing, UI payment
trigger, domain framework dependency, or production-security claim is introduced.
Amount confirmation remains local. Deterministic JVM tests verify owned logic;
real Binder marshalling/lifecycle/permission behavior remains manual and pending.

## Design System Foundation

`:design-system` owns reusable Compose theme configuration, spacing tokens,
and `PayNexusButton` in `com.paynexus.designsystem`. It has no project-module
dependencies. Merchant consumes it through an explicit production dependency.

The library must not depend on `:payment:domain`, `:payment:contract`, either
Android application, or any server module. It owns no screen state, ViewModel,
navigation, repository, IPC, network, persistence, or payment orchestration.
Features map business state to presentation outside the design system.
See the [Design System guide](../design/design-system.md) for its current APIs.

## Merchant Amount Entry

`:apps:merchant` owns launcher `MainActivity` in `com.paynexus.merchant`,
`MerchantApp` in `com.paynexus.merchant.ui`, and the amount-entry feature in
`com.paynexus.merchant.feature.amountentry`. The Activity installs `PayNexusTheme`;
the root delegates through `AmountEntryRoute` to stateless `AmountEntryScreen`.
The obsolete placeholder shell is removed. The screen owns its themed surface,
one safe-drawing inset boundary, and scrolling for constrained height/IME use.
The Activity explicitly requests `adjustResize` so IME insets can reach the
edge-to-edge content, including on older supported Android versions.

Merchant explicitly depends on `:design-system` and `:payment:domain`. The pure
`TryAmountParser` converts approved ASCII decimal input to Long minor units using
overflow guards before arithmetic. Both dot and comma mean a decimal separator;
at most two fractional digits are allowed. The ViewModel creates positive
`PaymentAmount(Money(minorUnits, CurrencyCode.TRY))` candidates through the existing
domain constructors. Zero remains a numeric parse result but cannot be confirmed.
`TryAmountFormatter` formats canonical amounts with integer division/remainder.
No domain constructors or Design System APIs change.

`AmountEntryViewModel` owns immutable presentation snapshots through synchronous
Compose observable state. It retains only the canonical candidate, not a parallel
minor-unit field. Confirmation retains a local selected amount; every edit event,
even identical text, clears confirmation. Configuration recreation retains state;
process recreation starts empty. No persistence or asynchronous work is introduced.

Local confirmation is not payment initiation, authorization, or a payment result.
No identifiers, payment lifecycle models, network, or persistence are introduced
by amount entry. Activity-owned Service binding does not initiate a payment.
Design System owns no amount entry, domain models, or window/inset behavior.
The mandatory payment path remains Merchant -> Payment Service -> Server.

## Merchant Payment Flow (PNX-016)

PNX-016 connects the existing local confirmation to the existing V2 transport
without changing the AIDL contract, Payment Service behavior, or component
dependency direction. The runtime path remains Merchant -> Payment Service;
Service-to-Server communication remains deferred.

`AmountEntryViewModel` now owns the Merchant application flow: editing, local
confirmation, processing, terminal domain result, terminal transport uncertainty,
and new-payment reset. It owns the canonical confirmed `PaymentAmount` and the
exact caller-generated `PaymentId`/`IdempotencyKey` pair for the displayed attempt.
Its state contains domain and Merchant presentation values only. It never retains
Context, `PaymentServiceClient`, Binder objects, generated AIDL interfaces, or
Parcelable transport models.

The Activity still owns one `PaymentServiceClient`. It binds on start, unbinds on
stop, and closes on destroy. The Activity passes a narrow submission function to
the ViewModel and forwards identifier-bearing terminal transport observations.
Connection readiness remains private to `PaymentConnectionPolicy`; it is not a
payment-flow state. `PaymentRequestPolicy` remains the authority for active request
tokens, connection-attempt identity, duplicate callback consumption, exact echoed
identifier validation, transport mapping, and callback cleanup.

Local confirmation never submits. A separate cashier action creates one fresh
UUID-backed Payment ID and idempotency key and asks the existing client to submit
the already-confirmed amount. Only `Accepted` admission enters Processing. Local
not-ready, already-active, or invalid-request admission remains at Confirmation
with a non-business error. No admission or transport error creates a
`PaymentOutcome`.

The ViewModel accepts a terminal observation only while Processing and only when
both exact identifiers match its owned attempt. It retains the accepted pair
through terminal display. New payment clears the amount, identifiers, result, and
presentation ownership but never submits or changes connection ownership. IPC
token identity remains stronger than identifier equality: an old token cannot
complete a later request even if strings are reused, and the UI guard prevents an
old Activity/client observation from changing a later flow.

Connection loss ends local waiting and may trigger the unchanged PNX-014 bounded
connection recovery. Recovery restores connectivity only and never submits a
payment. Activity stop abandons and detaches a pending callback; Merchant reports
an unknown local result without claiming remote cancellation. Configuration
recreation retains ViewModel state, but an abandoned client request is not resumed.
Process death starts a fresh flow. No timeout, cancellation protocol, persistence,
durable retry, or durable idempotency enforcement is introduced.

The Payment Service remains the existing stateless synthetic processor. Positive
minor units modulo three produce approved, declined/unspecified, or
failed/processing-error outcomes. These results demonstrate transport only and are
not bank or acquirer authorization. The signature bind permission, explicit
component, absence of Merchant Internet permission, and absence of payload logging
remain unchanged.
