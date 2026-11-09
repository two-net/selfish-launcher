// Every plugin version lives in gradle/libs.versions.toml (TD-03).
// Declaring the Kotlin plugins here also pins the Kotlin version that
// AGP's built-in Kotlin support uses, so all modules share one compiler.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
