# ADR-0003: Represent Monetary Values Using Integer Minor Units

## Status

Accepted

## Context

PayNexus models financial transactions.

Monetary values require exact representation.

Floating-point types such as `Float` and `Double` cannot reliably represent decimal financial values because of binary floating-point precision behavior.

The project needs a representation that is deterministic, testable, and safe for payment calculations.

## Decision

All monetary amounts will be represented using integer minor units.

Examples:

- TRY 10.00 -> 1000
- USD 12.50 -> 1250
- EUR 99.99 -> 9999

The amount value will use an integer type such as `Long`.

Currency must be represented explicitly.

A domain value object such as `Money` will encapsulate amount and currency rules.

`Float` and `Double` are forbidden for monetary domain values.

## Alternatives Considered

### Double

Rejected because floating-point precision behavior is inappropriate for exact monetary representation.

### Float

Rejected for the same reason as Double and with lower precision.

### BigDecimal Everywhere

BigDecimal can represent decimal monetary values accurately.

However, for the PayNexus domain, integer minor units provide simpler serialization, comparison, persistence, and interoperability.

BigDecimal may still be appropriate at specific conversion or external integration boundaries if required in the future.

## Consequences

### Positive

- Exact monetary representation.
- Simple equality comparison.
- Predictable serialization.
- Simple persistence.
- Easy interoperability across Android and server components.
- Reduced risk of rounding bugs.

### Negative

- Currency-specific minor unit rules must be respected.
- Display formatting requires conversion from minor units.
- Future currencies with unusual decimal rules require explicit handling.

## Architectural Constraint

No payment domain API may expose monetary values as `Float` or `Double`.
