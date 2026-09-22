import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> { buildFeatures.compose = true }
            }
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { buildFeatures.compose = true }
            }
            pluginManager.withPlugin("paynexus.code.quality") {
                extensions.configure<DetektExtension> {
                    config.setFrom(rootProject.files("build-logic/config/compose-detekt.yml"))
                }
                val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
                extensions.configure<SpotlessExtension> {
                    kotlin {
                        clearSteps()
                        ktlint(libs.findVersion("ktlint").get().requiredVersion)
                            .setEditorConfigPath(rootProject.file(".editorconfig"))
                            .editorConfigOverride(mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"))
                    }
                }
            }
        }
    }
}
