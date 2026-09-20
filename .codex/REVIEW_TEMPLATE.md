# Codex Self-Review Template

## Scope

- Does the diff match the linked task?
- Are there unrelated refactors?
- Is any requested acceptance criterion missing?

## Architecture

- Does Merchant still delegate payment processing to Payment Service?
- Did any framework type leak into domain code?
- Did transport DTOs leak into presentation/domain?
- Are component/module boundaries preserved?
- Is an ADR required?

## Payment Safety

- Is money represented using integer minor units?
- Could retries create duplicate payment operations?
- Are failure states modeled explicitly?
- Is idempotency considered where relevant?

## Android

- Is blocking work kept off the main thread?
- Is lifecycle ownership clear?
- Are Binder/service failure modes handled where relevant?
- Is Compose state deterministic and hoisted appropriately?

## Security

- Any secrets?
- Any real payment data?
- Any sensitive logging?
- Is boundary input validated?

## Testing

- Are behavior changes tested?
- Are failure paths covered?
- Are tests stable and meaningful?
- Were tests actually executed?

## Quality

- Any unexplained suppressions?
- Any new dependency without justification?
- Any dead code or TODO hiding acceptance criteria?
- Did formatting/static analysis run?

## Documentation

- Does README/setup need updating?
- Does architecture documentation need updating?
- Does an ADR need updating or creating?

## Verification Honesty

- Every claimed command was actually executed.
- Any unverified area is stated explicitly.
