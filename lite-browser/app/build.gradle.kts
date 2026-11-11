plugins {
    id("com.android.application")
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    // This build targets the user's ARM64 Android phone instead of bundling
    // every GeckoView ABI into one oversized universal APK.
    implementation("org.mozilla.geckoview:geckoview-omni-arm64-v8a:159.0.20261102144711")
}

android {
    namespace = "com.kingbrowser.lite"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kingbrowser.lite"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.7-lite"

        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
