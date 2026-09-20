---
name: gradle-modularization
description: Use for Gradle setup, convention plugins, version catalogs, module creation, build logic, dependency wiring, build variants, toolchains, and modularization decisions. Do not use for isolated feature logic that does not change build configuration or module structure.
---

# Gradle Modularization

## Use When

Use this skill when a task affects:

- Gradle project structure
- `settings.gradle.kts`
- root `build.gradle.kts`
- version catalogs
- convention plugins
- build logic
- module creation
- plugin configuration
- dependency wiring
- Android build types
- product flavors
- JVM toolchains
- Kotlin compiler configuration
- dependency locking or verification
- shared Gradle configuration

## Goals

- Keep the build reproducible.
- Keep build configuration centralized.
- Minimize duplicated Gradle configuration.
- Make module responsibilities explicit.
- Keep dependency direction consistent with architecture.
- Make local and CI builds behave the same way.
- Keep the project easy to understand and maintain.

## Core Principles

### Use the Gradle Wrapper

All project Gradle commands must use:

```text
./gradlew
