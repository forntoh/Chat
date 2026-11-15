plugins { id("chat.compose") }
kotlin {
    android {
        namespace = "dev.chat.sample"
        androidResources.enable = true
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ChatSample"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":chat:material3"))
            implementation(project(":chat:actions"))
            implementation(project(":chat:media"))
            implementation(libs.compose.resources)
            implementation(libs.material3)
        }
        jvmMain.dependencies { implementation(checkNotNull(project.extensions.extraProperties["chatDesktopRuntime"])) }
    }
}
compose.desktop { application { mainClass = "dev.chat.sample.MainKt" } }

compose.resources { packageOfResClass = "dev.chat.sample.resources" }
