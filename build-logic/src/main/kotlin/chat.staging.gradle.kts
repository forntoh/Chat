plugins { `maven-publish` }

group = "dev.forntoh.chat"
val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
version = libraries.findVersion("chat-staging").get().requiredVersion

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "chat-${project.name}" + artifactId.removePrefix(project.name)
        pom {
            name.set("Chat ${project.name}")
            description.set("Host-owned Compose Multiplatform chat ${project.name}")
            url.set("https://github.com/forntoh/Chat")
            licenses {
                license {
                    name.set("Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            scm {
                url.set("https://github.com/forntoh/Chat")
                connection.set("scm:git:https://github.com/forntoh/Chat.git")
            }
        }
    }
    repositories {
        maven {
            name = "staging"
            val stagingPath = providers.gradleProperty("chatStagingDirectory")
            // Only local file paths are accepted; no remote publishing repository is configured.
            url = rootProject.file(stagingPath.getOrElse("build/staging")).toURI()
        }
    }
}

// Carry attribution in binary and source archives; KDoc stays in the source artifacts.
tasks.withType<AbstractArchiveTask>().configureEach {
    if (archiveExtension.get() in listOf("jar", "aar")) {
        from(rootProject.file("LICENSE")) { into("META-INF") }
        from(rootProject.file("NOTICE")) { into("META-INF") }
    }
}

// A version is immutable even locally. Partial outputs require a fresh staging directory.
tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenRepository>().configureEach {
    doFirst {
        check(repository.url.scheme == "file") { "Staging must use a local file repository" }
        val destination = File(
            File(repository.url),
            "${publication.groupId.replace('.', '/')}/${publication.artifactId}/${publication.version}",
        )
        check(!destination.exists()) { "Staged version already exists: $destination; use a fresh directory" }
    }
}
