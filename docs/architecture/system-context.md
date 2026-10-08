# PayNexus System Context

## Overview

PayNexus is a modular payment terminal simulation composed of three independently running components:

1. Merchant Android Application
2. Headless Android Payment Service
3. Kotlin Payment Server

The architecture intentionally separates user interaction, on-device payment orchestration, and remote payment processing.

## High-Level Flow

```text
Merchant Android Application
        |
        | Android Binder / AIDL IPC
        v
Headless Android Payment Service
        |
        | HTTP
        v
Kotlin Payment Server
```

The Merchant Application and Payment Service are independently installed Android
APKs. Payment Service is headless and protected by a signature-level bind
permission. Merchant never communicates with Payment Server directly; Payment
Service owns remote payment orchestration, and Payment Server provides the
synthetic HTTP API and local SQLite persistence.
