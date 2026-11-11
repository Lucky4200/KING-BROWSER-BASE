plugins {
    id("com.android.application")
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("org.mozilla.geckoview:geckoview-stable:159.0.20261102144711")
}

android {
    namespace = "com.kingbrowser.lite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kingbrowser.lite"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.6-lite"
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
