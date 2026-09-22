plugins {
    id("paynexus.android.library")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.paynexus.designsystem"
    compileSdk = 37

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.runtime)
    api(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}

// Compose UI functions use PascalCase; retain all other default quality rules.
detekt {
    config.setFrom(files("detekt.yml"))
}

// Limit the canonical Compose naming allowance to this module's Kotlin sources.
spotless {
    kotlin {
        clearSteps()
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootProject.file(".editorconfig"))
            .editorConfigOverride(mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"))
    }
}
