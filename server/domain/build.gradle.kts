plugins {
    id("paynexus.kotlin.jvm.library")
}

base {
    archivesName.set("paynexus-server-domain")
}

dependencies {
    testImplementation(libs.kotlin.test.junit)
}
