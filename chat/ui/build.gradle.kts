plugins { id("chat.compose"); id("chat.staging") }
kotlin {
    android { namespace = "com.forntoh.chat.ui" }
    sourceSets {
        commonMain.dependencies {
            api(project(":chat:core"))
            api(libs.compose.runtime); api(libs.compose.foundation)
        }

    }
}
