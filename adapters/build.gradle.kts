// ADAPTERS layer (SRS §7, DESIGN §3.1): one class per port of :domain.
// No Compose plugin, and :app is out of reach (Gradle rejects the cycle).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.twonet.selfish.adapters"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // Version-staleness checks depend on the network and would start
        // failing as new releases appear; versions are reviewed by hand.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":domain"))
    implementation(libs.coroutines.android)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
