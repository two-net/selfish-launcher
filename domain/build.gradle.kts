// DOMAIN layer (SRS §7, DESIGN §3.1). A plain Kotlin/JVM module: android.*
// is not on the classpath, so any Android import fails to compile (AR-01),
// and its tests run without a device (NFR-MNT-01).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.coroutines.core)
    implementation(libs.serialization.json)

    // One fake per port (AR-03) and the @Verifies annotation (DESIGN §8.2),
    // shared with the test source sets of every module.
    testFixturesApi(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.serialization.json)
}
