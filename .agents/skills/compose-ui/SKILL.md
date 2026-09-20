---
name: compose-ui
description: Use for Jetpack Compose screens, UI state, navigation, adaptive layouts, accessibility, previews, interactions, and Compose-specific behavior. Do not use for backend-only changes or reusable design-token work that belongs primarily to the design-system skill.
---

# Compose UI

## Use When

Use this skill when a task affects:

- Jetpack Compose screens
- UI state rendering
- Compose navigation
- user interaction
- adaptive layouts
- loading/error/success states
- accessibility
- previews
- side effects
- screen-level event handling
- responsive behavior
- Compose UI testing

## Goals

- Build deterministic and testable UI.
- Keep business logic outside Composables.
- Keep UI state explicit.
- Support responsive layouts.
- Preserve accessibility.
- Make important screen states easy to preview and test.

## UI Architecture

Screen-level Composables should render state and emit events.

Prefer a structure such as:

```text
Route / Screen container
    |
    v
State holder / ViewModel
    |
    v
UI state
    |
    v
Stateless content Composable
