import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.coroutines.core)
        }
        wasmJsMain.dependencies {
            implementation(npm("libsodium-wrappers-sumo", "0.8.4"))
            implementation(npm("hash-wasm", "4.12.0"))
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.hdpwd.web.resources"
}
