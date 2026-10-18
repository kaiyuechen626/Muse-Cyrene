// Muse Cyrene modification: 2026-10-18; see CYRENE_MODIFICATIONS.md; GPL-3.0-or-later.
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
}

// v2.2.1: release 变体与主应用共用 keystore.properties —— 签名级权限
// io.zer0.muse.permission.A11Y_BRIDGE 要求主应用与 Provider 同签名,必须同一把钥匙。
// (storeFile 相对路径按模块目录解析;本模块与 app 同为根目录直系子模块,解析结果一致。)
val isCyreneBuild = providers.gradleProperty("cyreneBuild").isPresent
val a11yBridgePermission = if (isCyreneBuild) "io.zer0.muse.cyrene.permission.A11Y_BRIDGE" else "io.zer0.muse.permission.A11Y_BRIDGE"

val keystorePropertiesFile = rootProject.file("keystore.properties")

android {
    namespace = "io.zer0.muse.a11y"
    compileSdk = 36

    defaultConfig {
        applicationId = if (isCyreneBuild) "io.zer0.muse.cyrene.a11y" else "io.zer0.muse.a11y"
        manifestPlaceholders["a11yBridgePermission"] = a11yBridgePermission
        manifestPlaceholders["a11yAppLabel"] = if (isCyreneBuild) "Muse Cyrene 无障碍 Provider" else "@string/a11y_provider_app_name"
        manifestPlaceholders["a11yServiceLabel"] = if (isCyreneBuild) "Muse Cyrene UI 自动化(独立)" else "@string/a11y_provider_service_label"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // Match the main app's existing-key debug configuration; never create a key here.
        if (isCyreneBuild && providers.gradleProperty("cyreneKeystore").isPresent) {
            getByName("debug") {
                storeFile = file(providers.gradleProperty("cyreneKeystore").get())
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            if (keystorePropertiesFile.exists()) {
                val props = Properties()
                props.load(FileInputStream(keystorePropertiesFile))
                storeFile = file(props["storeFile"] as String)
                storePassword = props["storePassword"] as String
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 共享实现与 AIDL 契约(io.zer0.muse.accessibility)
    implementation(project(":accessibility"))
    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
