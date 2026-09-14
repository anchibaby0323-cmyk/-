plugins { id("com.android.application") }

android {
    namespace = "com.capybara.gptgeminiminisbridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.capybara.gptgeminiminisbridge"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1-auto-alpha"
    }
}

dependencies { testImplementation("junit:junit:4.13.2") }
