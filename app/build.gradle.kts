plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gemmaagent.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gemmaagent.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        ndk {
            // POCO M4 Pro (MediaTek Helio G96) = 64-bit ARM
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Ditandatangani dengan kunci debug agar APK langsung bisa diinstal.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // LiteRT-LM (penerus MediaPipe LLM Inference) untuk memuat model .litertlm
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-service:2.9.0")
    implementation("androidx.savedstate:savedstate-ktx:1.3.0")

    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
