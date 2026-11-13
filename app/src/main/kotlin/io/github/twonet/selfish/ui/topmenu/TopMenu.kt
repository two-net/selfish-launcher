package io.github.twonet.selfish.ui.topmenu

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.model.AppearanceMode
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.showsAmbiences
import io.github.twonet.selfish.ui.components.Glass
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.components.LocalWallpaperImages
import io.github.twonet.selfish.ui.components.pressable
import io.github.twonet.selfish.ui.tokens.Emphasis
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Shapes
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing
import io.github.twonet.selfish.ui.tokens.TextRole
import kotlin.math.roundToInt

/**
 * The Top Menu (SRS §4.7): a glass sheet that hangs from the top edge over
 * the dimmed Home, with the appearance switch (FR-TOP-02), the Ambiences row
 * (FR-TOP-03) and shortcuts (FR-TOP-04), rendered from [state]; every touch
 * goes back as an action (AR-02).
 *
 * [io.github.twonet.selfish.ui.LauncherScreen] pulls the sheet down with the
 * finger: [progress], from 0 to 1, is read only while drawing, and the
 * sheet's height, its whole travel, goes back through [onHeight]. While it
 * [catchesTouches], nothing beneath the sheet can be pressed, and a tap there
 * closes the menu (FR-TOP-05). The sheet's content scrolls in [scrollState]
 * when it doesn't fit, as at the largest font sizes (NFR-A11Y-04); the root
 * reads it, so a swipe up scrolls before it closes.
 *
 * The sheet is padded by the status bar, cutouts and rounded corners at the
 * top; only its glass runs to the edge (UI-07).
 */
@Composable
fun TopMenu(
    state: LauncherState,
    dispatch: (Action) -> Unit,
    progress: () -> Float,
    catchesTouches: Boolean,
    onHeight: (Int) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val currentDispatch by rememberUpdatedState(dispatch)
    val close = remember { { currentDispatch(Action.LayerSettled(Layer.Home)) } }
    Box(modifier.fillMaxSize().testTag(TopMenuTags.MENU)) {
        if (catchesTouches) {
            Box(
                Modifier
                    .fillMaxSize()
                    .closeOnTap(close)
                    .testTag(TopMenuTags.OUTSIDE),
            )
        }
        Glass(
            shape = Shapes.TopSheet,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onHeight(it.height) }
                .graphicsLayer { translationY = -(1f - progress()) * size.height }
                .testTag(TopMenuTags.SHEET),
        ) {
            Column(
                Modifier
                    .verticalScroll(scrollState)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(Spacing.Gutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.Large),
            ) {
                Section(stringResource(R.string.top_menu_appearance)) {
                    AppearanceSwitch(state.settings.appearance, dispatch)
                }
                if (state.showsAmbiences) {
                    Section(stringResource(R.string.top_menu_ambiences)) {
                        Ambiences(state.wallpapers, state.pinned, dispatch)
                    }
                }
                Column {
                    Shortcut(stringResource(R.string.top_menu_system_settings)) { dispatch(Action.OpenSystemSettings) }
                    // Only once there is a wallpaper to show, as in Events (FR-EVT-06).
                    if (state.wallpaper != null) {
                        Shortcut(stringResource(R.string.menu_wallpaper_details)) { dispatch(Action.OpenWallpaperDetails) }
                    }
                    // "Launcher settings" arrives with the Settings page (#18).
                }
            }
        }
    }
}

/** Test tags for [TopMenu]'s instrumented tests. */
internal object TopMenuTags {
    const val MENU = "top-menu"
    const val SHEET = "top-menu-sheet"
    const val OUTSIDE = "top-menu-outside"
    const val AMBIENCES = "top-menu-ambiences"
}

/** A labeled part of the menu; the label is a heading, so screen readers can jump between parts. */
@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        LauncherText(label, role = TextRole.Label, emphasis = Emphasis.Secondary, modifier = Modifier.semantics { heading() })
        content()
    }
}

private val APPEARANCES = listOf(
    AppearanceMode.System to R.string.appearance_system,
    AppearanceMode.Light to R.string.appearance_light,
    AppearanceMode.Dark to R.string.appearance_dark,
)

