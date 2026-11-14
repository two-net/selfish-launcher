package io.github.twonet.selfish.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.model.Animations
import io.github.twonet.selfish.domain.model.Appearance
import io.github.twonet.selfish.domain.model.DownloadOn
import io.github.twonet.selfish.domain.model.Haptics
import io.github.twonet.selfish.domain.model.HighlightSetting
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.state.AccessFeature
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.Overlay
import io.github.twonet.selfish.domain.state.appearance
import io.github.twonet.selfish.domain.state.notificationsOn
import io.github.twonet.selfish.domain.theme.Highlight
import io.github.twonet.selfish.domain.wallpaper.MarketResolver
import io.github.twonet.selfish.ui.components.ActionRow
import io.github.twonet.selfish.ui.components.Choice
import io.github.twonet.selfish.ui.components.ChoiceRow
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.components.PageHeader
import io.github.twonet.selfish.ui.components.PulleyItem
import io.github.twonet.selfish.ui.components.PulleyMenu
import io.github.twonet.selfish.ui.components.Section
import io.github.twonet.selfish.ui.components.SwitchRow
import io.github.twonet.selfish.ui.components.displayName
import io.github.twonet.selfish.ui.components.marketName
import io.github.twonet.selfish.ui.components.pressable
import io.github.twonet.selfish.ui.components.pulleyActions
import io.github.twonet.selfish.ui.components.selectedOutline
import io.github.twonet.selfish.ui.components.takeEveryTouch
import io.github.twonet.selfish.ui.onboarding.AccessExplanation
import io.github.twonet.selfish.ui.tokens.Emphasis
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Shapes
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing
import io.github.twonet.selfish.ui.tokens.TextRole
import io.github.twonet.selfish.ui.topmenu.AppearanceSwitch
import io.github.twonet.selfish.ui.tokens.color
import kotlinx.coroutines.launch
import java.text.Collator

/**
 * The launcher's Settings page (SRS §4.13): every setting, rendered from
 * [state]; each change goes back as an action and takes effect at once,
 * with no Save button. Its header, "Settings", is right-aligned in the
 * highlight color (UI-02), and its pulley menu offers "Replay tutorial"
 * (FR-ONB-03) and "About" (FR-GEST-05), also as actions on the header
 * (FR-GEST-09).
 *
 * Like Wallpaper details, it covers the whole screen over the dimmed
 * wallpaper and takes every touch, so nothing beneath can be pressed; Back
 * and Home close it (FR-GEN-03, FR-GEN-04). About, and the explanation a
 * switch shows before asking for special access (FR-ONB-04), open over it in
 * the same frame, crossfading, so the dim isn't drawn twice and the page
 * keeps its scroll position for Back.
 *
 * "Covers show" (FR-SET-08), "Swipe down on Home opens" (FR-SET-11) and
 * "Notification dots" (FR-SET-13) are left out until the features they
 * control are built (DESIGN §8.3).
 */
@Composable
internal fun SettingsPage(state: LauncherState, dispatch: (Action) -> Unit, modifier: Modifier = Modifier) {
    val dim = LauncherTheme.colors.dim
    val motion = LauncherTheme.motion
    val scroll = rememberScrollState()
    val top = state.overlays.lastOrNull()
    // The feature explained, kept while its explanation fades out.
    val kept = remember { KeptFeature() }
    val explained = ((top as? Overlay.AccessExplanation)?.feature ?: kept.value).also { kept.value = it }
    Box(modifier.fillMaxSize().drawBehind { drawRect(dim) }.testTag(SettingsTags.PAGE)) {
        // Beneath the page, so a touch anywhere it doesn't use goes no further.
        Box(Modifier.fillMaxSize().takeEveryTouch())
        AnimatedVisibility(visible = top == null, enter = fadeIn(motion.fadeIn()), exit = fadeOut(motion.fadeOut())) {
            SettingsList(state, dispatch, scroll)
        }
        AnimatedVisibility(visible = top == Overlay.About, enter = fadeIn(motion.fadeIn()), exit = fadeOut(motion.fadeOut())) {
            About(state)
        }
        AnimatedVisibility(visible = top is Overlay.AccessExplanation, enter = fadeIn(motion.fadeIn()), exit = fadeOut(motion.fadeOut())) {
            explained?.let { feature ->
                AccessExplanation(
                    feature = feature,
                    onContinue = { dispatch(Action.RequestAccess(feature)) },
                    onNotNow = { dispatch(Action.Back) },
                )
            }
        }
    }
}

