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
The Payment Service shell implements the version query. PNX-013 adds the Merchant
binding client with manual cross-application verification. PNX-014 adds deterministic
Merchant connection-policy tests, runtime version validation, Binder death monitoring,
and bounded recovery. PNX-015 adds V2 mapping, request-policy, and synthetic Service JVM tests.
PNX-017 adds a deterministic Ktor in-process test for the Payment Server health
foundation. Automated IPC integration, persistence, payment-server API, and
end-to-end test infrastructure remain future work. PNX-011 through PNX-016
sections below preserve historical procedures and evidence; they do not establish
PNX-017 runtime verification.

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

### Server

Keep server domain and application rules in JVM unit tests. Test Ktor routes,
request validation, response mapping, and idempotency with controlled test
infrastructure when introduced. PNX-017 uses Ktor's in-process test host to verify
the application module and deterministic `GET /health` response without binding a
real port. Database integration tests belong with the owning persistence
implementation. Do not make ordinary unit tests depend on real network access.

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

## Payment Server Application Foundation Verification (PNX-017)

PNX-017 adds one focused JVM test in `:server:application`. It loads the same
`Application.module()` used by the runnable bootstrap through Ktor's in-process
test host and verifies that `GET /health` returns HTTP 200, JSON with UTF-8, and
the exact deterministic body:

```json
{"status":"ok","service":"paynexus-payment-server"}
```

The test starts no external server process, binds no network port, and requires no
database or downstream service. It does not prove Netty socket behavior,
deployment configuration, TLS, proxy behavior, production availability,
Service-to-Server transport, or payment processing.

Use JDK 17 and the committed Gradle Wrapper from the repository root:

```bash
./gradlew :server:application:test --rerun-tasks
./gradlew :server:application:build
./gradlew :server:domain:build :server:infrastructure:build
./gradlew spotlessCheck
./gradlew detekt
./gradlew qualityCheck
./gradlew build
./gradlew :server:application:dependencies --configuration runtimeClasspath
./gradlew :server:application:dependencies --configuration testRuntimeClasspath
./gradlew :server:domain:dependencies --configuration runtimeClasspath
git diff --check
```

Inspect the server application XML and HTML test reports for actual counts and
failures. Inspect runtime and test dependency graphs for the intended Ktor
dependencies and confirm that `:server:domain` remains free of Ktor and Android.
Manual/external server verification is deferred and must not be inferred from JVM
tests or build success.

### PNX-017 Local Non-Runtime Verification Record

On 2026-09-29, Codex completed the approved local JVM/build verification on
`feature/PNX-017-payment-server-foundation`:

- the final fresh `:server:application:test --rerun-tasks` run executed one test
  with zero failures, errors, or skips, confirmed in the XML report;
- `:server:application:build` produced the application JAR, start scripts, and
  ZIP/TAR distributions with
  `com.paynexus.server.application.ApplicationKt` as the main class;
- the focused domain/infrastructure builds, `spotlessCheck`, `detekt`,
  `qualityCheck`, and repository `build` passed;
- the final `qualityCheck` reported 186 actionable tasks and the final repository
  build reported 421 actionable tasks;
- runtime and test dependency reports resolved Ktor 3.6.0, with only
  `ktor-server-core` and `ktor-server-netty` declared for production and
  `ktor-server-test-host` plus the existing Kotlin/JUnit alias declared for tests;
  Ktor's graph selected Kotlin stdlib 2.3.21 while repository compilation remained
  on Kotlin plugin 2.2.10, and compilation/tests/build completed successfully; and
- the `:server:domain` runtime graph contains only its existing pure Kotlin project
  dependencies and Kotlin stdlib, with no Ktor or Android dependency.

Initial verification exposed and fixed the Ktor 3.6 test API difference, the
missing explicit UTF-8 response parameter, an application-distribution collision
from the unused domain dependency, and one Spotless layout finding. The final
implementation removes that unused application-to-domain dependency; no quality
rule, suppression, test, compiler, AGP, Gradle, or repository-wide tool version
was weakened or changed.

No external server process, manual socket request, browser, curl, Android runtime,
emulator, device, or adb verification was performed. No commit, push, Pull Request,
merge, GitHub settings change, or remote CI verification was performed.

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
PNX-012 supplies the Service implementation and PNX-013 adds the Merchant client
and Activity-owned binding lifecycle. Compilation and
in-process fakes cannot prove remote marshalling, process death, permission
enforcement, or cross-process correctness. PNX-013 through PNX-015 must introduce appropriate runtime coverage
as those boundaries exist: real binding, unavailable service, version mismatch,
disconnect/death, permissions, and request/response transport.

Remote `CI / Quality and Build` results and required-check enforcement remain
separate verification after authorized commit/push/PR work. Local compilation
does not establish remote CI success or runtime Binder correctness.

## Payment Service Shell Verification

