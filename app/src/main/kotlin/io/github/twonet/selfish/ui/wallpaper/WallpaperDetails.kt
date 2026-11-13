package io.github.twonet.selfish.ui.wallpaper

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.ui.components.ClockFormat
import io.github.twonet.selfish.ui.components.Glass
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.components.LocalWallpaperImages
import io.github.twonet.selfish.ui.components.PageHeader
import io.github.twonet.selfish.ui.components.PulleyItem
import io.github.twonet.selfish.ui.components.PulleyMenu
import io.github.twonet.selfish.ui.components.TextButton
import io.github.twonet.selfish.ui.components.pulleyActions
import io.github.twonet.selfish.ui.tokens.Emphasis
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Shapes
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing
import io.github.twonet.selfish.ui.tokens.TextRole
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The page about the wallpaper in use (FR-WP-09, FR-WP-10): the image, its
 * title, copyright text, date and market, "Learn more", and when the
 * wallpaper was last updated. Its pulley menu offers "Refresh now", and
 * "Keep this wallpaper" or, once kept, "Resume daily" (FR-WP-08); the
 * pulley items are also actions on its header (FR-GEST-09). "Wallpaper
 * settings" arrives with the Settings page (#18).
 *
 * It covers the whole screen over the dimmed wallpaper and takes every touch,
 * so nothing beneath can be pressed; Back and Home close it (FR-GEN-03,
 * FR-GEN-04). Content is inset by the system bars and cutouts (UI-07).
 */
@Composable
internal fun WallpaperDetails(
    state: LauncherState,
    entry: WallpaperEntry,
    dispatch: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val pulley = listOf(
        PulleyItem(stringResource(R.string.menu_refresh_now)) { dispatch(Action.RefreshWallpaper) },
        if (state.pinned == entry.image) {
            PulleyItem(stringResource(R.string.menu_resume_daily)) { dispatch(Action.ResumeDaily) }
        } else {
            PulleyItem(stringResource(R.string.menu_keep_wallpaper)) { dispatch(Action.PinWallpaper(entry.image)) }
        },
    )
    val dim = LauncherTheme.colors.dim
    Box(modifier.fillMaxSize().drawBehind { drawRect(dim) }.testTag(WallpaperDetailsTags.SCREEN)) {
        // Beneath the page, so a touch anywhere it doesn't use goes no further.
        Box(Modifier.fillMaxSize().takeEveryTouch())
        PulleyMenu(items = pulley, atTop = { !scroll.canScrollBackward }, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = Spacing.Gutter, end = Spacing.Gutter, bottom = Spacing.Gutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                PageHeader(
                    title = stringResource(R.string.menu_wallpaper_details),
                    modifier = Modifier.testTag(WallpaperDetailsTags.HEADER),
                    actions = pulleyActions(pulley),
                )
                Preview(entry, Modifier.align(Alignment.CenterHorizontally))
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Tiny)) {
                        if (entry.title.isNotBlank()) LauncherText(entry.title)
                        if (entry.copyright.isNotBlank()) {
                            LauncherText(entry.copyright, role = TextRole.Label, emphasis = Emphasis.Secondary)
                        }
                        LauncherText(published(entry.date), role = TextRole.Label, emphasis = Emphasis.Secondary)
                        LauncherText(
                            stringResource(R.string.wallpaper_market, marketName(entry.market)),
                            role = TextRole.Label,
                            emphasis = Emphasis.Secondary,
                        )
                        val link = entry.link
                        if (link != null) {
                            TextButton(stringResource(R.string.events_learn_more)) { dispatch(Action.OpenLink(link)) }
                        }
                    }
                }
                Status(state)
            }
        }
    }
}

/** The image, portrait like the screen it covers; glass until it has loaded. Decorative: the text names it. */
@Composable
private fun Preview(entry: WallpaperEntry, modifier: Modifier) {
    val images = LocalWallpaperImages.current
    val widthPx = with(LocalDensity.current) { Sizes.WallpaperPreviewWidth.roundToPx() }
    val heightPx = (widthPx / Sizes.WallpaperPreviewAspectRatio).roundToInt()
    val bitmap by produceState<ImageBitmap?>(null, images, entry.image, widthPx) {
        if (images != null) value = images.thumbnail(entry.image, widthPx, heightPx)
    }
    val shaped = modifier
        .width(Sizes.WallpaperPreviewWidth)
        .aspectRatio(Sizes.WallpaperPreviewAspectRatio)
        .clip(Shapes.Glass)
        .testTag(WallpaperDetailsTags.PREVIEW)
    val shown = bitmap
    if (shown != null) {
        Image(bitmap = shown, contentDescription = null, modifier = shaped, contentScale = ContentScale.Crop)
    } else {
        Glass(modifier = shaped) {}
    }
}

/**
 * When the wallpaper was last updated, or that "Refresh now" is checking,
 * announced as it changes (FR-WP-09, AC-04). A failed check shows no error:
 * the time of the last success simply stays (FR-WP-11).
 */
@Composable
private fun Status(state: LauncherState) {
    val lastSuccess = state.wallpaperStatus.lastSuccess
    val text = when {
        state.wallpaperWork.refreshing -> stringResource(R.string.wallpaper_checking)
        lastSuccess != null -> stringResource(R.string.wallpaper_updated, updated(lastSuccess, state))
        else -> return
    }
    LauncherText(
        text,
        modifier = Modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(WallpaperDetailsTags.STATUS),
        role = TextRole.Label,
        emphasis = Emphasis.Secondary,
    )
}

/** The day the image was published, written out in the device's language (NFR-L10N-02). */
@Composable
private fun published(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { ClockFormat.formatter(ClockFormat.LONG_DATE, locale, ZoneOffset.UTC) }
    return format.format(Date.from(date.atStartOfDay(ZoneOffset.UTC).toInstant()))
}

/** The date and time of [instant], on the device's 12- or 24-hour clock (NFR-L10N-02). */
@Composable
private fun updated(instant: Instant, state: LauncherState): String {
    val locale = LocalConfiguration.current.locales[0]
    val zone = state.clock?.zone ?: ZoneId.systemDefault()
    val skeleton = ClockFormat.SHORT_DATE + ClockFormat.time(state.clock?.is24Hour ?: true)
    val format = remember(locale, zone, skeleton) { ClockFormat.formatter(skeleton, locale, zone) }
    return format.format(Date.from(instant))
}

/** A market's name in the device's language, such as "English (United States)" for `en-US`. */
@Composable
private fun marketName(market: Market): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(market, locale) { Locale.forLanguageTag(market.code).getDisplayName(locale).ifBlank { market.code } }
}

/** Takes every touch that reaches it, so nothing beneath can be pressed. */
private fun Modifier.takeEveryTouch(): Modifier = pointerInput(Unit) {
    awaitEachGesture { awaitFirstDown().consume() }
}

/** Test tags for Wallpaper details' instrumented tests. */
internal object WallpaperDetailsTags {
    const val SCREEN = "wallpaper-details"
    const val HEADER = "wallpaper-details-header"
    const val PREVIEW = "wallpaper-details-preview"
    const val STATUS = "wallpaper-details-status"
}
