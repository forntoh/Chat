plugins {
    id("chat.compose")
    id("chat.staging")
}
kotlin {
    android { namespace = "dev.forntoh.chat.material3" }
    sourceSets {
        commonMain.dependencies {
            api(project(":chat:ui"))
            implementation(libs.compose.animation)
            implementation(libs.material3)
            implementation(libs.tabler.icons)
        }
    }
}
