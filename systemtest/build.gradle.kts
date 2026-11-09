// System tests (level I, DESIGN §8.1): UiAutomator scenarios that cross app
// boundaries, such as Home and Back presses, the default-Home prompt and
// killing the launcher's process. Self-instrumenting, so the tests run in
// their own process and survive the launcher being killed. Never shipped.
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "io.github.twonet.selfish.systemtest"
    compileSdk = libs.versions.compileSdk.get().toInt()
    targetProjectPath = ":app"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The @Verifies annotation (DESIGN §8.2).
    implementation(testFixtures(project(":domain")))
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.kotlin.test)
}