PNX-012 adds a headless Android bound Service with one private generated
`IPaymentService.Stub` implementation. It returns `PaymentIpcContract.CURRENT_VERSION`
and consumes only `:payment:contract`. The manifest exports the Service with the
signature permission `com.paynexus.paymentservice.permission.BIND_PAYMENT_SERVICE`,
owned only by Payment Service, and no intent filter or custom process.
At the PNX-012 baseline Merchant remained unbound; PNX-013 now requests the
permission and owns the binding client. There is no payment
transport, networking, persistence, DI, or direct coroutine infrastructure.

The task-specific strategy is compilation, artifact/manifest/dependency inspection,
and regression of the four existing contract compatibility tests. No Service JVM
test, Robolectric, mocks, or new instrumentation infrastructure is added solely
for a constant-returning Stub. Direct in-process invocation cannot establish
cross-application binding, component resolution, permission enforcement, process
separation, or disconnect/death behavior. Real integration coverage belongs with
the Merchant client in PNX-013/PNX-014; payment transport follows in PNX-015.

Using JDK 17 and SDK Platform 37, run:

```bash
./gradlew :apps:payment-service:assembleDebug
./gradlew :apps:payment-service:lint
./gradlew :payment:contract:test --rerun-tasks
./gradlew spotlessCheck
./gradlew detekt
./gradlew qualityCheck
./gradlew build
./gradlew :apps:payment-service:dependencies --configuration debugRuntimeClasspath
./gradlew :apps:payment-service:dependencies --configuration releaseRuntimeClasspath
git diff --check
```

Inspect the actual contract XML reports for method counts, failures, errors, and
skips; record fresh/cached/up-to-date task execution. Discover merged manifests
and the debug APK under `apps/payment-service/build/` rather than assuming AGP
output paths. Verify the effective Service class, `exported="true"`, exact bind
permission, and its single `signature` declaration. Confirm no Service intent
filter, launcher, custom process, Internet/foreground-service permission, or
unexpected application-owned component. Distinguish library-generated entries.

Inspect the APK manifest, class inventory, and resources: the Service and generated
contract must be packaged, without Merchant code, payment transport, Compose,
network/persistence stacks, or sensitive configuration. Inspect both runtime
dependency graphs for `:payment:contract` and absence of payment domain, Merchant,
design system, server, Lifecycle UI, networking, persistence, and DI dependencies.
Review the source diff for secrets, signing material, and caller/payment logging.

If a local device/emulator is available:

```bash
./gradlew :apps:payment-service:installDebug
adb shell dumpsys package com.paynexus.paymentservice
```

Record device/API, installation result, and installed Service/permission metadata.
Do not create a temporary client. Package inspection does not prove binding or
signature-permission enforcement. Report exact commands/results, discovered
artifact paths, dependency findings, and limitations. Cross-app runtime correctness
remains unverified until real client tests exist. Remote `CI / Quality and Build`
and required-check enforcement remain separate checks after authorized Git work.

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
9. Inspect manifest, source, and runtime logs for absence of payment initiation,
   network behavior, or raw-input logging; Activity-owned binding is expected.
   Record device/API, keyboard, exercised scenarios, and anything unavailable.
   Synthetic data only.

This checklist is not a claim that all runtime or preview scenarios were observed.
Record actual evidence with each implementation report; JVM tests cannot establish
IME, inset, lifecycle recreation, or TalkBack correctness. Remote
`CI / Quality and Build` and required-check enforcement must be observed later,
after separate commit/push/PR authorization.

## Merchant Binding Foundation Verification (PNX-013)

The client adds Android framework ownership rather than independent Kotlin rules.
No artificial state helper, mocked Context tests, Robolectric, instrumentation
runner, or dependency is introduced. Existing Merchant and contract JVM tests
remain unchanged. Manual real-device integration is the task-specific choice;
JVM regression and compilation cannot prove Binder lifecycle or authorization.

Run fresh `:apps:merchant:test --rerun-tasks` and
`:payment:contract:test --rerun-tasks`, inspect their XML reports separately, and
record test methods, failures, errors, skips, and task execution/cache status.
Run both app `assembleDebug` tasks, Merchant `lint`, repository `spotlessCheck`,
`detekt`, `qualityCheck`, `build`, and `git diff --check`. Inspect Merchant debug
and release runtime dependency reports for design-system, payment-domain, and
payment-contract only as direct project dependencies, with no Service implementation,
server, networking, persistence, or new DI dependency.

Discover actual merged manifests and APK paths under each application's build
outputs. Merchant must request, not declare, the bind permission and must not
contain the Service implementation component or new Internet/foreground-service
permission. Preserve the launcher and distinguish library/debug contributions.
Payment Service must retain its exported component, sole signature permission
ownership, and no intent filter. Compare actual APK signing identities locally;
do not store signing keys, certificates, or fingerprints in the repository.

