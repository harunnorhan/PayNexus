---
name: observability
description: Use for structured logging, correlation IDs, transaction tracing, diagnostics, health endpoints, metrics, latency measurement, and cross-component payment visibility. Do not use for ordinary debug prints.
---

# Observability

## Use When

Use this skill for:

- structured logs
- correlation IDs
- transaction tracing
- request tracing
- latency measurement
- health endpoints
- diagnostics
- server observability
- cross-component tracing
- metrics
- operational troubleshooting

## Goals

- Make payment flows traceable.
- Keep logs safe.
- Support debugging across Merchant, Payment Service, and Payment Server.
- Avoid noisy or sensitive logging.

## Traceability

A logical payment should be traceable across:

```text
Merchant App
    |
    v
Payment Service
    |
    v
Payment Server
