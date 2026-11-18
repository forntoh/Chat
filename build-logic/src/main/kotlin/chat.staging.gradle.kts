import org.gradle.plugins.signing.Sign

plugins {
    `maven-publish`
    signing
}

group = "dev.forntoh.chat"
val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
version = libraries.findVersion("chat-staging").get().requiredVersion

// This is an honest documentation pointer, not rendered API documentation.
val releaseSource = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map { it.trim() }
val documentationReadme = tasks.register("writeCentralDocumentation") {
    val destination = layout.buildDirectory.file("central-documentation/README.md")
    inputs.property("releaseSource", releaseSource)
    outputs.file(destination)
    doLast {
        val source = releaseSource.get()
        check(source.matches(Regex("[0-9a-f]{40}"))) { "Expected immutable release-source commit" }
        destination.get().asFile.apply {
            parentFile.mkdirs()
            writeText("""
                # Chat 0.0.1 project documentation

                This archive is a documentation pointer; it contains no rendered API documentation.
                Kotlin sources and KDoc are supplied in the corresponding sources JAR.
                Human-readable project documentation at immutable release source $source:
                https://github.com/forntoh/Chat/blob/$source/README.md
                https://github.com/forntoh/Chat/tree/$source/docs
            """.trimIndent() + "\n")
        }
    }
}
val centralDocumentation = tasks.register<Jar>("centralDocumentationJar") {
    archiveClassifier.set("javadoc")
    from(documentationReadme)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.register("prepareCentralMetadata") {
    dependsOn(centralDocumentation)
    dependsOn(tasks.withType<org.gradle.api.publish.maven.tasks.GenerateMavenPom>())
}
// No signer is approved in this local slice. Do not read keys or create signatures.
val requireApprovedCentralSigner = tasks.register("requireApprovedCentralSigner") {
    doLast { error("Central signing is disabled: separately approved signer configuration is required") }
}
tasks.withType<Sign>().configureEach { dependsOn(requireApprovedCentralSigner) }
tasks.register<Sign>("signCentralPublications") {
    description = "Reserved signing seam; fails closed until a signer is separately approved and wired"
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "chat-${project.name}" + artifactId.removePrefix(project.name)
        artifact(centralDocumentation)
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
            developers {
                developer {
                    id.set("forntoh")
                    name.set("Thomas Forntoh")
                    url.set("https://github.com/forntoh")
                }
            }
            scm {
                url.set("https://github.com/forntoh/Chat")
                connection.set("scm:git:https://github.com/forntoh/Chat.git")
                developerConnection.set("scm:git:ssh://git@github.com/forntoh/Chat.git")
            }
        }
    }
    repositories {
        maven {
            name = "staging"
            val stagingPath = providers.gradleProperty("chatStagingDirectory")
            // Only local file paths are accepted; no remote publishing repository is configured.
            val localPath = stagingPath.getOrElse("build/staging")
            check("://" !in localPath) { "Staging must use a local file path" }
            url = rootProject.file(localPath).toURI()
        }
    }
}

// Carry attribution in binary and source archives; KDoc stays in the source artifacts.
tasks.withType<AbstractArchiveTask>().configureEach {
    from(
        providers.provider {
            if (archiveFileName.get().endsWith(".jar") || archiveFileName.get().endsWith(".aar")) {
                listOf(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
            } else {
                emptyList<File>()
            }
        },
    ) { into("META-INF") }
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
