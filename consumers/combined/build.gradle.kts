import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}
kotlin {
    jvmToolchain(21)
    android { namespace = "dev.chat.consumer.combined"; compileSdk = 37; minSdk = 24 }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets.commonMain.dependencies {
        implementation("dev.forntoh.chat.candidate:chat-actions:0.1.0-local")
        implementation("dev.forntoh.chat.candidate:chat-media:0.1.0-local")
    }
}

// The public seam is the selected published graph; no producer project is included.
tasks.register("verifySelection") {
    doLast {
        val targets = mapOf(
            "jvmRuntimeClasspath" to "jvm",
            "androidCompileClasspath" to "android",
            "iosArm64CompileKlibraries" to "iosarm64",
            "iosSimulatorArm64CompileKlibraries" to "iossimulatorarm64"
        )
        for ((configurationName, target) in targets) {
            val configuration = configurations.getByName(configurationName)
            val result = configuration.incoming.resolutionResult
            val unresolved = result.allDependencies.filterIsInstance<UnresolvedDependencyResult>()
            check(unresolved.isEmpty()) { unresolved.joinToString("\n") { it.attempted.toString() + ": " + it.failure.message } }
            val components = result.allComponents.filter { it.id != result.rootComponent.get().id }
            check(components.none { it.id is ProjectComponentIdentifier }) { "Producer project coupling" }
            val modules = components.mapNotNull { it.id as? ModuleComponentIdentifier }
            val chat = modules.filter { it.group == "dev.forntoh.chat.candidate" }
            val expected = setOf("chat-core", "chat-ui", "chat-actions", "chat-media")
            check(chat.map { it.version }.toSet() == setOf("0.1.0-local"))
            check(chat.map { it.module }.toSet() == expected + expected.map { "$it-$target" }) {
                "Unexpected Chat target selection: $chat"
            }
            check(chat.count { it.module == "chat-core-$target" } == 1)
            check(modules.none {
                it.module.contains("material3", true) || it.group == "com.composables" ||
                    it.module.contains("sample", true) || it.module.contains("build-logic", true)
            }) { "Unwanted optional or producer dependency" }
            val otherPlatforms = when (target) {
                "jvm" -> listOf("-android", "-ios", "-wasm", "-js", "-linux", "-mingw", "-macos")
                "android" -> listOf("-desktop", "-ios", "-wasm", "-js", "-linux", "-mingw", "-macos")
                else -> listOf("-android", "-desktop", "-jvm", "-wasm", "-js", "-linux", "-mingw", "-macos") +
                    if (target == "iosarm64") listOf("-iossimulatorarm64", "-iosx64") else listOf("-iosarm64", "-iosx64")
            }
            val leaks = modules.filter { module -> otherPlatforms.any { module.module.contains(it) } }
            check(leaks.isEmpty()) { "Cross-platform dependency leak: $leaks" }
            // Force artifact resolution too: a partial metadata graph is not a passing check.
            val files = configuration.files
            check(files.isNotEmpty())
            println("PASS $configurationName (${files.size} artifacts)")
            modules.sortedBy { it.toString() }.forEach { println("MODULE $configurationName $it") }
            configuration.incoming.artifacts.artifacts.sortedBy { it.file.name }.forEach {
                println("ARTIFACT $configurationName ${it.id.componentIdentifier} ${it.variant.attributes} ${it.file.name}")
            }
        }
    }
}