Use `adb devices -l`, select a development device, install both app debug APKs,
and inspect `pm path`, `dumpsys package`, and Service runtime state. Prefer debugger
breakpoints to observe Activity start, one bind attempt, onServiceConnected, and
the non-null generated interface. Background/foreground twice and verify cleanup
and fresh binding without duplicate-bind or invalid-unbind crashes. Exercise
recreation and rapid backgrounding while binding where practical. On a disposable
environment, test Service absence, disconnected state, no retry, and usable amount
entry, then restore the Service and verify binding again. Do not leave diagnostic
logging or invoke the remote version method solely for this verification.

PNX-014 retains compatibility negotiation, explicit Binder death monitoring,
reconnect policy, richer failure state, and deeper lifecycle recovery. PNX-015
retains asynchronous payment transport. No payment operation crosses Binder and
Service-to-Server communication remains absent. Report unexecuted scenarios and
never infer runtime binding from package installation or source inspection.

### PNX-013 Local Verification Record

Observed on 2026-09-27 using JDK 17 and Medium_Phone (`emulator-5554`, API 37,
`sdk_gphone16k_arm64`), started with `-read-only -no-snapshot-save`:

- Fresh Merchant `testDebugUnitTest`: 67 methods, zero failures/errors/skips;
  fresh contract `testDebugUnitTest`: 4 methods, zero failures/errors/skips.
  Each focused `--rerun-tasks` invocation executed its tasks, not cached tests.
  No release unit-test execution is claimed.
- Both debug assemblies, Merchant lint, Spotless, Detekt, qualityCheck, and full
  build passed. The combined successful invocation had 40 executed and 371
  up-to-date tasks. An initial Spotless layout failure in the new client was fixed
  with the Merchant Kotlin formatter before rerunning the checks.
- Debug/release runtime dependency reports contain the three intended project
  dependencies and no Service implementation, server, network, persistence, or
  new DI stack. Existing transitive Compose/Lifecycle coroutines are unchanged.
- Both debug APK signatures verified with SDK build-tools 36.1.0 `apksigner`;
  their signing certificate identities matched. No signing material is recorded.
- Both APK installations succeeded. Package-manager metadata showed the Service's
  signature permission and Merchant's requested/granted permission. Service dumps
  showed the exact explicit component and applied permission in a separate process.
- With Service absent, a JDK debugger attached through ADB/JDWP observed the false
  bind result cleanup path, then `Disconnected` with null registration and proxy.
  No automatic bind loop was observed. UI automation entered synthetic `12.34`
  and confirmed `Amount ready: TRY 12.34` / `No payment has been started.`
- After installing Service, debugger breakpoints observed `onServiceConnected`,
  a non-null `IPaymentService.Stub.Proxy`, and `Connected`. Two subsequent
  background/foreground cycles cleared registration/proxy on stop and acquired
  distinct new connection/proxy objects on start. Service dumps showed no remaining
  binding after cleanup and one registration while connected.
- Debugger invocation of bind while connected retained the same registration;
  repeated unbind after cleanup completed harmlessly. Rotation recreated the
  Activity/client and acquired a fresh proxy. No crash appeared in the crash buffer.
  No temporary application code, logging, or version-method invocation was used.

Inspected merged manifests at
`apps/{merchant,payment-service}/build/intermediates/merged_manifests/{debug,release}/process{Debug,Release}Manifest/AndroidManifest.xml`.
Merchant requests but does not define the Service permission, has no Service
component, and adds no Internet or foreground-service permission. Its launcher
remains unchanged; PreviewActivity is debug-only and the AndroidX dynamic-receiver
permission is a library contribution. Service retains its exported component,
signature permission, and no intent filter.

Unverified: permission denial with incompatible signing, dead/null-binding callback
fault injection, stale-callback delivery, bind repetition while still Binding,
rapid background during Binding, and process-death recovery. Automated Binder
instrumentation remains absent. This verifies the exercised development path,
not comprehensive permission enforcement or Binder resilience. Remote CI and
repository settings were not changed or verified by this local work.

## Binder Compatibility and Recovery Verification (PNX-014)

`PaymentConnectionPolicyTest` adds deterministic JVM coverage for the Merchant-local
connection policy: acquisition versus readiness, strict version acceptance,
terminal no-retry outcomes, opaque attempt/session ownership, duplicate events,
one recovery per started interval, stale results, stop-before-recovery, and reset
only on a genuine new lifecycle interval. The policy contains no Android objects;
these tests do not prove platform binding, exception delivery, death registration,
permission enforcement, or cross-process marshalling. Existing amount-entry and
contract tests remain intact. No mocking, coroutine, or instrumentation dependency
is introduced.