/** The last feature explained, kept across compositions. Not state: whatever changes it already recomposes. */
private class KeptFeature {
    var value: AccessFeature? = null
}

@Composable
private fun SettingsList(state: LauncherState, dispatch: (Action) -> Unit, scroll: ScrollState) {
    val settings = state.settings
    val pulley = listOf(
        PulleyItem(stringResource(R.string.menu_replay_tutorial)) { dispatch(Action.ReplayTutorial) },
        PulleyItem(stringResource(R.string.menu_about)) { dispatch(Action.OpenAbout) },
    )
    PulleyMenu(items = pulley, atTop = { !scroll.canScrollBackward }, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Outside the scroll, so nothing scrolls under the status bar or a cutout (UI-07).
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(start = Spacing.Gutter, end = Spacing.Gutter, bottom = Spacing.Gutter)
                .testTag(SettingsTags.LIST),
            verticalArrangement = Arrangement.spacedBy(Spacing.Large),
        ) {
            PageHeader(
                title = stringResource(R.string.settings_title),
                modifier = Modifier.testTag(SettingsTags.HEADER),
                actions = pulleyActions(pulley),
            )
            Section(stringResource(R.string.top_menu_appearance)) {
                // FR-SET-01, FR-THM-09.
                AppearanceSwitch(settings.appearance, dispatch)
                Labeled(R.string.settings_highlight) {
                    HighlightChoices(settings.highlight, dark = state.appearance == Appearance.Dark, dispatch)
                }
            }
            WallpaperSettings(settings, dispatch)
            Section(stringResource(R.string.settings_home)) {
                // FR-SET-07.
                SwitchRow(stringResource(R.string.settings_show_clock), settings.showClock, { dispatch(Action.SetShowClock(it)) })
            }
            Section(stringResource(R.string.settings_app_grid)) {
                // FR-SET-09, FR-SET-10.
                Labeled(R.string.settings_grid_columns) { ColumnChoices(settings.gridColumns, dispatch) }
                ActionRow(
                    label = stringResource(R.string.settings_sort_az),
                    description = stringResource(R.string.settings_sort_az_description),
                ) { dispatch(Action.SortAppGrid) }
            }
            Section(stringResource(R.string.settings_events)) {
                // FR-SET-12: turning it on without notification access
                // explains it first, and asks only on "Continue" (FR-ONB-04).
                SwitchRow(
                    label = stringResource(R.string.settings_notifications_in_events),
                    checked = state.notificationsOn,
                    onCheckedChange = {
                        dispatch(if (it) Action.TurnOnFeature(AccessFeature.NotificationsInEvents) else Action.TurnOffNotifications)
                    },
                    description = stringResource(R.string.settings_notifications_in_events_description),
                )
            }
            Section(stringResource(R.string.settings_touch_and_motion)) {
                val followSystem = stringResource(R.string.settings_follow_system)
                // FR-SET-14, FR-GEST-08.
                Labeled(R.string.settings_haptics) {
                    ChoiceRow(
                        listOf(Choice(Haptics.FollowSystem, followSystem), Choice(Haptics.Off, stringResource(R.string.haptics_off))),
                        selected = settings.haptics,
                        onSelect = { dispatch(Action.SetHaptics(it)) },
                    )
                }
                // FR-SET-15, FR-ANIM-15.
                Labeled(R.string.settings_animations) {
                    ChoiceRow(
                        listOf(Choice(Animations.FollowSystem, followSystem), Choice(Animations.Reduced, stringResource(R.string.animations_reduced))),
                        selected = settings.animations,
                        onSelect = { dispatch(Action.SetAnimations(it)) },
                    )
                }
            }
            Section(stringResource(R.string.settings_layout)) {
                // FR-SET-16.
                ActionRow(
                    label = stringResource(R.string.settings_reset_layout),
                    description = stringResource(R.string.settings_reset_layout_description),
                ) { dispatch(Action.ResetLayout) }
            }
            // FR-ONB-03 and FR-SET-17, also in the pulley menu.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                ActionRow(stringResource(R.string.menu_replay_tutorial), Modifier.testTag(SettingsTags.REPLAY_ROW)) { dispatch(Action.ReplayTutorial) }
                ActionRow(stringResource(R.string.menu_about), Modifier.testTag(SettingsTags.ABOUT_ROW)) { dispatch(Action.OpenAbout) }
            }
        }
    }
}

