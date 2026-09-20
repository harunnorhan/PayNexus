plugins {
    id("paynexus.kotlin.jvm.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
}
