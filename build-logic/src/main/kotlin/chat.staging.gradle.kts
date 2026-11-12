plugins { `maven-publish` }
group = "dev.chat.candidate"
version = "0.1.0-local"
publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "chat-${project.name}" + artifactId.removePrefix(project.name)
        pom { name.set("Chat ${project.name}"); description.set("Host-owned Compose Multiplatform chat ${project.name}") }
    }
    repositories { maven { name = "staging"; url = rootProject.layout.buildDirectory.dir("staging").get().asFile.toURI() } }
}
