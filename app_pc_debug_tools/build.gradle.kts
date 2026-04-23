import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

group = "com.newchar.debug"
version = "1.0.0"

repositories {
    // 阿里云镜像（主）
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    maven { url = uri("https://maven.aliyun.com/repository/kotlin") }
    
    // 腾讯云镜像
    maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
    maven { url = uri("https://mirrors.cloud.tencent.com/repository/maven-google/") }
    
    // 清华大学镜像
    maven { url = uri("https://mirrors.tuna.tsinghua.edu.cn/maven-central/") }
    
    // JetBrains Compose
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    
    // 官方源
    google()
    mavenCentral()
}

kotlin {
    jvm("jvm") {
        withJava()
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)

                implementation(kotlin("stdlib"))
                
                // 协程核心
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                
                // JSON 序列化
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                
                // 现代日期时间 API (Kotlin Multiplatform)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.5.4")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs) {
                    exclude(group = "org.jetbrains.compose.material", module = "material")
                    exclude(group = "org.jetbrains.compose.material", module = "material-desktop")
                    exclude(group = "org.jetbrains.compose.material", module = "material-icons-core")
                    exclude(group = "org.jetbrains.compose.material", module = "material-icons-core-desktop")
                    exclude(group = "org.jetbrains.compose.material", module = "material-ripple")
                    exclude(group = "org.jetbrains.compose.material", module = "material-ripple-desktop")
                }

                implementation(kotlin("stdlib-jdk8"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.newchar.debug.pc.MainKt"

        /*
         * Release / Distribution template
         *
         * Compose Desktop native distribution DSL:
         * - supports packaging for macOS(.dmg/.pkg), Windows(.exe/.msi), Linux(.deb/.rpm)
         * - release obfuscation is handled through the release.proguard block
         * - macOS signing/notarization can be configured in macOS.signing / macOS.notarization
         *
         * Official references:
         * - https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-native-distribution.html
         */

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Exe,
                TargetFormat.Deb,
                TargetFormat.Rpm
            )

            packageName = "PC Debug Tools"
            packageVersion = "1.0.0"

            description = "Android Device Debug Tools for Desktop (Windows & macOS)"
            copyright = "© 2024 NewChar Debug Tools. All rights reserved."

            vendor = "NewChar"

            modules(
                "java.instrument",
                "java.management",
                "java.naming",
                "java.sql",
                "jdk.crypto.ec"
                // "jdk.accessibility"
            )

            /*
             * Full package metadata template
             *
             * packageVersion = "1.0.0"
             * vendor = "NewChar"
             * description = "Android Device Debug Tools for Desktop"
             * copyright = "© 2026 NewChar"
             *
             * Linux / Windows / macOS can also override versions independently:
             *
             * macOS {
             *     packageVersion = "1.0.0"
             *     dmgPackageVersion = "1.0.0"
             *     pkgPackageVersion = "1.0.0"
             * }
             *
             * windows {
             *     packageVersion = "1.0.0"
             *     exePackageVersion = "1.0.0"
             *     msiPackageVersion = "1.0.0"
             * }
             *
             * linux {
             *     packageVersion = "1.0.0"
             *     debPackageVersion = "1.0.0"
             *     rpmPackageVersion = "1.0.0"
             * }
             */

            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icons/app_icon.icns"))
                bundleID = "com.newchar.debug.pctools"
                appCategory = "public.app-category.developer-tools"

                dockName = "Debug Tools"

                entitlementsFile.set(null as java.io.File?)
                signing {
                    identity.set(null as String?)
                }

                /*
                 * macOS signing / notarization template
                 *
                 * Notes:
                 * - `packageDmg` only creates a dmg package, it is not automatically a signed release package.
                 * - A production package usually needs:
                 *   1. Developer ID Application signing
                 *   2. Apple notarization
                 *   3. Stapling ticket
                 *
                 * Example using Gradle properties or environment variables:
                 *
                 * val macSignIdentity = providers.gradleProperty("mac.sign.identity")
                 *     .orElse(providers.environmentVariable("MAC_SIGN_IDENTITY"))
                 * val appleId = providers.gradleProperty("mac.notarize.appleId")
                 *     .orElse(providers.environmentVariable("MAC_NOTARIZE_APPLE_ID"))
                 * val applePassword = providers.gradleProperty("mac.notarize.password")
                 *     .orElse(providers.environmentVariable("MAC_NOTARIZE_PASSWORD"))
                 * val appleTeamId = providers.gradleProperty("mac.notarize.teamId")
                 *     .orElse(providers.environmentVariable("MAC_NOTARIZE_TEAM_ID"))
                 *
                 * macOS {
                 *     bundleID = "com.newchar.debug.pctools"
                 *     dockName = "Debug Tools"
                 *     appCategory = "public.app-category.developer-tools"
                 *
                 *     signing {
                 *         sign.set(true)
                 *         identity.set(macSignIdentity.orNull)
                 *         // keychain.set(file("/Users/xxx/Library/Keychains/login.keychain-db"))
                 *     }
                 *
                 *     notarization {
                 *         appleID.set(appleId)
                 *         password.set(applePassword)
                 *         teamID.set(appleTeamId)
                 *     }
                 *
                 *     // Optional:
                 *     // provisioningProfile.set(file("desktop/macos/App.provisionprofile"))
                 *     // runtimeProvisioningProfile.set(file("desktop/macos/Runtime.provisionprofile"))
                 * }
                 *
                 * Typical commands after enabling config:
                 * ./gradlew :app_pc_debug_tools:packageDmg
                 * ./gradlew :app_pc_debug_tools:notarizeDmg
                 */
            }

            windows {
                val icoFile = project.file("src/jvmMain/resources/icons/app_icon.ico")
                if (icoFile.exists()) {
                    iconFile.set(icoFile)
                }
                menuGroup = "NewChar Debug Tools"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

                dirChooser = true
                perUserInstall = false

                /*
                 * Windows packaging / signing template
                 *
                 * Compose Desktop can generate unsigned .msi / .exe installers directly.
                 * Code signing is usually performed in CI after packaging by signtool or enterprise signing tools.
                 *
                 * Example CI step:
                 * signtool sign /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 ^
                 *   /f cert.pfx /p %WINDOWS_CERT_PASSWORD% build/compose/binaries/main-exe/app/YourApp.exe
                 *
                 * Packaging commands:
                 * ./gradlew :app_pc_debug_tools:packageMsi
                 * ./gradlew :app_pc_debug_tools:packageExe
                 */
            }

            linux {
                val pngFile = project.file("src/jvmMain/resources/icons/app_icon.png")
                if (pngFile.exists()) {
                    iconFile.set(pngFile)
                }

                /*
                 * Linux packaging / signing template
                 *
                 * Packaging commands:
                 * ./gradlew :app_pc_debug_tools:packageDeb
                 * ./gradlew :app_pc_debug_tools:packageRpm
                 *
                 * If distribution requires package signing, it is usually completed
                 * after package generation with distro-specific tools in CI.
                 */
            }
        }

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        /*
         * Full release obfuscation / shrink template
         *
         * buildTypes.release.proguard {
         *     isEnabled.set(true)
         *     optimize.set(true)
         *     obfuscate.set(true)
         *     joinOutputJars.set(false)
         *     configurationFiles.from(
         *         project.file("proguard-rules.pro"),
         *         project.file("proguard-desktop.pro")
         *     )
         * }
         *
         * Recommended release commands:
         * ./gradlew :app_pc_debug_tools:createReleaseDistributable
         * ./gradlew :app_pc_debug_tools:packageReleaseDistributionForCurrentOS
         *
         * Platform-specific package commands:
         * ./gradlew :app_pc_debug_tools:packageDmg
         * ./gradlew :app_pc_debug_tools:packageMsi
         * ./gradlew :app_pc_debug_tools:packageExe
         * ./gradlew :app_pc_debug_tools:packageDeb
         * ./gradlew :app_pc_debug_tools:packageRpm
         */
    }
}
