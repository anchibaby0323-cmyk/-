plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.example.switchprokeyfix"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.switchprokeyfix"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
