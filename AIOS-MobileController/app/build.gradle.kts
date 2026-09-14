plugins { id("com.android.application") }

android {
    namespace = "com.capybara.aios"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.capybara.aios"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3-github-alpha"
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
