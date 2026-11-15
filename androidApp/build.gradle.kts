plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}
android {
    namespace = "dev.chat.sample.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.chat.sample.android"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
dependencies {
    implementation(project(":sample"))
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    androidTestImplementation(libs.android.compose.test)
    // API 37 removed the reflective InputManager API used by Espresso 3.7.
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.espresso.core)
}
