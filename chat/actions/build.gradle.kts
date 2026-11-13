plugins { id("chat.compose"); id("chat.staging") }
kotlin {
    android { namespace = "dev.chat.actions" }
    sourceSets.commonMain.dependencies { api(project(":chat:ui")) }
}
