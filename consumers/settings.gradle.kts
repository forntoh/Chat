pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        maven { url = uri(providers.gradleProperty("chatRepository").get()); content { includeGroup("dev.chat.candidate") } }
        google(); mavenCentral()
    }
}
rootProject.name = "ChatConsumers"
include(":core-only", ":custom-ui", ":default-material3")
