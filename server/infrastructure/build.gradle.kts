plugins {
    id("paynexus.kotlin.jvm.library")
}

dependencies {
    implementation(project(":server:application"))
    implementation(project(":server:domain"))
}
