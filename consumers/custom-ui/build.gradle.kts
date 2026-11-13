plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}
kotlin {
    jvmToolchain(21)
    android { namespace = "dev.chat.consumer.ui"; compileSdk = 37; minSdk = 24 }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { implementation("dev.forntoh.chat.candidate:chat-ui:0.1.0-local") }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.compose.ui:ui-test-junit4:1.11.1")
        }
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
        check(modules.none { it.contains("material3") })
        println(modules.joinToString("\n"))
        }
    }
}
