import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class CodeQualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.diffplug.spotless")
            pluginManager.apply("dev.detekt")

            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("src/**/*.kt")
                    ktlint("1.8.0")
                        .setEditorConfigPath(rootProject.file(".editorconfig"))
                }

                kotlinGradle {
                    target("*.gradle.kts")
                    ktlint("1.8.0")
                        .setEditorConfigPath(rootProject.file(".editorconfig"))
                }
            }

            extensions.configure<DetektExtension> {
                toolVersion.set("2.0.0-alpha.6")
                buildUponDefaultConfig.set(true)
                parallel.set(true)
            }
        }
    }
}
