pluginManagement {
    includeBuild("build-logic")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "Chat"
include(":chat:media", ":chat:actions", ":chat:core", ":chat:ui", ":chat:material3", ":sample", ":androidApp")
