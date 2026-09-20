plugins {
    `kotlin-dsl`
}

group = "com.paynexus.buildlogic"

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "paynexus.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }

        register("androidLibrary") {
            id = "paynexus.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }

        register("kotlinJvmLibrary") {
            id = "paynexus.kotlin.jvm.library"
            implementationClass = "KotlinJvmLibraryConventionPlugin"
        }
    }
}
