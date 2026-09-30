plugins {
    id("paynexus.android.application")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.paynexus.paymentservice"

    defaultConfig {
        applicationId = "com.paynexus.paymentservice"
    }
}

dependencies {
    implementation(project(":payment:contract"))
    implementation(project(":payment:domain"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.test.junit)
}
