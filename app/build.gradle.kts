plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Номер сборки на GitHub. Каждая новая сборка получает версию больше прошлой,
// поэтому телефон ставит её поверх старой как обновление.
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.avrora.paint"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.avrora.paint"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "0.3.$runNumber"
    }

    // Постоянный сертификат подписи. Пока он один и тот же,
    // новые версии устанавливаются поверх старых без удаления.
    signingConfigs {
        create("avrora") {
            storeFile = rootProject.file("keystore/avrora.jks")
            storePassword = System.getenv("AVRORA_STORE_PASSWORD") ?: "u3ZcxAkmvUbdp2uytzea"
            keyAlias = "avrora"
            keyPassword = System.getenv("AVRORA_KEY_PASSWORD") ?: "u3ZcxAkmvUbdp2uytzea"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("avrora")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("avrora")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
}