The production client keeps Android resources private, links death before querying,
and runs the synchronous query on `paynexus-contract-version`, never on main.
Only supported compatibility establishes `Ready`. Death/disconnect/binding-died
and already-dead Binder failures permit one recovery; false bind, missing Service,
permission denial, null binding, incompatible versions, generic query failures,
and worker rejection do not. Duplicate loss events cannot create another recovery.
Stop invalidates current work and recovery; destroy closes the worker.

Use JDK 17 and SDK Platform 37 from the repository root:

```bash
./gradlew :apps:merchant:test --rerun-tasks
./gradlew :payment:contract:test --rerun-tasks
./gradlew :apps:merchant:assembleDebug
./gradlew :apps:payment-service:assembleDebug
./gradlew :apps:merchant:lint
./gradlew spotlessCheck
./gradlew detekt
./gradlew qualityCheck
./gradlew build
./gradlew :apps:merchant:dependencies --configuration debugRuntimeClasspath
./gradlew :apps:merchant:dependencies --configuration releaseRuntimeClasspath
git diff --check
```

Inspect fresh XML/HTML reports for exact method counts, failures, errors, skips,
and executed/cached/up-to-date status. Inspect merged debug/release manifests and
dependency graphs for preserved permissions, explicit application boundaries, and
absence of new network, persistence, DI, retry, or worker-library dependencies.
Remote CI and required-check enforcement are separate from local verification.

### Runtime Procedure

Use a disposable development emulator with explicit serial selection; never
uninstall packages or kill processes on the owner's primary device. Discover
actual APK paths after assembly and select `PNX_DEVICE`, `PNX_SERVICE_APK`, and
`PNX_MERCHANT_APK` accordingly:

```bash
adb devices -l
adb -s "$PNX_DEVICE" install -r "$PNX_SERVICE_APK"
adb -s "$PNX_DEVICE" install -r "$PNX_MERCHANT_APK"
adb -s "$PNX_DEVICE" shell pm path com.paynexus.paymentservice
adb -s "$PNX_DEVICE" shell pm path com.paynexus.merchant
adb -s "$PNX_DEVICE" shell am start -n com.paynexus.merchant/.MainActivity
adb -s "$PNX_DEVICE" shell dumpsys activity services com.paynexus.paymentservice
```

Use debugger observation rather than permanent application diagnostics. Observe
one explicit bind, a linked death recipient, `CheckingCompatibility`, worker-thread
version query returning 1, and main-thread `Ready`. Background/foreground twice,
check release/fresh attempt identities, and rotate to exercise disposal/recreation.
Repeated bind and unbind calls must not duplicate registrations or crash.

While Merchant remains started, obtain the actual Service PID and kill only that
verified PID on the disposable emulator, where the debug package permits it:

```bash
adb -s "$PNX_DEVICE" shell pidof com.paynexus.paymentservice
adb -s "$PNX_DEVICE" shell run-as com.paynexus.paymentservice kill -9 "$PNX_SERVICE_PID"
```

Verify the old PID disappeared. Observe the death recipient, proxy cleanup, and
at most one new bind/handshake. Kill the recovered Service again in that same
started interval and verify no third attempt. Background/foreground restores a
fresh budget. Exercise stop while recovery is queued where debugger/platform
ordering permits; verify stale recovery does not rebind after stop. `am force-stop`
is a different package-stop scenario, not ordinary process-death evidence;
`am kill` may leave a foreground-bound process alive. Do not infer death from
command success alone. No `pm clear` is needed.

For absence, background Merchant, uninstall only Payment Service on the disposable
emulator, then foreground Merchant. Verify explicit unavailability and no retry
loop. Reinstall Service and perform a stop/start to reconnect. Enter synthetic
`12.34` and confirm the local amount-ready/no-payment text in both ready and
unavailable conditions. Inspect source, effective manifests, and dependencies for
absence of payment transport/networking; log silence is not sufficient proof.

Unsupported versions, null binding, permission denial, generic RemoteException,
link/death races, and stale callback delivery need separate evidence. Do not add
production fault-injection hooks or modify the Service solely to exercise them.
List each unexecuted runtime case explicitly. JVM policy events are not actual
Android fault injection.

### Known Runtime Limits

Cancellation removes queued tasks and rejects late results, but cannot guarantee
termination of an in-flight synchronous Binder transaction. No handshake deadline
is implemented. A hung transaction may occupy the single worker and delay a later
query; lifecycle stop still invalidates ownership and releases binding. A destroyed
client's blocked worker may remain until the transaction ends, without retaining
Activity Context. `Ready` means compatibility was confirmed with no observed death;
it is not a future liveness guarantee or authorization check. Payment transport,
payment timeout/retry semantics, and Service-to-Server networking remain future work.

### PNX-014 Local Verification Record

Observed on 2026-09-27 with OpenJDK 17.0.17 and Medium_Phone (`emulator-5554`,
API 37), launched for this task with `-read-only -no-snapshot-save`:

