import java.util.concurrent.atomic.AtomicBoolean
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
                dirChooser = true
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

// Compose 会在 packageMsi 执行时清空 jpackage resource-dir。
// 后台把允许同版本覆盖安装的 WiX 模板拷回去，供 jpackage 打包使用。
tasks.matching { it.name == "packageMsi" || it.name == "packageReleaseMsi" }.configureEach {
    val wixTemplates = layout.projectDirectory.dir("packaging/windows")
    val jpackageResources = layout.buildDirectory.dir("compose/tmp/resources")
    inputs.dir(wixTemplates)
    val stopCopy = AtomicBoolean(false)
    var copier: Thread? = null
    doFirst {
        stopCopy.set(false)
        copier = Thread(
            {
                val sourceDir = wixTemplates.asFile
                val destDir = jpackageResources.get().asFile
                while (!stopCopy.get()) {
                    runCatching {
                        if (destDir.isDirectory) {
                            sourceDir.listFiles()?.forEach { file ->
                                file.copyTo(destDir.resolve(file.name), overwrite = true)
                            }
                        }
                    }
                    Thread.sleep(20)
                }
            },
            "copy-jpackage-wix-templates",
        ).apply {
            isDaemon = true
            start()
        }
    }
    doLast {
        stopCopy.set(true)
        copier?.join(1_000)
    }
}
