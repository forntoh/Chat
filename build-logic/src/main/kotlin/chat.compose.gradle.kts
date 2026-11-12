plugins {
    id("chat.kmp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
kotlin.sourceSets.named("jvmTest") {
    dependencies {
        implementation(org.jetbrains.compose.ComposePlugin.DesktopDependencies.currentOs)
        implementation(libraries.findLibrary("compose-test").get())
    }
}
