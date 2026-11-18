plugins {
    id("chat.kmp")
    id("chat.desktop-runtime")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
kotlin.sourceSets.named("jvmTest") {
    dependencies {
        implementation(checkNotNull(project.extensions.extraProperties["chatDesktopRuntime"]))
        implementation(libraries.findLibrary("compose-test").get())
    }
}
