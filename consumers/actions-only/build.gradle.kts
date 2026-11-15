plugins {
    id("chat.desktop-runtime")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}
kotlin {
    jvmToolchain(21)
    android {
        namespace = "dev.chat.consumer.actions"
        compileSdk = 37
        minSdk = 24
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { implementation(libs.chat.actions) }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(checkNotNull(project.extensions.extraProperties["chatDesktopRuntime"]))
            implementation(libs.compose.test)
        }
    }
}
tasks.register("verifySelection") {
    doLast {
        val targetConfigurations =
            listOf("jvmRuntimeClasspath", "androidCompileClasspath", "iosArm64CompileKlibraries", "iosSimulatorArm64CompileKlibraries")
        for (configurationName in targetConfigurations) {
            println("$path $configurationName")
            val modules = configurations.getByName(configurationName).incoming.resolutionResult.allComponents
                .mapNotNull { it.moduleVersion }.map { "${it.group}:${it.name}" }.sorted()
            check(modules.none { it.contains("chat-media") || it.contains("chat-sample") })
            check(modules.none { it.contains("material3") })
            println(modules.joinToString("\n"))
        }
    }
}
