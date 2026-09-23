plugins {
    id("paynexus.android.library")
}

android {
    namespace = "com.paynexus.payment.contract"

    buildFeatures {
        aidl = true
    }
}

dependencies {
    testImplementation(libs.kotlin.test.junit)
}