- Fresh Merchant `testDebugUnitTest`: 87 methods (20 connection policy, 48 parser,
  14 ViewModel, 5 formatter), zero failures/errors/skips. The final focused
  `--rerun-tasks` run executed all 64 actionable tasks.
- Fresh contract `testDebugUnitTest`: 4 methods, zero failures/errors/skips;
  the focused `--rerun-tasks` run executed all 21 actionable tasks. XML and HTML
  reports were inspected. No release unit-test execution is claimed.
- Both debug assemblies, Merchant lint, Spotless, Detekt, qualityCheck, and full
  build passed together: 30 executed and 381 up-to-date actionable tasks. Initial
  formatting, function-count, return-count, and line-length findings were fixed in
  source; no quality configuration or suppression changed. `git diff --check`
  passed. These are local results, not remote CI evidence.
- Fresh debug/release Merchant dependency reports retained design-system,
  payment-contract, and payment-domain as the direct project dependencies.
  Existing transitive coroutines remain 1.9.0 and Lifecycle remains 2.11.0;
  no coroutine declaration or new library was added. No network, persistence,
  DI, retry, or WorkManager stack was introduced.
- Both APKs installed successfully. Debug/release merged manifests preserve the
  sole Service-owned signature permission, Merchant's permission request, exported
  explicit Service, and no Internet/foreground-service permission. Merchant's
  AndroidX dynamic-receiver permission remains a library contribution.
- A host-side JDI debugger observed real `IPaymentService.Stub.Proxy` acquisition,
  successful death linking, `CheckingCompatibility`, execution of `readVersion`
  on `paynexus-contract-version`, the typed version result 1, and main-thread
  transition to `Ready`. No diagnostic application logging or production hook
  was added; temporary debugger utilities were outside the repository.
- Two background/foreground cycles released the Service registration and obtained
  fresh attempt/session identities. Service dumps showed no binding after stop.
  Rotation observed old-client `close()` and a new client reaching `Ready`.
- Ordinary process death was induced with `run-as ... kill -9` for the verified
  Service PID 4373. `ps -p 4373` confirmed it disappeared. The debugger observed
  the recipient on a Binder thread and main-thread cleanup/recovery. One new
  attempt reached `Ready` in the same session with its recovery allowance used;
  the new Service PID was 4598. Killing 4598 and confirming its disappearance
  left `Unavailable(ConnectionLost)`, no active attempt/proxy, no pending recovery,
  and no third bind. Subsequent lifecycle restart restored connectivity.
- Stop during recovery was attempted with debugger-assisted Home navigation.
  The first probe synchronously waited for input while suspending main and caused
  an input-dispatch ANR; Android killed the debugged Merchant. This was not a
  passing application scenario. After correcting the host probe and restarting
  Merchant, recovery ran before Android delivered `onStop`; stop then cleared
  the connection, with no post-stop rebind. The exact stop-before-queued-recovery
  ordering remains runtime-unverified, with deterministic JVM coverage only.
- With Merchant backgrounded, Payment Service was uninstalled only from the
  disposable emulator. Foregrounding Merchant observed the false-bind cleanup
  path, `Unavailable`, and null active resources without retry. UI automation
  confirmed synthetic `12.34` as `Amount ready: TRY 12.34` and
  `No payment has been started.` Service was reinstalled; a later fresh Merchant
  lifecycle reached `Ready` with a linked Binder. The same amount confirmation
  was observed again while ready. No exception appeared in the crash buffer;
  the debugger-induced ANR above is recorded separately.

Unverified at runtime: incompatible version, null binding, incompatible-signature
permission denial, generic version-query RemoteException, exact already-dead/link
race, package-update `onBindingDied`, delayed stale compatibility result, deliberate
old ServiceConnection delivery, exact stop-before-queued-recovery ordering, repeated
explicit bind/unbind/close invocation, and hung-query/worker-saturation behavior.
Racing duplicate loss notifications were observed; that does not establish every
stale-event interleaving. JVM policy tests cover decisions without claiming Android
fault injection. No instrumentation infrastructure, server communication, payment
transport, production signing policy, or comprehensive Binder resilience is claimed.

## Asynchronous Payment Transport Verification (PNX-015)

PNX-015 uses JVM tests and local non-device build/artifact verification only.
Codex must not run adb, install tasks, emulator/device interaction, or debugger
runtime verification for this task. Harun + ChatGPT own the separate manual
checklist below. No runtime IPC success or remote CI success is inferred.

