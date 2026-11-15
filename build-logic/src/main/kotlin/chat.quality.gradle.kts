plugins {
    base
    `java-base`
}

val detektCli by configurations.creating
val ktlintCli by configurations.creating
repositories { mavenCentral() }
dependencies {
    detektCli("io.gitlab.arturbosch.detekt:detekt-cli:1.23.8")
    ktlintCli("com.pinterest.ktlint:ktlint-cli:1.7.1:all")
}
val kotlinSources = fileTree(projectDir) {
    include("**/*.kt", "**/*.kts")
    exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", "**/.git/**", "evidence/**")
}
val qualityLauncher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
val reports = layout.buildDirectory.dir("reports/quality")
tasks.register<JavaExec>("detekt") {
    javaLauncher.set(qualityLauncher)
    group = "verification"
    description = "Analyze maintained Kotlin and scripts without compiler type resolution."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    inputs.files(kotlinSources)
    inputs.file("config/quality/detekt.yml")
    outputs.upToDateWhen { false }
    args(
        "--input", projectDir.absolutePath, "--excludes", "**/build/**,**/.gradle/**,**/.kotlin/**,**/.git/**,**/evidence/**",
        "--config", file("config/quality/detekt.yml").absolutePath, "--build-upon-default-config",
        "--report", "html:${reports.get().file("detekt.html").asFile}",
        "--report", "sarif:${reports.get().file("detekt.sarif").asFile}",
    )
}
fun registerKtlint(name: String, format: Boolean) = tasks.register<JavaExec>(name) {
    javaLauncher.set(qualityLauncher)
    group = if (format) "formatting" else "verification"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    inputs.files(kotlinSources)
    inputs.file(".editorconfig")
    outputs.upToDateWhen { false }
    if (format) args("--format")
    doFirst { reports.get().asFile.mkdirs() }
    if (!format) args("--reporter=plain", "--reporter=checkstyle,output=${reports.get().file("ktlint.xml").asFile}")
    args("**/*.kt", "**/*.kts", "!**/build/**", "!**/.gradle/**", "!**/.kotlin/**", "!**/.git/**", "!evidence/**")
}
registerKtlint("ktlintCheck", false)
registerKtlint("ktlintFormat", true)
tasks.named("check") { dependsOn("detekt", "ktlintCheck") }
