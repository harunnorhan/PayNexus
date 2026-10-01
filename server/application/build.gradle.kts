plugins {
    id("paynexus.kotlin.jvm.library")
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.paynexus.server.application.ApplicationKt")
}

dependencies {
    implementation(project(":server:domain"))
    implementation(project(":server:infrastructure"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