/**
 * Light, Dark and System, as radio buttons; the current mode is outlined in
 * the highlight color (FR-TOP-02, FR-AMB-04). #17 makes the launcher follow it.
 */
@Composable
private fun AppearanceSwitch(current: AppearanceMode, dispatch: (Action) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        for ((mode, label) in APPEARANCES) {
            AppearanceOption(label, selected = mode == current, modifier = Modifier.weight(1f)) {
                dispatch(Action.SetAppearance(mode))
            }
        }
    }
}

@Composable
private fun AppearanceOption(@StringRes label: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(Shapes.Glass)
            .selectedOutline(selected, LauncherTheme.highlight)
            .pressable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        LauncherText(
            text = stringResource(label),
            emphasis = if (selected) Emphasis.Primary else Emphasis.Secondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(Spacing.Small),
        )
    }
}

/**
 * "Daily", then the cached images, newest first, as radio buttons; the
 * active one is outlined in the highlight color (FR-TOP-03). Choosing an
 * image pins it; choosing Daily resumes the daily changes (FR-WP-08).
 */
@Composable
private fun Ambiences(wallpapers: List<WallpaperEntry>, pinned: ImageId?, dispatch: (Action) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup().testTag(TopMenuTags.AMBIENCES),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        item(key = DAILY_KEY) {
            AmbienceTile(label = null, selected = pinned == null, onClick = { dispatch(Action.ResumeDaily) }) {
                Glass(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LauncherText(stringResource(R.string.ambience_daily), role = TextRole.Label, textAlign = TextAlign.Center)
                }
            }
        }
        items(wallpapers, key = { it.image.value }) { entry ->
            AmbienceTile(label = entry.title, selected = pinned == entry.image, onClick = { dispatch(Action.PinWallpaper(entry.image)) }) {
                AmbienceThumbnail(entry.image)
            }
        }
    }
}

/** Not an [ImageId], so it can never be mistaken for one. */
private const val DAILY_KEY = 0

/** One tile of the Ambiences row; [label] reads it to screen readers when its content has no text. */
@Composable
private fun AmbienceTile(label: String?, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .width(Sizes.AmbienceWidth)
            .aspectRatio(Sizes.AmbienceAspectRatio)
            .clip(Shapes.Glass)
            .selectedOutline(selected, LauncherTheme.highlight)
            .pressable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                if (label != null) contentDescription = label
            },
    ) {
        content()
    }
}

/** The cached [image], cropped to the tile, or glass until it has loaded or if it isn't cached. */
@Composable
private fun AmbienceThumbnail(image: ImageId) {
    val images = LocalWallpaperImages.current
    val widthPx = with(LocalDensity.current) { Sizes.AmbienceWidth.roundToPx() }
    val heightPx = (widthPx / Sizes.AmbienceAspectRatio).roundToInt()
    val bitmap by produceState<ImageBitmap?>(null, images, image, widthPx) {
        if (images != null) value = images.thumbnail(image, widthPx, heightPx)
    }
    val shown = bitmap
    if (shown != null) {
        Image(bitmap = shown, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Glass(Modifier.fillMaxSize()) {}
    }
}

/** A full-width row that opens something (FR-TOP-04). */
@Composable
private fun Shortcut(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.Glass)
            .pressable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        LauncherText(label, modifier = Modifier.padding(horizontal = Spacing.Small, vertical = Spacing.Medium))
    }
}

/** The outline of the chosen option, in the [highlight] color (FR-AMB-04), drawn over its content. */
private fun Modifier.selectedOutline(selected: Boolean, highlight: Color): Modifier =
    if (selected) border(Sizes.SelectedOutline, highlight, Shapes.Glass) else this

/**
 * Takes every touch that reaches it, so nothing beneath can be pressed, and
 * calls [onTap] for a tap (FR-TOP-05). Swipes still reach the launcher's
 * root, which watches every touch before anything under it.
 */
private fun Modifier.closeOnTap(onTap: () -> Unit): Modifier = pointerInput(onTap) {
    awaitEachGesture {
        awaitFirstDown().consume()
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
        up.consume()
        onTap()
    }
}
