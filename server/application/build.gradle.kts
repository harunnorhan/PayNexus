plugins {
    id("paynexus.kotlin.jvm.library")
    application
}

application {
    mainClass.set("com.paynexus.server.application.ApplicationKt")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
