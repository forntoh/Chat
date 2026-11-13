plugins { id("chat.compose") }
kotlin {
    android { namespace = "dev.chat.sample" }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "ChatSample"; isStatic = true }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":chat:material3"))
            implementation(project(":chat:actions"))
            implementation(project(":chat:media"))
            implementation(compose.components.resources)
            implementation(libs.material3)
        }
        jvmMain.dependencies { implementation(compose.desktop.currentOs) }

    }
}
compose.desktop { application { mainClass = "dev.chat.sample.MainKt" } }

compose.resources { packageOfResClass = "dev.chat.sample.resources" }
