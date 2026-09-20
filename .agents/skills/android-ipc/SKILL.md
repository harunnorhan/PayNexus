---
name: android-ipc
description: Use for AIDL, Binder, Android Service binding, Parcelable IPC models, exported service configuration, IPC permissions, process boundaries, and contract versioning. Do not use for ordinary in-process interfaces or backend-only communication.
---

# Android IPC

## Use When

Use this skill when a task affects:

- AIDL
- Binder
- bound Android Servicess
- cross-application communication
- cross-process communication
- Parcelable IPC models
- service binding
- service connection lifecycle
- Binder death
- IPC permissions
- exported service configuration
- API/contract versioning
- caller validation

## Goals

- Keep Merchant Application and Payment Service truly independent.
- Expose a small and stable IPC contract.
- Handle cross-process failures explicitly.
- Prevent implementation details from leaking across the process boundary.
- Keep IPC safe, versioned, and testable.

## Architectural Invariant

The Merchant Application and Payment Service are separate Android applications.

Communication must follow:

```text
Merchant Application
        |
        | Binder / AIDL
        v
Payment Service
