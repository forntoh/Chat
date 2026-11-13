plugins { id("chat.compose"); id("chat.staging") }
kotlin {
    android { namespace = "dev.chat.media" }
    sourceSets.commonMain.dependencies { api(project(":chat:ui")) }
}
