plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}
kotlin {
    jvmToolchain(21)
    android { namespace = "dev.chat.consumer.material3"; compileSdk = 37; minSdk = 24 }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { implementation("dev.chat.candidate:chat-material3:0.1.0-local") }
    }
}
tasks.register("verifySelection") {
    doLast {
        val targetConfigurations = listOf("jvmRuntimeClasspath", "androidCompileClasspath", "iosArm64CompileKlibraries", "iosSimulatorArm64CompileKlibraries")
        for (configurationName in targetConfigurations) {
        println("$path $configurationName")
        val modules = configurations.getByName(configurationName).incoming.resolutionResult.allComponents
            .mapNotNull { it.moduleVersion }.map { "${it.group}:${it.name}" }.sorted()
        check(modules.none { it.contains("chat-actions") || it.contains("chat-media") || it.contains("chat-sample") })
        println(modules.joinToString("\n"))
        }
    }
}
