plugins { id("chat.compose"); id("chat.staging") }
kotlin {
    android { namespace = "com.forntoh.chat.actions" }
    sourceSets.commonMain.dependencies { api(project(":chat:ui")) }
}
