package io.github.twonet.selfish.ui.settings

import androidx.annotation.StringRes
import io.github.twonet.selfish.R

/**
 * One open-source component the launcher ships, as About lists it
 * (FR-SET-17): its [name], and the `group:artifact` prefixes of the modules
 * it brings onto the release runtime classpath (`gradle/runtime-dependencies.txt`,
 * DESIGN §7.1). `LicensesRulesTest` checks that every shipped module is
 * covered, so About can't fall behind the list.
 */
internal class OpenSourceComponent(@StringRes val name: Int, val modules: List<String>)

/**
 * The open-source components About credits (FR-SET-17). Every one is under
 * the Apache License 2.0, whose text ships as `R.raw.license_apache_2_0`. A
 * component under another license would need its own text and a line in
 * About saying which applies.
 */
internal object Licenses {
    val COMPONENTS: List<OpenSourceComponent> = listOf(
        OpenSourceComponent(R.string.oss_kotlin, listOf("org.jetbrains.kotlin:")),
        OpenSourceComponent(R.string.oss_jetbrains_annotations, listOf("org.jetbrains:annotations")),
        OpenSourceComponent(R.string.oss_kotlinx_coroutines, listOf("org.jetbrains.kotlinx:kotlinx-coroutines-")),
        OpenSourceComponent(R.string.oss_kotlinx_serialization, listOf("org.jetbrains.kotlinx:kotlinx-serialization-")),
        OpenSourceComponent(R.string.oss_compose, listOf("androidx.compose")),
        OpenSourceComponent(R.string.oss_androidx, listOf("androidx.")),
        OpenSourceComponent(R.string.oss_listenablefuture, listOf("com.google.guava:listenablefuture")),
        OpenSourceComponent(R.string.oss_jspecify, listOf("org.jspecify:jspecify")),
    )
}
