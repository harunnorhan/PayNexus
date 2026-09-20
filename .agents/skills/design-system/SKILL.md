---
name: design-system
description: Use for reusable Compose UI components, theme tokens, typography, spacing, colors, shapes, icons, component variants, and visual consistency. Do not use for screen-specific business behavior or backend changes.
---

# Design System

## Use When

Use this skill when a task affects:

- reusable Compose components
- theme configuration
- colors
- typography
- spacing
- shapes
- elevations
- icons
- common input components
- buttons
- cards
- status components
- visual tokens
- component variants
- screenshot/golden baselines

## Goals

- Keep visual language consistent.
- Avoid duplicated one-off styling.
- Make UI implementation token-driven.
- Support responsive and accessible interfaces.
- Keep components reusable without over-generalizing them.
- Preserve light and dark theme behavior.

## Design System Ownership

The design system owns reusable visual primitives and components.

Examples:

```text
PayNexusTheme
PayNexusColors
PayNexusTypography
PayNexusSpacing
PayNexusShapes

PayNexusButton
PayNexusTextField
PayNexusAmountField
PayNexusTopBar
PaymentStatusCard
TransactionSummary
