import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    androidTarget()
    jvm("desktop")
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.serialization.cbor)
            implementation(libs.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.core)
            implementation(libs.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.diglol.crypto)
            implementation(libs.androidx.biometric)
        }
        named("desktopMain").dependencies {
            implementation(libs.diglol.crypto)
        }
        wasmJsMain.dependencies {
            implementation(npm("libsodium-wrappers-sumo", "0.8.4"))
        }
    }
}

android {
    namespace = "com.hdpwd.shared"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    buildToolsVersion = "35.0.0"
    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }
}
