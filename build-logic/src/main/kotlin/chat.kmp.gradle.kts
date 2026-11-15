plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}
val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
kotlin {
    jvmToolchain(21)
    android {
        compileSdk = 37
        minSdk = 24
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets.commonTest.dependencies { implementation(libraries.findLibrary("kotlin-test").get()) }
}
