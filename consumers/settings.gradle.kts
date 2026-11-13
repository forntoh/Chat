pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        maven { url = uri(providers.gradleProperty("chatRepository").get()); content { includeGroup("dev.forntoh.chat.candidate") } }
        google(); mavenCentral()
    }
}
rootProject.name = "ChatConsumers"
include(":combined", ":media-only", ":actions-only", ":core-only", ":custom-ui", ":default-material3")
