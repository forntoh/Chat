plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "dev.chat.sample.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.chat.sample.android"; minSdk = 24; targetSdk = 37; versionCode = 1; versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies {
    implementation(project(":sample"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.2")
    // API 37 removed the reflective InputManager API used by Espresso 3.7.
    androidTestImplementation("androidx.test:runner:1.8.0-alpha04")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.8.0-alpha04")
}
