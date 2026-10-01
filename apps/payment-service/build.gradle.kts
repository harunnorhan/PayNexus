plugins {
    id("paynexus.android.application")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.paynexus.paymentservice"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.paynexus.paymentservice"
    }

    buildTypes {
        debug {
            buildConfigField("String", "PAYMENT_SERVER_BASE_URL", "\"http://10.0.2.2:8080\"")
        }
        release {
            buildConfigField("String", "PAYMENT_SERVER_BASE_URL", "\"\"")
        }
    }
}

dependencies {
    implementation(project(":payment:contract"))
    implementation(project(":payment:domain"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.test.junit)
}