Contract tests accept only V2 and reject old, negative, extreme, and future values.
Six additional bounds tests exercise missing primitive bytes, invalid sizes, UTF-16
prefix/terminator/padding calculations, null prefixes, truncated string sizes, and
large-length overflow prevention. They test the production arithmetic helper, not
Android Parcel calls; real malformed-Parcel delivery remains runtime-unverified.
Merchant mapping tests cover exact identifiers, 256-code-unit bounds, positive
Long extremes, explicit wire outcomes, and malformed results. Request-policy tests
cover readiness, one active request, immediate completion, duplicate and stale
callbacks, reused identifiers, mismatches, transport failures, abandonment, and
connection recovery without replay. Existing connection-policy tests retain all
PNX-014 scenarios with V2 expectations; amount-entry and domain tests are preserved.
Service tests cover null/blank/oversized request fields, nonpositive amounts,
noncanonical currencies, exact mapping/echoing, and deterministic modulo outcomes.
No new mocking, Parcelize, instrumentation, or test-support framework is introduced.

Tests call production mapping/policy logic with ordinary values, never Parcel or
Binder methods. They cannot prove marshalling, callback delivery, Android threading,
process death, permission enforcement, or real lifecycle cleanup. Generated code
and artifact inspection provide compilation/static evidence only.

### Local Non-Device Commands

Run from the repository root with JDK 17 and SDK Platform 37:

```bash
./gradlew :payment:domain:test --rerun-tasks
./gradlew :payment:contract:test --rerun-tasks
./gradlew :apps:merchant:test --rerun-tasks
./gradlew :apps:payment-service:test --rerun-tasks
./gradlew :payment:contract:assembleDebug
./gradlew :apps:merchant:assembleDebug
./gradlew :apps:payment-service:assembleDebug
./gradlew :payment:contract:lint
./gradlew :apps:merchant:lint
./gradlew :apps:payment-service:lint
./gradlew spotlessCheck
./gradlew detekt
./gradlew qualityCheck
./gradlew build
./gradlew :payment:contract:dependencies --configuration debugRuntimeClasspath
./gradlew :payment:contract:dependencies --configuration releaseRuntimeClasspath
./gradlew :apps:merchant:dependencies --configuration debugRuntimeClasspath
./gradlew :apps:merchant:dependencies --configuration releaseRuntimeClasspath
./gradlew :apps:payment-service:dependencies --configuration debugRuntimeClasspath
./gradlew :apps:payment-service:dependencies --configuration releaseRuntimeClasspath
git diff --check
git status --short
git diff
```

Inspect fresh XML/HTML test reports for actual method counts, failures/errors/skips,
and distinguish executed, cached, and up-to-date tasks. Assembly compiles AIDL.
Discover generated Java, AAR/APK, and merged manifest paths after assembly. Verify
query transaction offset 0, appended submission offset 1, one-way request/callback
flags, manual Parcelable creators and field symmetry, packaged contract classes,
unchanged explicit signature boundary, and no Internet permission or new component.
Inspect debug/release dependency graphs for the approved Service domain dependency
and existing test library only; contract remains domain-independent.

### Manual Runtime Checklist — Harun + ChatGPT Only

All PNX-015 device/runtime cases are pending. Use a disposable development environment
and matching V2 apps. No production diagnostics or automatic payment UI is needed.

1. Confirm separate application processes and V2 readiness. On Merchant's main
   thread in Android Studio's debugger, invoke the Activity-owned client's
   `submitPayment(PaymentId(...), IdempotencyKey(...), PaymentAmount(...))` with
   synthetic values. Resume execution before waiting; inspect `submissionState`.
2. Submit 300, 301, and 302 TRY minor units individually. Observe a real remote
   proxy, exact Service receipt, asynchronous callback, and approved/declined/failed
   mapping. No bank/acquirer operation occurs. Dispatch return is not completion.
3. Repeat with mixed-case/padded identifiers, values above Int.MAX_VALUE, and
   Long.MAX_VALUE. Confirm exact values through both process boundaries.
4. Submit again while pending: verify local rejection and no second dispatch.
   Delay a callback, stop/restart or rotate Merchant, and verify old delivery cannot
   complete a newer request, including reused identifier strings. Exercise duplicate
   delivery where controlled debugger ordering permits it.
5. Exercise Service loss while pending: uncertain transport failure, at most one
   connection recovery per started interval, and no payment replay. Background and
   foreground again to verify fresh ownership. Record unexercised races explicitly.
6. Pair V2 Merchant/V1 Service and V1 Merchant/V2 Service: incompatibility and no
   payment dispatch. Test absent Service and permission denial separately where
   available; matching-signature success does not prove rejection enforcement.
7. Exercise invalid request/result and remote rejection using controlled debugger
   inputs where practical. Confirm protocol failure never becomes a domain decline.
8. Confirm amount entry still displays local amount-ready/no-payment confirmation.
   Inspect permissions and logs for absence of new networking or payload logging.

Record device/API, app versions, event ordering, and every unavailable scenario.
One-way calls, malformed parcels, missing callbacks, death races, and cancellation
need real runtime evidence. No callback deadline or durable idempotency exists.
Service-to-Server integration and remote CI remain separate future verification.

### PNX-015 Local Non-Device Verification Record

