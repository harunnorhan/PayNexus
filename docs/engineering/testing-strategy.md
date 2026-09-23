# Testing Strategy

## Current Foundation

PayNexus currently has deterministic Kotlin/JVM unit tests in `:payment:domain`,
using `kotlin.test` with the existing JUnit-compatible dependency. Two internal
test-source helpers, `declinedPaymentOutcome` and `failedPaymentOutcome`, share
outcome setup between outcome and lifecycle tests. They delegate directly to
domain constructors and accept explicit reason or failure overrides.

Merchant also has local JVM tests for TRY parsing, integer formatting, and
synchronous ViewModel state. There is no shared testing module, mocking library,
Compose instrumentation infrastructure, or emulator CI. The Android IPC contract
has local JVM compatibility-policy tests and compiler/artifact verification.
Runtime IPC, persistence, server, and end-to-end infrastructure remains future direction.

## Philosophy and Naming

Use the lowest test level that verifies observable behavior. Prefer fast domain
and application unit tests, focused integration tests at boundaries, and a small
set of end-to-end tests when the runtime flow exists. Test relevant failure paths
as well as successful behavior. Never weaken assertions or quality gates to make
verification pass.

Use behavioral names, such as `business decline and technical failure remain
distinct`, rather than `test1` or `shouldWork`. Keep each test focused on one
behavioral reason to fail. Arrange, Act, and Assert structure should be clear;
explicit section comments are optional.

## Test Layers

### Domain

Keep value-object, validation, equality, and state-transition tests in the owning
domain module. Exercise constructors directly when construction or rejection is
the behavior under test. Keep boundary amounts, currencies, identifiers, and
expected outcomes visible. Money uses integer minor units and explicit currency;
zero and negative `Money` values are valid, but `PaymentAmount` must be positive.

Current lifecycle tests cover the four legal transitions, all 21 illegal
combinations among the five modeled states, exception source and target, terminal
outcome preservation, and original-state immutability. Preserve this coverage.

### Application — Future

Test use cases, orchestration, cancellation, and failure propagation on the JVM
where possible. Cover duplicate requests and uncertain payment outcomes when
those behaviors exist. Introduce controlled time, identifiers, or coroutine
scheduling only when an actual production abstraction and test require them.
Do not equate a timeout with confirmed payment failure.

### Android

Merchant currently tests synchronous ViewModel behavior locally without framework
access. Use local tests for future Flow/StateFlow behavior where appropriate.
Use Android tests for lifecycle-sensitive behavior, navigation,
and Compose interactions or accessibility that require the platform. Keep
Android-specific rules and helpers out of pure Kotlin payment test support.

### IPC

