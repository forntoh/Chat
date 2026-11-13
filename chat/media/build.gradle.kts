plugins { id("chat.compose"); id("chat.staging") }
kotlin {
    android { namespace = "com.forntoh.chat.media" }
    sourceSets.commonMain.dependencies { api(project(":chat:ui")) }
}