Final verification continued on 2026-09-28 using OpenJDK 17.0.17 and SDK Platform 37, starting from
clean branch `feature/PNX-015-payment-ipc-transport` at `92c42e7`:

| Suite | Test methods | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Payment domain (`test`) | 26 | 0 | 0 | 0 |
| Contract (`testDebugUnitTest`) | 10 | 0 | 0 | 0 |
| Merchant (`testDebugUnitTest`) | 105 | 0 | 0 | 0 |
| Payment Service (`testDebugUnitTest`) | 9 | 0 | 0 | 0 |

Fresh focused `--rerun-tasks` commands above passed. Their invocations executed
10, 21, 64, and 49 actionable tasks respectively. XML and HTML reports were
inspected. Merchant retains 67 amount-entry tests and 20 connection-policy tests,
plus 12 request-policy and 6 mapping tests. Service has 6 mapping and 3 synthetic
processor tests. Contract has 4 compatibility and 6 bounds tests: 150 methods total
across the four suites. Table cases are not counted as additional JUnit methods.
No release-unit-test execution is claimed.

All three `assembleDebug` and `lint` commands passed, as did `spotlessCheck`,
`detekt`, `qualityCheck`, and `build`. Verification used incremental tasks where
applicable: the final qualityCheck had 4 executed/181 up-to-date tasks; build had
28 executed/386 up-to-date tasks. Initial Detekt line-length,
return-count, condition-complexity, and class-function-count findings were fixed
in source. Scoped Kotlin Spotless apply tasks formatted the three affected modules.
No suppression, baseline, quality configuration, test weakening, or CI change was
introduced. Final diff review and `git diff --check` passed.

All six debug/release runtime dependency reports passed inspection. Contract has
only Kotlin library support; Merchant keeps design-system, contract, and domain
as direct project dependencies. Service now consumes contract and domain, with
existing core modules transitively. Its only new test declaration uses the existing
`kotlin.test`/JUnit alias. There is no new external library, Parcelize plugin,
networking/persistence/DI stack, or server client. Existing Merchant transitive
AndroidX/Compose coroutine and serialization support is unchanged.

Generated debug/release Java under
`payment/contract/build/generated/aidl_source_output_dir/` preserves version-query
transaction offset 0 and appends submission at offset 1. Submission and both
callback methods use `FLAG_ONEWAY` and no reply Parcel. Manual Parcelable source
read/write order and types match. Final review found that unchecked primitive
reads could default a missing result reason to zero. The final readers check every
primitive width and each string's prefix/padded UTF-16 size before decoding, with
overflow-safe Long size arithmetic and decoded length/position checks. Fixed-message
BadParcelableException rejects incomplete encodings; application mapping still
rejects null/invalid semantic fields. This retains the approved wire layout.
These are source/JVM/static findings, not evidence of actual malformed-Parcel
execution. Both AARs under `payment/contract/build/outputs/aar/`
package generated interfaces/Stubs/proxies, transport classes and creators; their
manifests contain no components or permissions, and classes are contract-only.
Generated default classes are compiler output, not registered fallback behavior.

Merged debug/release manifests under each app's
`build/intermediates/merged_manifests/` retain the sole Service-owned signature
permission, Merchant's request, and the existing explicit exported Service.
Offline `aapt2 dump permissions` and `dexdump` inspection of debug/release APKs
under each app's `build/outputs/apk/` confirmed no Internet permission, packaged
contract interfaces/parcelables/creators, and no opposite application implementation
classes. Merchant's existing AndroidX components and debug-only PreviewActivity
remain library contributions. No manifest or signing configuration changed.
Source/diff inspection found no payment payload logging, secrets, credentials,
signing material, floating-point money, or direct Merchant-to-Server path.

**Runtime/device verification was not performed and remains PENDING.** No adb,
install task, emulator interaction, physical-device interaction, or debugger runtime
verification was executed. JVM tests and artifact inspection do not prove real
cross-process delivery, marshalling, lifecycle races, or permission enforcement.
No commit, push, PR, GitHub settings, or protection-rule changes were made; remote
CI success is not claimed. Server integration remains deferred.

## Merchant Payment Flow Verification (PNX-016)

PNX-016 extends the existing Merchant JVM layer without introducing mocking,
coroutine-test, Compose instrumentation, navigation-test, or device-test
infrastructure. `AmountEntryViewModelTest` preserves PNX-010 parsing, formatting,
confirmation, immutable snapshot, and Long-boundary coverage. Its payment-flow
tests inject deterministic identifier pairs and a lightweight submission function;
they do not mock Android framework or Binder calls.

The flow tests cover local confirmation without submission, exact confirmed amount
and identifier submission, accepted Processing admission, not-ready/already-active/
invalid local rejection, duplicate action suppression, all three domain outcomes,
transport/protocol separation, lifecycle abandonment, exact identifier matching,
stale and duplicate terminal observations, reset, and fresh identifiers for the
next explicit attempt. Existing request-policy tests retain PNX-015 token,
connection-attempt, mismatch, duplicate callback, cleanup, and no-replay coverage;
their terminal transport observations now also assert exact request identifiers.

