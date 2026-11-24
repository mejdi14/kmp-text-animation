import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

group = "io.github.mejdi14"
version = "0.1.0-SNAPSHOT"

kotlin {
    explicitApi()

    iosArm64()
    iosSimulatorArm64()
    jvm()
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    android {
        namespace = "io.github.mejdi14.textmotion"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        androidResources { enable = true }
        withHostTest {}
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // Shared source set for every Skia-backed target (iOS, Desktop JVM, JS, Wasm).
        val skikoMain by creating { dependsOn(commonMain.get()) }
        iosMain.get().dependsOn(skikoMain)
        jvmMain.get().dependsOn(skikoMain)
        jsMain.get().dependsOn(skikoMain)
        wasmJsMain.get().dependsOn(skikoMain)

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "io.github.mejdi14.textmotion.resources"
    generateResClass = auto
}

publishing {
    // `./gradlew :textmotion:publishToMavenLocal` publishes every target to ~/.m2.
    // No remote repository is configured on purpose.
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("TextMotion")
            description.set("Compose Multiplatform text animation library: 20 production-ready text effects for Android, iOS, Desktop and Web.")
            url.set("https://github.com/mejdi14/kmp-text-animation")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers { developer { id.set("mejdi14") } }
            scm { url.set("https://github.com/mejdi14/kmp-text-animation") }
        }
    }
}
