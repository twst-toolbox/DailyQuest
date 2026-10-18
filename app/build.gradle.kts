import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.dailyquest.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dailyquest.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // 自定义 APK 输出文件名
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val buildType = variant.buildType.name
            val versionName = variant.versionName
            val versionCode = variant.versionCode
            val date = SimpleDateFormat("yyyyMMdd-HHmm").format(Date())
            (this as com.android.build.gradle.internal.api.ApkVariantOutputImpl).outputFileName =
                "DailyQuest-${versionName}-b${versionCode}-${date}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            excludes += setOf("META-INF/**")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // JSON 解析
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // OpenCV
    implementation("org.opencv:opencv:4.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
