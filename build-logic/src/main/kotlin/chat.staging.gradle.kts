plugins { `maven-publish` }

group = "dev.forntoh.chat.candidate"
val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
version = libraries.findVersion("chat-staging").get().requiredVersion

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "chat-${project.name}" + artifactId.removePrefix(project.name)
        pom {
            name.set("Chat ${project.name}")
            description.set("Host-owned Compose Multiplatform chat ${project.name}")
        }
    }
    repositories {
        maven {
            name = "staging"
            url = rootProject.layout.buildDirectory.dir("staging").get().asFile.toURI()
        }
    }
}