/**
 * Daily Bing wallpaper (FR-SET-02), and while it is on, the market, the lock
 * screen and which networks to download on (FR-SET-03 to FR-SET-05), which
 * mean nothing without it.
 */
@Composable
private fun WallpaperSettings(settings: Settings, dispatch: (Action) -> Unit) {
    val motion = LauncherTheme.motion
    Section(stringResource(R.string.settings_wallpaper)) {
        SwitchRow(
            label = stringResource(R.string.settings_daily_wallpaper),
            checked = settings.dailyWallpaper,
            onCheckedChange = { dispatch(Action.SetDailyWallpaper(it)) },
            description = stringResource(R.string.settings_daily_wallpaper_description),
        )
        AnimatedVisibility(
            visible = settings.dailyWallpaper,
            enter = expandVertically(motion.reflow()) + fadeIn(motion.fadeIn()),
            exit = shrinkVertically(motion.reflow()) + fadeOut(motion.fadeOut()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                MarketChoice(settings.market, dispatch)
                SwitchRow(stringResource(R.string.settings_lock_screen), settings.lockScreen, { dispatch(Action.SetWallpaperLockScreen(it)) })
                Labeled(R.string.settings_download_on) {
                    ChoiceRow(
                        listOf(
                            Choice(DownloadOn.AnyNetwork, stringResource(R.string.download_any_network)),
                            Choice(DownloadOn.WifiOnly, stringResource(R.string.download_wifi_only)),
                        ),
                        selected = settings.downloadOn,
                        onSelect = { dispatch(Action.SetDownloadOn(it)) },
                    )
                }
            }
        }
    }
}

/**
 * The Bing market (FR-SET-03, FR-WP-03): a row naming the current one, which
 * opens the choices beneath it, pushing the rest down, like a context menu.
 * Automatic names the market it resolves to on this device; the others are
 * in alphabetical order of their names. Choosing one closes the list and
 * brings the row back into view, showing the new market, since the choice
 * may have been far down the list. Whether it is open is transient UI state.
 */
@Composable
private fun MarketChoice(current: Market?, dispatch: (Action) -> Unit) {
    val motion = LauncherTheme.motion
    val locale = LocalConfiguration.current.locales[0]
    var open by remember { mutableStateOf(false) }
    val row = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val choose = { market: Market? ->
        dispatch(Action.SetMarket(market))
        open = false
        scope.launch { row.bringIntoView() }
    }
    val automatic = stringResource(R.string.settings_market_automatic, marketName(MarketResolver.resolve(null, locale)))
    val markets = remember(locale) {
        val collator = Collator.getInstance(locale)
        MarketResolver.SUPPORTED.map { it to it.displayName(locale) }.sortedWith(compareBy(collator) { it.second })
    }
    Column {
        ActionRow(
            label = stringResource(R.string.settings_market),
            description = if (current == null) automatic else marketName(current),
            modifier = Modifier.bringIntoViewRequester(row).testTag(SettingsTags.MARKET),
        ) { open = !open }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(motion.reflow()) + fadeIn(motion.fadeIn()),
            exit = shrinkVertically(motion.reflow()) + fadeOut(motion.fadeOut()),
        ) {
            Column(Modifier.fillMaxWidth().selectableGroup().testTag(SettingsTags.MARKET_LIST)) {
                ListChoice(automatic, chosen = current == null) { choose(null) }
                for ((market, name) in markets) {
                    ListChoice(name, chosen = market == current) { choose(market) }
                }
            }
        }
    }
}

