plugins {
    id("paynexus.android.application")
    id("paynexus.android.compose")
}

android {
    namespace = "com.paynexus.merchant"

    defaultConfig {
        applicationId = "com.paynexus.merchant"
    }
}

dependencies {
    implementation(project(":design-system"))
    implementation(project(":payment:domain"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.kotlin.test.junit)
    debugImplementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