These tests establish repository-owned orchestration only. They do not establish
real cross-process marshalling, Activity lifecycle ordering, permission enforcement,
visual correctness, callback delivery, or process-death behavior. Activity stop
abandons local request ownership; remote cancellation is not claimed. A silent
live Service can leave Processing active until lifecycle cleanup because there is
no callback deadline. Process death starts a fresh flow because no persistence is
introduced.

### Local Non-Device Commands

Use JDK 17 and SDK Platform 37 from the repository root:

```bash
./gradlew :payment:domain:test --rerun-tasks
./gradlew :payment:contract:test --rerun-tasks
./gradlew :apps:merchant:test --rerun-tasks
./gradlew :apps:payment-service:test --rerun-tasks
./gradlew :payment:contract:assembleDebug
./gradlew :apps:merchant:assembleDebug
./gradlew :apps:payment-service:assembleDebug
./gradlew :payment:contract:lint
./gradlew :apps:merchant:lint
./gradlew :apps:payment-service:lint
./gradlew spotlessCheck
./gradlew detekt
./gradlew qualityCheck
./gradlew build
./gradlew :payment:contract:dependencies --configuration debugRuntimeClasspath
./gradlew :payment:contract:dependencies --configuration releaseRuntimeClasspath
./gradlew :apps:merchant:dependencies --configuration debugRuntimeClasspath
./gradlew :apps:merchant:dependencies --configuration releaseRuntimeClasspath
./gradlew :apps:payment-service:dependencies --configuration debugRuntimeClasspath
./gradlew :apps:payment-service:dependencies --configuration releaseRuntimeClasspath
git diff --check
git status --short
git diff
```

Inspect XML/HTML test reports for actual methods, failures, errors, and skips, and
record executed/cached/up-to-date task state. Assembly and artifact inspection must
confirm the unchanged V2 generated transaction layout, one-way submission/callback
flags, packaged contract classes, exact signature permission boundary, explicit
Service, absence of Internet permission, and absence of opposite-application code.
Dependency reports must retain the existing project direction and contain no new
network, persistence, DI, coroutine, navigation, retry, or test framework.

### PNX-016 Local Verification Record

On 2026-09-28, Codex completed the approved non-device verification scope on
`feature/PNX-016-merchant-payment-flow`:

- fresh JVM runs passed for payment domain (26 tests), payment contract (10),
  Merchant (116), and Payment Service (9), with zero failures, errors, or skips;
- contract, Merchant, and Payment Service debug assembly and lint tasks passed;
- `spotlessCheck`, `detekt`, `qualityCheck`, and the repository `build` passed;
- all six debug/release runtime dependency reports completed without a new direct
  dependency or a new network, persistence, DI, retry, navigation, or test stack;
- generated AIDL retained transaction slots 0/1 and one-way submission/result/
  rejection calls; debug/release merged manifests retained the explicit
  signature-permission boundary and contained no Internet permission;
- the contract AAR and both debug APKs contained the shared V2 contract classes,
  while neither APK contained the other application's implementation class; and
- final source/diff inspection found no payment payload logging, secrets,
  credentials, floating-point money, production data, direct Merchant-to-Server
  path, or changes to manifests, Gradle dependencies, AIDL, payment domain, or
  Payment Service production code.

These results are local evidence only. They do not establish device/runtime IPC,
visual behavior, lifecycle race behavior, process death, or permission enforcement.
No commit, push, pull request, GitHub setting, or remote CI state was created or
changed.

### Manual Runtime Checklist — Harun + ChatGPT Only

Codex must not run adb, an emulator/device, installation tasks, Android Studio
debugging, or runtime Binder scenarios for PNX-016. Runtime verification remains
pending for Harun + ChatGPT:

1. Establish V2 readiness with matching Merchant and Payment Service builds.
2. Exercise TRY 3.00 -> Confirmation -> Processing -> Approved.
3. Select New payment, then exercise TRY 3.01 -> Processing -> Declined.
4. Select New payment, then exercise TRY 3.02 -> Processing -> Failed.
5. Verify repeated Start payment actions cannot submit a second active request.
6. Exercise Service-unavailable and connection-loss behavior without crash,
   fabricated outcome, or automatic replay.
7. Restore Service connectivity and verify only a new explicit payment submits.
8. Where practical, verify an old result cannot mutate a later flow.
9. Verify amount entry works after New payment and across applicable recreation.
10. Inspect logs for absence of payment payload and identifier logging.

Record device/API, app versions, scenario ordering, and unavailable cases. Synthetic
outcomes are not bank/acquirer authorization. Service-to-Server integration,
persistence, remote CI, and production security remain separate future work.
