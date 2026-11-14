package io.github.twonet.selfish.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.twonet.selfish.BuildConfig
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.components.PageHeader
import io.github.twonet.selfish.ui.components.Section
import io.github.twonet.selfish.ui.components.TextButton
import io.github.twonet.selfish.ui.tokens.Emphasis
import io.github.twonet.selfish.ui.tokens.Spacing
import io.github.twonet.selfish.ui.tokens.TextRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * About (FR-SET-17), open over Settings: the launcher's name and version
 * (FR-GEN-01), that it isn't affiliated with Jolla or Microsoft, where the
 * wallpapers come from and who owns them, with the copyright text of the one
 * in use (FR-WP-10), and the open-source components it is built with, with
 * the license text on request. Back returns to Settings (FR-GEN-04).
 */
@Composable
internal fun About(state: LauncherState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Outside the scroll, so nothing scrolls under the status bar or a cutout (UI-07).
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(start = Spacing.Gutter, end = Spacing.Gutter, bottom = Spacing.Gutter)
            .testTag(SettingsTags.ABOUT),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        PageHeader(stringResource(R.string.menu_about), Modifier.testTag(SettingsTags.ABOUT_HEADER))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tiny)) {
            LauncherText(stringResource(R.string.app_name))
            LauncherText(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), role = TextRole.Label, emphasis = Emphasis.Secondary)
            LauncherText(stringResource(R.string.about_not_affiliated), role = TextRole.Label, emphasis = Emphasis.Secondary)
        }
        Section(stringResource(R.string.about_images)) {
            LauncherText(stringResource(R.string.about_images_attribution), role = TextRole.Label, emphasis = Emphasis.Secondary)
            val copyright = state.wallpaper?.copyright
            if (!copyright.isNullOrBlank()) {
                LauncherText(stringResource(R.string.about_current_image, copyright), role = TextRole.Label, emphasis = Emphasis.Secondary)
            }
        }
        Section(stringResource(R.string.about_licenses)) {
            LauncherText(stringResource(R.string.about_licenses_intro), role = TextRole.Label, emphasis = Emphasis.Secondary)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tiny)) {
                for (component in Licenses.COMPONENTS) LauncherText(stringResource(component.name), role = TextRole.Label)
            }
            var shown by remember { mutableStateOf(false) }
            TextButton(stringResource(if (shown) R.string.about_hide_license else R.string.about_show_license)) { shown = !shown }
            if (shown) LicenseText()
        }
    }
}

/** The Apache License 2.0, read from the app's resources off the main thread; nothing until it is read. */
@Composable
private fun LicenseText() {
    val resources = LocalResources.current
    val text by produceState<String?>(null, resources) {
        value = withContext(Dispatchers.IO) {
            resources.openRawResource(R.raw.license_apache_2_0).bufferedReader().use { reader ->
                // Without the plain-text file's indentation, it wraps to the screen.
                reader.readLines().joinToString("\n") { it.trim() }.trim()
            }
        }
    }
    text?.let {
        LauncherText(it, role = TextRole.Label, emphasis = Emphasis.Secondary, modifier = Modifier.fillMaxWidth().testTag(SettingsTags.LICENSE_TEXT))
    }
}
