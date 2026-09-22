# Design System

## Purpose and ownership

`:design-system` is an Android Compose library in `com.paynexus.designsystem`.
It owns reusable visual primitives, not Merchant screens or payment behavior.
It has no project-module dependencies. It must not depend on payment domain or
contract models, either Android application, or server modules. ViewModels,
navigation, repositories, networking, persistence, and IPC do not belong here.
Future Merchant UI may depend on this library; it is not integrated yet.

## Build and dependencies

Compose configuration is local to the module, using `paynexus.android.library`
and `org.jetbrains.kotlin.plugin.compose`, with the compiler version following
the catalog Kotlin version. AGP built-in Kotlin remains enabled. Compose library
versions are aligned by the stable official BOM `2026.09.00`; individual Compose
libraries have no version overrides. This module overrides compile SDK to 37,
required by the BOM's Compose AAR metadata; the shared convention remains at 36.
Minimum SDK remains 26 and Java compatibility remains 17. Install Android SDK
Platform 37 for local builds. Future consuming applications must satisfy the
same dependency compile-SDK requirements.

The BOM, runtime, and UI use `api` because public APIs expose Compose annotations,
`Modifier`, and `Dp`. Foundation and Material 3 are implementation dependencies.
Preview annotations and tooling are debug-only. No activity, navigation, font,
network, or persistence library is added directly.

Compose convention-plugin extraction is deferred until another real Compose
consumer exists, to be reevaluated in PNX-009. There is no `paynexus.android.compose`.

## Theme usage

```kotlin
PayNexusTheme {
    PayNexusButton(
        text = "Continue",
        onClick = onContinue,
        modifier = modifier,
    )
}
```

Import `PayNexusTheme` from `com.paynexus.designsystem.theme` and
`PayNexusButton` from `com.paynexus.designsystem.component`. Callers supply
localized labels, callbacks, and layout constraints.

`PayNexusTheme(darkTheme, content)` defaults to system dark-mode selection and
provides Material 3 colors, typography, and shapes. It does not draw a background
or manage system bars. Use a themed surface where the consumer needs a background.
There is no business state in the theme API.

## Colors and typography

Branding is provisional. Internal light and dark color schemes use Material 3
default palettes and semantic roles. Dynamic color is not implemented. There is
no public color wrapper and no approved/declined/processing color vocabulary.
Business status must not be communicated through color alone.

Typography uses default/platform fonts. The explicit initial roles are
`titleLarge` (22 sp / 28 sp line height), `bodyLarge` (16 / 24), and `labelLarge`
(14 / 20); other roles retain Material defaults. Font scaling remains enabled.
There are no bundled or downloaded custom fonts.

## Shapes and spacing

Material 3 shapes define small, medium, and large rounded corners at 8, 12,
and 16 dp. Other roles retain Material defaults. Buttons use the medium shape.

The immutable public `PayNexusSpacing` object provides:

| Token | Value |
| --- | --- |
| xs | 4 dp |
| sm | 8 dp |
| md | 16 dp |
| lg | 24 dp |
| xl | 32 dp |

Prefer these tokens for shared spacing. Accessibility minimum sizes are
constraints, not spacing tokens. No configurable token framework exists.

## Current component

Only `PayNexusButton(text, onClick, modifier, enabled)` exists. It is a stateless
Material 3 text button wrapper with theme styling, horizontal `md` and vertical
`sm` padding. It forwards enabled state and click handling to Material Button.
It has no loading, debounce, icon, navigation, or payment behavior.

It supports multiline labels, growing height, and caller-controlled width.
It does not force full width. Future components require demonstrated reuse;
there is no component framework or planned catalog presented as implemented.

## Accessibility and responsive rules

The button requests a minimum 48 dp width and height and retains Material touch
target and disabled semantics. Consumers must provide sufficient parent space
and must not impose clipping or smaller fixed constraints that defeat these
minimums. Text remains in scalable units with no single-line or fixed-height
restriction. The visible label supplies the accessible name; no redundant
content description is added.

Inspect contrast and disabled clarity in both themes. Verify large fonts,
wrapping, and touch targets in the actual consuming layout. Material defaults
and successful compilation alone do not establish accessibility compliance.

Components respect parent constraints and directional padding. Production code
contains no device dimensions, device-model checks, absolute positioning, or
screen breakpoints. Feature layers own adaptive screen layout.

## Previews

Debug-only previews cover light/dark themes, enabled and disabled buttons,
long text at narrow width, and 2x font scale. Theme samples show representative
text roles and buttons at narrow and wide preview widths. Labels are synthetic,
callbacks are empty, and previews need no domain models or external services.
Preview dimensions are demonstration scenarios, not production assumptions.

Open the preview files in Android Studio and render all previews. Inspect
wrapping, clipping, disabled clarity, spacing, and both palettes. Preview source
compilation does not mean previews have been rendered or visually inspected.

## Verification

Use JDK 17 and the repository Gradle Wrapper:

```bash
./gradlew :design-system:assembleDebug
./gradlew :design-system:lint
./gradlew spotlessCheck
./gradlew detekt
./gradlew qualityCheck
./gradlew build
./gradlew :design-system:dependencies --configuration debugRuntimeClasspath
./gradlew :design-system:dependencies --configuration releaseRuntimeClasspath
git diff --check
```

Compilation (including debug previews), lint, static analysis, manual previews,
and the full build are the primary verification layers. No tests merely assert
static token values or repeat Material's implementation. No screenshot, emulator
CI, or Compose instrumentation infrastructure is introduced. Interaction and
accessibility behavior have no new automated UI coverage in this foundation.
New custom behavior requires reevaluating the appropriate behavioral test level.

The existing `CI / Quality and Build` remains the remote gate. Its result must
be observed on an authorized PR; this document does not claim CI success.

## Quality configuration

Initial Detekt execution reported `FunctionNaming` for `PayNexusTheme` and
`PayNexusButton`. The module-local `detekt.yml` uses the supported
`ignoreAnnotated: ['Composable']` setting only for that rule. Other functions
retain naming checks, all other default rules remain active, and no baseline
or blanket suppression is used. Shared build logic and CI gates are unchanged.

Spotless/ktlint also reported the canonical composable names. A module-local
Spotless editor-config override applies ktlint's `Composable` naming exception
only to this module's Kotlin sources; the function-naming rule remains enabled.
