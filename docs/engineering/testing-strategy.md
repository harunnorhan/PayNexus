# Testing Strategy

## Current Foundation

PayNexus currently has deterministic Kotlin/JVM unit tests in `:payment:domain`,
using `kotlin.test` with the existing JUnit-compatible dependency. Two internal
test-source helpers, `declinedPaymentOutcome` and `failedPaymentOutcome`, share
outcome setup between outcome and lifecycle tests. They delegate directly to
domain constructors and accept explicit reason or failure overrides.

There is no dedicated shared testing module or test convention plugin. Android,
IPC, persistence, server, and end-to-end test infrastructure described below is
future direction, not implemented capability.

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

### Android — Future

Use local tests for ViewModel and Flow/StateFlow behavior where framework access
is unnecessary. Use Android tests for lifecycle-sensitive behavior, navigation,
and Compose interactions or accessibility that require the platform. Keep
Android-specific rules and helpers out of pure Kotlin payment test support.

### IPC — Future

Use instrumentation and integration tests for AIDL contracts, service binding,
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

## Merchant Shell Verification

PNX-009 adds a launchable Compose Activity and neutral shell, with no feature
interaction, business state, or navigation. Its approved verification consists of
compilation, lint, static analysis, debug previews, and manual runtime inspection.
No Compose instrumentation or screenshot infrastructure is introduced. This limited
strategy applies only to this shell; PNX-010 must choose automated behavioral tests
when interaction/state appears. Existing domain tests remain unchanged.

Use JDK 17 and SDK Platform 37. Compile SDK is 37; both applications retain explicit
target SDK 36 and minimum SDK 26. Verify regenerated manifests rather than assuming
these values from successful compilation.

```bash
./gradlew -p build-logic :convention:build
./gradlew :apps:merchant:assembleDebug :design-system:assembleDebug :apps:payment-service:assembleDebug
./gradlew :apps:merchant:lint
./gradlew spotlessCheck
./gradlew detekt
./gradlew qualityCheck
./gradlew build
./gradlew :apps:merchant:dependencies --configuration debugRuntimeClasspath
./gradlew :apps:merchant:dependencies --configuration releaseRuntimeClasspath
git diff --check
```

Inspect manifests for the intended launcher, label, window theme, SDKs, permissions,
and exported components. Inspect dependency reports for Activity 1.13.0, Compose
BOM alignment, and absence of preview tooling from release runtime. AndroidX
contributes a signature-protected dynamic-receiver permission, a non-exported
startup provider, and a profile-install receiver protected by `android.permission.DUMP`.
Debug tooling also contributes an exported `PreviewActivity`; it must be absent
from release. These library manifest entries are distinct from the single
application-owned launcher Activity. No network permission is requested.

Render all six `MerchantShellPreviews` scenarios in Android Studio: light, dark,
narrow, wide, large font, and landscape. Inspect wrapping and clipping. Preview
compilation alone does not establish that rendering was inspected.

On an available local device/emulator:

1. Run `./gradlew :apps:merchant:installDebug`.
2. Find the icon and **PayNexus Merchant** label in the launcher and tap it.
3. Confirm startup without a crash or framework action bar; inspect the Compose
   text, Design System typography/spacing, and themed background.
4. Repeat in light/dark mode, normal/2x font scale, narrow portrait, landscape,
   and a wider window where available. Check scrolling and text wrapping.
5. Check status/navigation bars and cutouts, including gesture and three-button
   navigation where available. Important content must remain within safe insets.
6. With TalkBack, check text reading order, title heading, and scrolling.
7. Record device/API and checks actually performed. Report unavailable scenarios
   as pending; installation alone is not launch verification.

These are verification instructions, not a claim that runtime or preview inspection
has occurred. Remote `CI / Quality and Build` still requires separately authorized
PR work and observation of the completed run and required-check enforcement.
