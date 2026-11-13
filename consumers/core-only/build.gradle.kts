plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}
kotlin {
    jvmToolchain(21)
    android { namespace = "dev.chat.consumer.core"; compileSdk = 37; minSdk = 24 }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { implementation("dev.forntoh.chat.candidate:chat-core:0.1.0-local") }
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
        check(modules.none { it.contains("compose") || it.contains("material3") })
        println(modules.joinToString("\n"))
        }
    }
}