/** One choice in a list of them, as a radio button; the chosen one is outlined in the highlight color. */
@Composable
private fun ListChoice(label: String, chosen: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.Glass)
            .selectedOutline(chosen, LauncherTheme.highlight)
            .pressable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = chosen },
        contentAlignment = Alignment.CenterStart,
    ) {
        LauncherText(
            label,
            emphasis = if (chosen) Emphasis.Primary else Emphasis.Secondary,
            modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = Spacing.Medium),
        )
    }
}

/**
 * Automatic and the 8 preset colors (FR-SET-06, FR-AMB-05), as radio
 * buttons that wrap onto as many lines as they need. Each preset is drawn as
 * it would be shown in the current appearance, adjusted for contrast
 * (FR-AMB-02), and read by its name.
 */
@Composable
private fun HighlightChoices(current: HighlightSetting, dark: Boolean, dispatch: (Action) -> Unit) {
    val outline = LauncherTheme.highlight
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup().testTag(SettingsTags.HIGHLIGHTS),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        for (setting in HighlightSetting.entries) {
            val chosen = setting == current
            val label = stringResource(highlightName(setting))
            val source = setting.color
            val tile = Modifier
                .clip(Shapes.Glass)
                .selectedOutline(chosen, outline)
                .pressable(role = Role.RadioButton) { dispatch(Action.SetHighlight(setting)) }
            if (source == null) {
                Box(
                    tile.height(Sizes.Swatch).semantics { selected = chosen },
                    contentAlignment = Alignment.Center,
                ) {
                    LauncherText(
                        label,
                        role = TextRole.Label,
                        emphasis = if (chosen) Emphasis.Primary else Emphasis.Secondary,
                        modifier = Modifier.padding(horizontal = Spacing.Medium),
                    )
                }
            } else {
                val color = remember(source, dark) { Highlight.of(source).color(dark) }
                Box(
                    tile
                        .size(Sizes.Swatch)
                        .semantics {
                            selected = chosen
                            contentDescription = label
                        }
                        .drawBehind {
                            drawCircle(color, radius = size.minDimension / 2f - Sizes.SelectedOutline.toPx() * 2f)
                        },
                )
            }
        }
    }
}

/** The 3, 4 or 5 columns of the App Grid (FR-SET-09), as numbers in the device's digits. */
@Composable
private fun ColumnChoices(current: Int, dispatch: (Action) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    ChoiceRow(
        Settings.GRID_COLUMNS.map { Choice(it, String.format(locale, "%d", it)) },
        selected = current,
        onSelect = { dispatch(Action.SetGridColumns(it)) },
    )
}

/** A setting's [label] above its choices. */
@Composable
private fun Labeled(@StringRes label: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tiny)) {
        LauncherText(stringResource(label), modifier = Modifier.padding(horizontal = Spacing.Small))
        content()
    }
}

@StringRes
private fun highlightName(setting: HighlightSetting): Int = when (setting) {
    HighlightSetting.Automatic -> R.string.highlight_automatic
    HighlightSetting.Red -> R.string.highlight_red
    HighlightSetting.Orange -> R.string.highlight_orange
    HighlightSetting.Yellow -> R.string.highlight_yellow
    HighlightSetting.Green -> R.string.highlight_green
    HighlightSetting.Teal -> R.string.highlight_teal
    HighlightSetting.Blue -> R.string.highlight_blue
    HighlightSetting.Purple -> R.string.highlight_purple
    HighlightSetting.Pink -> R.string.highlight_pink
}

/** Test tags for the Settings page's instrumented tests. */
internal object SettingsTags {
    const val PAGE = "settings"
    const val LIST = "settings-list"
    const val HEADER = "settings-header"
    const val HIGHLIGHTS = "settings-highlights"
    const val MARKET = "settings-market"
    const val MARKET_LIST = "settings-market-list"
    const val REPLAY_ROW = "settings-replay-tutorial"
    const val ABOUT_ROW = "settings-about"
    const val ABOUT = "about"
    const val ABOUT_HEADER = "about-header"
    const val LICENSE_TEXT = "about-license"
}