The contract foundation tests deterministic version compatibility on the JVM and
uses Android library compilation plus generated API/artifact inspection. See
[IPC contract verification](#ipc-contract-foundation-verification).

Future runtime work must use instrumentation and integration tests for service binding,
Binder death, unavailable services, version compatibility, and permission or
input-validation boundaries. In-process fakes cannot establish correctness of
the real cross-process contract.

### Persistence — Future

Test mappings independently where possible. Use appropriate database integration
tests for DAOs, migrations, transactions, and durable idempotency once persistence
exists. Isolate test databases and verify failure behavior without external
production services or data.

### Server — Future

Keep server domain and application rules in JVM unit tests. Test Ktor routes,
request validation, response mapping, and idempotency with controlled test
infrastructure when introduced. Database integration tests belong with the
owning persistence implementation. Do not make ordinary unit tests depend on
real network access.

### End-to-End — Future

Exercise the simulated `Merchant Application -> Payment Service -> Payment Server`
path once implemented, including selected failure scenarios. Preserve the
Payment Service boundary. Any future integration environment must be isolated,
explicitly configured, and use synthetic data; emulator CI and end-to-end tests
are not part of the current foundation.

## Fixture Ownership and Extraction

Keep fixtures local to their owning test source set until there is demonstrated
reuse. The current outcome helpers are internal functions in
`com.paynexus.payment.domain` under `payment/domain/src/test/kotlin`. They contain
no production behavior, validation, normalization, or generated values.

A dedicated `:payment:testing` module is deferred until a real second module
needs reusable payment-domain test support. When justified, its intended
dependency direction is:

```text
:payment:testing
    ->
:payment:domain
```

Future consumer test source sets may depend on `:payment:testing`. Production
modules must never depend on test-support modules. Keep `:payment:domain`'s own
invariant-focused tests local rather than forcing them through a shared module.
Future payment test support must remain pure Kotlin/JVM; platform, transport,
and persistence helpers belong with their respective testing layers.

Use small, specifically named factories with deterministic synthetic defaults
and explicit overrides. Do not hide meaningful business inputs or expected
values. Avoid catch-all utilities, generic builders, and a speculative
`:core:testing` module. Extract shared Gradle testing conventions only when
repeated, stable configuration justifies them; retain explicit module-specific
dependencies.

## Deterministic Execution

Tests must not depend on wall-clock time, arbitrary sleeps, random UUIDs, random
test ordering, local timezone, machine identity, environment-dependent fixtures,
mutable global state, real network access, or external services. Use explicit
values and isolated state. If a specific future test requires randomness, use
and report a controlled seed so the case can be reproduced.

Default identifiers must be obviously synthetic, such as `payment-test-001` and
`idempotency-test-001`. A fixture must preserve domain invariants by using domain
constructors, not by duplicating or bypassing validation. Do not introduce clock,
ID generator, or dispatcher abstractions solely in anticipation of future tests.

## Mocking and Fake Policy

Prefer pure domain values, then real lightweight collaborators, then handwritten
fakes. Use mocks only when a concrete test cannot achieve useful isolation
cleanly otherwise. Avoid interaction assertions that merely mirror implementation.
There is no mocking library in this foundation.

Add a fake only when a current production abstraction and real test need it.
Fakes must model meaningful behavior and remain owned by the relevant tests.
Repository, gateway, clock, ID generator, and dispatcher fakes are not introduced
speculatively.

## Test-Data Safety

Use synthetic data only. Never copy real cardholder data, PAN, CVV, PIN,
credentials, production tokens, or private endpoints into tests, fixtures, or
logs. Preserve validation at trust boundaries. Testing this simulation does not
establish PCI compliance or production payment security certification.

## Local Verification and CI

Use JDK 17 and the committed Gradle Wrapper from the repository root:

```bash
./gradlew :payment:domain:test
./gradlew test
./gradlew spotlessCheck
./gradlew detekt
./gradlew qualityCheck
./gradlew build
git diff --check
```

Inspect `payment/domain/build/reports/tests/test/index.html` and the XML results
under `payment/domain/build/test-results/test/` to confirm test counts, failures,
and skips. Distinguish tasks that actually execute from cached or up-to-date
results. Use `./gradlew :payment:domain:test --rerun-tasks` when fresh execution
is needed to establish verification evidence.

The existing `CI` workflow runs the `Quality and Build` job for Pull Requests
targeting `main`, using JDK 17. It executes `./gradlew qualityCheck` and
`./gradlew build`; the JVM build lifecycle includes unit tests. It does not run
emulator or end-to-end tests. Keep these gates intact and use the same commands
locally. Additional focused local tests supplement these gates.

After an authorized Pull Request is created, observe the actual CI result and
verify required-check enforcement separately. A workflow file alone does not
prove repository protection settings or CI success. Report only commands and
results actually observed, including limitations and skipped or cached work.

## IPC Contract Foundation Verification

PNX-011 adds four local JVM test methods in `:payment:contract`, using the existing
`kotlin.test`/JUnit support. They verify `supports(1)` is true and `supports(0)`,
`supports(2)`, and `supports(-1)` are false. No mocks, Android framework calls,
clock, randomness, or sleeps are involved.

Use JDK 17 and SDK Platform 37. The module inherits minimum SDK 26 and Java 17;
application target SDK decisions remain unchanged.

```bash
./gradlew :payment:contract:test
./gradlew :payment:contract:assembleDebug
./gradlew :payment:contract:lint
./gradlew spotlessCheck
./gradlew detekt
./gradlew qualityCheck
./gradlew build
./gradlew :payment:contract:dependencies
git diff --check
./gradlew :payment:contract:test --rerun-tasks
```

Inspect HTML reports in `payment/contract/build/reports/tests/` and XML in
`payment/contract/build/test-results/`. Report actual method counts, failures,
errors, and skips for `testDebugUnitTest`, which the current AGP configuration
runs through `:payment:contract:test`. The suite contains four methods. Do not
infer release-variant test execution from a successful build; if additional test
variants are configured later, report them separately. Record fresh, cached, or
up-to-date execution; use the focused rerun above for fresh evidence.

After assembly, discover actual generated outputs under `payment/contract/build/`
rather than assuming an AGP-specific directory. Inspect generated
`com.paynexus.payment.contract.IPaymentService` for `getContractVersion()`, its
`Stub`, transaction dispatch, and proxy implementation. Inspect the output AAR
and its `classes.jar` for compiled interface, Stub/proxy support, and
`PaymentIpcContract`. Do not edit generated files or add handwritten substitutes.
Inspect the packaged manifest and complete archive/class inventory for absence
of application components, permissions, Service/Merchant implementations, payment
request/result models, and sensitive configuration. No source manifest is needed
with the current convention and AGP: assembly generates a component-free manifest
with package `com.paynexus.payment.contract` and minimum SDK 26 in the AAR.

Inspect dependency reports, including debug/release runtime classpaths if needed.
The contract must not depend on payment domain, either application, server modules,
Compose, Lifecycle, network, or persistence stacks. Distinguish Kotlin/Android
library support and test-only JUnit dependencies from application dependencies.

No instrumentation, screenshots, or emulator infrastructure is added in PNX-011.
There is no Service implementation, Merchant client, binding, second-process
interaction, or Binder lifecycle. Compilation and in-process fakes cannot prove
remote marshalling, process death, permission enforcement, or cross-process
correctness. PNX-012 through PNX-015 must introduce appropriate runtime coverage
as those boundaries exist: real binding, unavailable service, version mismatch,
disconnect/death, permissions, and request/response transport.

Remote `CI / Quality and Build` results and required-check enforcement remain
separate verification after authorized commit/push/PR work. Local compilation
does not establish remote CI success or runtime Binder correctness.

## Design System Foundation Verification

`:design-system` uses compilation, Android Lint, existing repository quality
checks, and manual Compose preview inspection for its initial theme, tokens,
and thin Material button wrapper. Debug previews cover light/dark themes,
enabled/disabled states, long text, and large font scale. They compile with
`:design-system:assembleDebug`; compilation does not establish visual correctness.

No constant-assertion tests, screenshot infrastructure, emulator CI, or Compose
instrumentation infrastructure are added for this foundation. Automated UI
interaction and accessibility coverage remain future work. Custom behavior must
trigger a fresh testing decision rather than inheriting this limited strategy.
See [Design System verification](../design/design-system.md#verification).

## Merchant Amount Entry Verification

PNX-010 tests `TryAmountParser`, `TryAmountFormatter`, and `AmountEntryViewModel`
with `kotlin.test`/JUnit in the Merchant test source set. Parser coverage includes
whole/fractional values, both separators, leading separators/zeros, empty and
incomplete input, zero, malformed/unsupported input, and exact Long boundaries.
Syntax is validated before overflow classification. Formatting tests include the
smallest and maximum positive amounts, using exact strings and integer values.
ViewModel tests cover canonical TRY construction, validation, enablement, local
confirmation, no-op invalid/repeated confirmation, immutable snapshots, and clearing
confirmation on every edit event, including identical text. Existing domain
invariant tests remain unchanged.

The task-specific testing decision is JVM behavioral tests plus local runtime
interaction checks. The screen is thin wiring over deterministic state; introducing
runner/device-test infrastructure for this screen is disproportionate. This does
not establish automated UI/accessibility coverage or exempt future navigation,
Binder lifecycle, payment-result flows, or complex interactions from reconsidering
Compose instrumentation. No screenshots/goldens or emulator CI are introduced.

Use JDK 17 and SDK Platform 37. Compile SDK is 37, target SDK 36, and minimum SDK 26.

```bash
./gradlew :apps:merchant:test
./gradlew :payment:domain:test
./gradlew :apps:merchant:assembleDebug
./gradlew :apps:merchant:lint
./gradlew spotlessCheck
./gradlew detekt
./gradlew qualityCheck
./gradlew build
./gradlew :apps:merchant:dependencies --configuration debugRuntimeClasspath
./gradlew :apps:merchant:dependencies --configuration releaseRuntimeClasspath
git diff --check
```

Inspect Merchant HTML reports under `apps/merchant/build/reports/tests/` and XML
under `apps/merchant/build/test-results/`, for `testDebugUnitTest` and
`testReleaseUnitTest` when executed. Record test-method counts, failures, errors,
skips, and whether tasks executed freshly, from cache, or were up-to-date. Table
cases are not additional JUnit test methods. Use `:apps:merchant:test --rerun-tasks`
if fresh evidence is needed. Inspect domain reports separately.

Dependency reports must resolve Lifecycle to stable 2.11.0, retain Compose BOM
alignment, and exclude preview tooling from release runtime. Merchant declares no
direct coroutine dependency or asynchronous feature behavior. Inspect for absence
of network/persistence stacks and Payment Service implementation dependencies.
Inspect merged manifests for launcher, SDKs, `adjustResize`, permissions, and
exported components. Existing AndroidX startup/profile-install/dynamic-receiver
entries are library contributions, not new Merchant components. Debug-only
PreviewActivity must remain absent from release; no network permission is intended.

Debug-only `AmountEntryPreviews` cover empty, valid whole, fractional comma,
incomplete, invalid zero/format, overflow, confirmed, dark, narrow, large font,
landscape, and wide scenarios. They pass explicit synthetic state to stateless
content, never instantiate a ViewModel or service. Render them in Android Studio
and inspect clipping/wrapping. Compilation alone is not rendered-preview evidence.

On an available local device/emulator:

1. Run `./gradlew :apps:merchant:installDebug` and open **PayNexus Merchant** from
   the launcher. Confirm startup and amount entry without a framework action bar.
2. Exercise `12`, `12.3`, `12,34`, `.5`, and `,5`; verify enabled confirmation and
   exact canonical two-decimal display after confirmation.
3. Exercise `12.`, zero, empty, negative, malformed and overflow input. Paste or
   use hardware input where the decimal keyboard lacks characters. Confirm no
   crash and disabled confirmation; intermediate copy must remain neutral.
4. Confirm using button and IME; verify local **Amount ready** and
   **No payment has been started.** Editing again must clear confirmation.
5. Check keyboard usability and scrolling so the action remains reachable. The
   screen owns safe-drawing padding once, including IME insets, before token spacing.
6. Repeat in light/dark, normal/2x font, narrow portrait, landscape/reduced height,
   and a wider window where practical. Inspect system bars/cutouts and gesture/
   three-button navigation where available.
7. Rotate while editing and after confirming: ViewModel state should survive
   ordinary configuration recreation. Process recreation intentionally starts empty.
8. With TalkBack where practical, inspect heading, label, reading order, localized
   error semantics, disabled action, and polite ready announcement.
9. Inspect manifest, source, and runtime logs for absence of payment-service/
   network behavior or raw-input logging. Record device/API, keyboard, exercised
   scenarios, and anything unavailable. Synthetic data only.

This checklist is not a claim that all runtime or preview scenarios were observed.
Record actual evidence with each implementation report; JVM tests cannot establish
IME, inset, lifecycle recreation, or TalkBack correctness. Remote
`CI / Quality and Build` and required-check enforcement must be observed later,
after separate commit/push/PR authorization.
