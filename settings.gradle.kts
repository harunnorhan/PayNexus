pluginManagement {
    includeBuild("build-logic")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PayNexus"

include(
    ":apps:merchant",
    ":apps:payment-service",
    ":design-system",
    ":core:model",
    ":core:domain",
    ":payment:contract",
    ":payment:domain",
    ":server:application",
    ":server:domain",
    ":server:infrastructure",
)
