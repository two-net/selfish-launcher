// PRESENTATION layer and composition root (SRS §7, DESIGN §3.1).
// Only AppGraph.kt may touch :adapters; LayerRulesTest enforces it.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.twonet.selfish"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Placeholder until Q-4 and Q-7 are settled (TD-07).
        applicationId = "io.github.twonet.selfish"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
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
    implementation(project(":domain"))
    implementation(project(":adapters"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

// BackupRulesTest, ThemeRulesTest and ManifestRulesTest read resources and
// the manifest straight from src/main. Make them test inputs, or an edit to
// them would leave the tests UP-TO-DATE.
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/res/xml").withPropertyName("backupRules").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/res/values").withPropertyName("themes").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/res/mipmap-anydpi").withPropertyName("launcherIcon").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("src/main/AndroidManifest.xml").withPropertyName("manifest").withPathSensitivity(PathSensitivity.RELATIVE)
}

// checkDependencies (AR-08, TD-06, DESIGN §8.2): the shipped runtime classpath
// must match the reviewed list in gradle/runtime-dependencies.txt, which
// mirrors DESIGN §7.1. Adding a component means updating both in one PR.
val checkDependencies by tasks.registering {
    group = "verification"
    description = "Fails if the release runtime classpath differs from gradle/runtime-dependencies.txt."

    val allowlistFile = rootProject.layout.projectDirectory.file("gradle/runtime-dependencies.txt")
    val resolved = configurations.named("releaseRuntimeClasspath").flatMap { it.incoming.resolutionResult.rootComponent }
    inputs.file(allowlistFile)
    inputs.property("resolved", resolved.map { root -> modulesOf(root).sorted().joinToString("\n") })

    doLast {
        val actual = inputs.properties["resolved"].toString().lines().filter { it.isNotBlank() }.toSet()
        val allowed = allowlistFile.asFile.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val forbidden = actual.filter { it.startsWith("com.google.android.gms:") || it.startsWith("com.google.firebase:") }
        val added = actual - allowed
        val removed = allowed - actual
        if (forbidden.isNotEmpty() || added.isNotEmpty() || removed.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("The release runtime classpath differs from gradle/runtime-dependencies.txt.")
                    if (forbidden.isNotEmpty()) appendLine("Never allowed (TD-06): ${forbidden.sorted()}")
                    if (added.isNotEmpty()) appendLine("Not in the list (justify in DESIGN §7, then add): ${added.sorted()}")
                    if (removed.isNotEmpty()) appendLine("No longer used (remove from the list): ${removed.sorted()}")
                },
            )
        }
    }
}

fun modulesOf(root: org.gradle.api.artifacts.result.ResolvedComponentResult): Set<String> {
    val seen = mutableSetOf<org.gradle.api.artifacts.result.ResolvedComponentResult>()
    val queue = ArrayDeque(listOf(root))
    val modules = mutableSetOf<String>()
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        if (!seen.add(component)) continue
        (component.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)
            ?.let { modules += "${it.group}:${it.module}" }
        component.dependencies
            .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
            .forEach { queue += it.selected }
    }
    return modules
}

tasks.named("check") { dependsOn(checkDependencies) }
