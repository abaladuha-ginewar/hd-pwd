import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.hdpwd.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "hd-pwd"
            packageVersion = "1.0.0"
            description = "hd-pwd password manager"
            copyright = "© hd-pwd"
            vendor = "hd-pwd"
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "hd-pwd"
                upgradeUuid = "8f3c1a2e-6b4d-4e91-9c2a-1d7b5e0f3a28"
            }
            macOS {
                iconFile.set(project.file("icons/icon.png"))
                bundleID = "com.hdpwd.desktop"
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}
