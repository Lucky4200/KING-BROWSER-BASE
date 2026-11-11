plugins {
    id("com.android.application")
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
}

android {
    namespace = "com.kingbrowser.lite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kingbrowser.lite"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "1.2-lite"
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
