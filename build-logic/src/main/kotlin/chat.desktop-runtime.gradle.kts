val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
val os = System.getProperty("os.name").lowercase()
val arch = when (System.getProperty("os.arch").lowercase()) {
    "aarch64", "arm64" -> "arm64"
    "amd64", "x86_64" -> "x64"
    else -> error("Unsupported desktop architecture")
}
val platform = when {
    os.startsWith("mac") -> "macos"
    os.startsWith("linux") -> "linux"
    os.startsWith("windows") -> "windows"
    else -> error("Unsupported desktop operating system")
}
extensions.extraProperties["chatDesktopRuntime"] = libraries.findLibrary("desktop-$platform-$arch").get()
