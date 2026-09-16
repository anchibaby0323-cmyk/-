plugins {
    id("com.android.application")
}

android {
    namespace = "dev.mobilepilot"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.mobilepilot"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-bridge"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
