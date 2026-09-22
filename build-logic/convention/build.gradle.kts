plugins {
    `kotlin-dsl`
}

group = "com.paynexus.buildlogic"

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "paynexus.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }

        register("androidCompose") {
            id = "paynexus.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }

        register("androidLibrary") {
            id = "paynexus.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }

        register("kotlinJvmLibrary") {
            id = "paynexus.kotlin.jvm.library"
            implementationClass = "KotlinJvmLibraryConventionPlugin"
        }

        register("codeQuality") {
            id = "paynexus.code.quality"
            implementationClass = "CodeQualityConventionPlugin"
        }
    }
}
