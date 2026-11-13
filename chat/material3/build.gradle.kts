plugins { id("chat.compose"); id("chat.staging") }
kotlin {
    android { namespace = "com.forntoh.chat.material3" }
    sourceSets {
        commonMain.dependencies {
            api(project(":chat:ui"))
            implementation(libs.material3)
            implementation(libs.tabler.icons)
        }

    }
}
