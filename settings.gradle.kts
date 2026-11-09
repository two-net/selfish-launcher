pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "selfish-launcher"

// The three layers of SRS §7 (DESIGN §3.1).
include(":domain")   // DOMAIN: plain Kotlin/JVM
include(":adapters") // ADAPTERS: one class per port
include(":app")      // PRESENTATION + composition root
