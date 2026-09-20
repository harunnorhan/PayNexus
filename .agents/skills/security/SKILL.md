---
name: security
description: Use for trust boundaries, permissions, secrets, networking security, exported Android components, request validation, sensitive data handling, signing, and payment-security assumptions. Use whenever a change affects data crossing application or network boundaries.
---

# Security

## Use When

Use this skill for:

- trust boundaries
- Android permissions
- exported components
- Binder access control
- network security
- secrets
- credentials
- logging review
- input validation
- signing
- configuration security
- payment data handling
- threat-model changes

## Goals

- Keep PayNexus security-aware.
- Prevent sensitive data leakage.
- Validate all trust boundaries.
- Keep secrets outside source control.
- Avoid exaggerated production-security claims.

## Security Position

PayNexus is a payment-system simulation.

It must not claim:

- PCI DSS certification
- banking certification
- production acquiring readiness
- production cryptographic compliance

Security controls must be described accurately.

## Synthetic Data Only

Only synthetic payment data may be used.

Never use real:

- PAN
- CVV
- PIN
- bank credentials
- customer financial data
- production payment keys

## Secrets

Never commit:

- API keys
- passwords
- private keys
- signing credentials
- tokens
- database credentials

Secrets belong in environment-specific secure storage.

Local secret files must be gitignored.

## Logging

Never log sensitive payment information.

Safe logging may include:

- transaction ID
- correlation ID
- terminal ID
- merchant ID
- result status
- latency

Do not log raw payment payloads unless they are guaranteed synthetic and non-sensitive, and even then prefer structured safe fields.

## Trust Boundaries

Treat each boundary as untrusted input.

Important boundaries include:

```text
Merchant App
-> Payment Service

Payment Service
-> Payment Server

External configuration
-> Application

HTTP request
-> Server
