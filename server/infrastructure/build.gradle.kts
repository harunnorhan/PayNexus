plugins {
    id("paynexus.kotlin.jvm.library")
}

base {
    archivesName.set("paynexus-server-infrastructure")
}

dependencies {
    implementation(project(":server:domain"))
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.kotlin.test.junit)
}
