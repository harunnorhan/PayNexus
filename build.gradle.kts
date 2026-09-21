plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

val qualityCheck = tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs repository-wide code quality checks."
}

subprojects {
    qualityCheck.configure {
        dependsOn(
            tasks.matching {
                it.name == "spotlessCheck" ||
                    it.name == "detekt" ||
                    it.name == "lint"
            },
        )
    }
}
