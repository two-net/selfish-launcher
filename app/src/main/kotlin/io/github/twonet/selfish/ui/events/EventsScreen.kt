package io.github.twonet.selfish.ui.events

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.events.NotificationGroup
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.NotificationItem
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.clearable
import io.github.twonet.selfish.domain.state.notificationGroups
import io.github.twonet.selfish.domain.state.notificationsOn
import io.github.twonet.selfish.domain.state.pendingClear
import io.github.twonet.selfish.domain.state.showsAccessCard
import io.github.twonet.selfish.ui.components.AppIcon
import io.github.twonet.selfish.ui.components.ClockFormat
import io.github.twonet.selfish.ui.components.Glass
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.components.LocalWallpaperImages
import io.github.twonet.selfish.ui.components.PageHeader
import io.github.twonet.selfish.ui.components.PulleyItem
import io.github.twonet.selfish.ui.components.PulleyMenu
import io.github.twonet.selfish.ui.components.SwipeToDismiss
import io.github.twonet.selfish.ui.components.onBoundsInWindow
import io.github.twonet.selfish.ui.components.pressable
import io.github.twonet.selfish.ui.components.pulleyActions
import io.github.twonet.selfish.ui.motion.SidewaysClaims
import io.github.twonet.selfish.ui.tokens.Emphasis
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.PENDING_ALPHA
import io.github.twonet.selfish.ui.tokens.Shapes
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing
import io.github.twonet.selfish.ui.tokens.TextRole
import java.time.Instant
import java.time.ZoneId
import java.util.Date

/**
 * Events (SRS §4.6), the view beside Home, rendered from [state]; every
 * touch goes back as an action (AR-02). Swiping sideways to and from it is
 * [io.github.twonet.selfish.ui.LauncherScreen]'s.
 *
 * - Today's date is its page header (FR-EVT-02).
 * - The "Today's wallpaper" card shows the image of the day with its title,
 *   copyright text and "Learn more"; tapping it opens Wallpaper details
 *   (FR-EVT-03, FR-WP-10). It appears once #14 provides the wallpaper.
 * - With notifications on, they are listed grouped by app (FR-EVT-04). A
 *   dismissible one is swiped sideways to dismiss it, so it registers in
 *   [claims] (FR-EVT-05).
 * - Without them, a card explains them and offers to turn them on, until it
 *   is dismissed (FR-EVT-07).
 * - "No new events" when there is nothing besides the wallpaper card
 *   (FR-EVT-08).
 * - Its pulley menu has "Wallpaper details" and, with notifications to
 *   clear, "Clear notifications" (FR-EVT-06); screen readers find them as
 *   actions on the header (FR-GEST-09).
 *
 * Content is inset by the system bars, cutouts and rounded corners (UI-07).
 */
@Composable
internal fun EventsScreen(
    state: LauncherState,
    dispatch: (Action) -> Unit,
    claims: SidewaysClaims,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val wallpaper = state.wallpaper
    val notificationsOn = state.notificationsOn
    val groups = if (notificationsOn) state.notificationGroups else emptyList()
    val pending = state.pendingClear
    val pulley = buildList {
        if (wallpaper != null) add(PulleyItem(stringResource(R.string.menu_wallpaper_details)) { dispatch(Action.OpenWallpaperDetails) })
        if (notificationsOn && state.clearable.isNotEmpty()) {
            add(PulleyItem(stringResource(R.string.menu_clear_notifications)) { dispatch(Action.ClearNotifications) })
        }
    }
    val zone = state.clock?.zone ?: ZoneId.systemDefault()
    val entries = buildList {
        add(EventsEntry.Header)
        if (wallpaper != null) add(EventsEntry.Wallpaper(wallpaper))
        if (state.showsAccessCard) add(EventsEntry.NotificationsCard)
        groups.forEach { group ->
            add(EventsEntry.Group(group))
            group.items.forEach { add(EventsEntry.Notification(it)) }
        }
        if (!state.showsAccessCard && groups.isEmpty()) add(EventsEntry.NoNewEvents)
    }

    val motion = LauncherTheme.motion
    PulleyMenu(
        items = pulley,
        atTop = { !listState.canScrollBackward },
        modifier = modifier.fillMaxSize().testTag(EventsTags.SCREEN),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag(EventsTags.LIST),
            contentPadding = PaddingValues(start = Spacing.Gutter, end = Spacing.Gutter, bottom = Spacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            items(entries, key = EventsEntry::key) { entry ->
                // Entries fade in and out and move over the Medium duration, as the grid's do (FR-ANIM-08).
                val modifier = Modifier.animateItem(fadeInSpec = motion.fadeIn(), placementSpec = motion.reflow(), fadeOutSpec = motion.fadeOut())
                when (entry) {
                    EventsEntry.Header -> PageHeader(
                        title = state.clock?.let { today(it) }.orEmpty(),
                        modifier = modifier.testTag(EventsTags.HEADER),
                        actions = pulleyActions(pulley),
                    )
                    is EventsEntry.Wallpaper -> WallpaperCard(entry.entry, dispatch, modifier)
                    EventsEntry.NotificationsCard -> NotificationsCard(dispatch, modifier)
                    is EventsEntry.Group -> GroupHeader(entry.group, modifier)
                    is EventsEntry.Notification ->
                        NotificationRow(entry.item, entry.item.ref in pending, zone, state.clock, claims, dispatch, modifier)
                    EventsEntry.NoNewEvents -> NoNewEvents(modifier)
                }
            }
        }
    }
}

/** Test tags for [EventsScreen]'s instrumented tests. */
internal object EventsTags {
    const val SCREEN = "events"
    const val LIST = "events-list"
    const val HEADER = "events-header"
    const val WALLPAPER = "events-wallpaper"
    const val THUMBNAIL = "events-thumbnail"
    const val NOTIFICATION = "events-notification"
}

/** Today's date in full, in the device locale (FR-EVT-02, NFR-L10N-02). */
@Composable
private fun today(clock: ClockInfo): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale, clock.zone) { ClockFormat.formatter(ClockFormat.DATE, locale, clock.zone) }
    return format.format(Date.from(clock.now))
}

/** One entry in Events' list, in order: the header, then the cards, then the notifications or "No new events". */
private sealed interface EventsEntry {
    val key: String

    data object Header : EventsEntry {
        override val key = "header"
    }

    data class Wallpaper(val entry: WallpaperEntry) : EventsEntry {
        override val key = "wallpaper"
    }

    data object NotificationsCard : EventsEntry {
        override val key = "notifications-card"
    }

    data class Group(val group: NotificationGroup) : EventsEntry {
        override val key = "group:${group.packageName}:${group.userSerial}"
    }

    data class Notification(val item: NotificationItem) : EventsEntry {
        override val key = "notification:${item.key}"
    }

    data object NoNewEvents : EventsEntry {
        override val key = "no-new-events"
    }
}

/**
 * The image of the day in use as the wallpaper, with its title, copyright
 * text and "Learn more" (FR-EVT-03, FR-WP-10). Tapping it opens Wallpaper
 * details.
 */
@Composable
private fun WallpaperCard(entry: WallpaperEntry, dispatch: (Action) -> Unit, modifier: Modifier) {
    Glass(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClickLabel = stringResource(R.string.menu_wallpaper_details)) { dispatch(Action.OpenWallpaperDetails) }
            .testTag(EventsTags.WALLPAPER),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Thumbnail(entry)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Tiny)) {
                LauncherText(stringResource(R.string.events_wallpaper_title), role = TextRole.Label, emphasis = Emphasis.Secondary)
                if (entry.title.isNotBlank()) LauncherText(entry.title)
                if (entry.copyright.isNotBlank()) {
                    LauncherText(entry.copyright, role = TextRole.Label, emphasis = Emphasis.Secondary)
                }
                val link = entry.link
                if (link != null) {
                    TextButton(stringResource(R.string.events_learn_more)) { dispatch(Action.OpenLink(link)) }
                }
            }
        }
    }
}

/** The cached image, cropped to a square, or glass until it has loaded or if it isn't cached. Decorative. */
@Composable
private fun Thumbnail(entry: WallpaperEntry) {
    val images = LocalWallpaperImages.current
    val sizePx = with(LocalDensity.current) { Sizes.WallpaperThumbnail.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, images, entry.image, sizePx) {
        if (images != null) value = images.thumbnail(entry.image, sizePx, sizePx)
    }
    val shown = bitmap
    val modifier = Modifier.size(Sizes.WallpaperThumbnail).clip(Shapes.Glass).testTag(EventsTags.THUMBNAIL)
    if (shown != null) {
        Image(bitmap = shown, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Glass(modifier = modifier) {}
    }
}

/**
 * What notifications in Events are, and a way to turn them on, which asks
 * for access first if needed (FR-EVT-07, FR-ONB-04); "Hide" dismisses it
 * for good.
 */
@Composable
private fun NotificationsCard(dispatch: (Action) -> Unit, modifier: Modifier) {
    Glass(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = Spacing.Gutter, end = Spacing.Small, top = Spacing.Medium)) {
            LauncherText(stringResource(R.string.events_notifications_card), modifier = Modifier.padding(end = Spacing.Small))
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                TextButton(stringResource(R.string.events_notifications_hide)) { dispatch(Action.HideNotificationsCard) }
                TextButton(stringResource(R.string.events_notifications_turn_on)) { dispatch(Action.TurnOnNotifications) }
            }
        }
    }
}

/** A short action in a card, as text, at least the minimum touch size (UI-06). */
@Composable
private fun TextButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.pressable(role = Role.Button, onClick = onClick).padding(horizontal = Spacing.Small),
        contentAlignment = Alignment.CenterStart,
    ) {
        LauncherText(label, role = TextRole.Label, maxLines = 1)
    }
}

/** An app's icon and name above its notifications (FR-EVT-04). */
@Composable
private fun GroupHeader(group: NotificationGroup, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.Small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val app = group.app
        if (app != null) AppIcon(app) else AppIcon(label = group.label)
        LauncherText(group.label, modifier = Modifier.weight(1f), maxLines = 1)
    }
}

/**
 * One notification: its title, text and time (FR-EVT-04). Tapping it opens
 * what it points to. A dismissible one is swiped sideways to dismiss it,
 * and screen readers get that as an action (FR-EVT-05). One waiting to be
 * cleared is faded until its remorse timer runs out (FR-EVT-06).
 */
@Composable
private fun NotificationRow(
    item: NotificationItem,
    pending: Boolean,
    zone: ZoneId,
    clock: ClockInfo?,
    claims: SidewaysClaims,
    dispatch: (Action) -> Unit,
    modifier: Modifier,
) {
    val dismissible = item.clearable && !pending
    val dismissLabel = stringResource(R.string.action_dismiss_notification)
    var bounds by remember { mutableStateOf<Bounds?>(null) }
    SwipeToDismiss(
        enabled = dismissible,
        claims = claims,
        onDismiss = { dispatch(Action.DismissNotification(item.key)) },
        modifier = modifier.fillMaxWidth().graphicsLayer { alpha = if (pending) PENDING_ALPHA else 1f },
    ) {
        Glass(
            modifier = Modifier
                .fillMaxWidth()
                .onBoundsInWindow { bounds = it }
                .pressable { dispatch(Action.OpenNotification(item.key, bounds)) }
                .semantics {
                    if (dismissible) {
                        customActions = listOf(
                            CustomAccessibilityAction(dismissLabel) {
                                dispatch(Action.DismissNotification(item.key))
                                true
                            },
                        )
                    }
                }
                .testTag(EventsTags.NOTIFICATION),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = Spacing.Medium)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalAlignment = Alignment.CenterVertically) {
                    LauncherText(item.title, modifier = Modifier.weight(1f), maxLines = 1)
                    LauncherText(
                        text = time(item.timeMillis, zone, clock),
                        role = TextRole.Label,
                        emphasis = Emphasis.Secondary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
                if (item.text.isNotBlank()) {
                    LauncherText(item.text, role = TextRole.Label, emphasis = Emphasis.Secondary, maxLines = TEXT_LINES)
                }
            }
        }
    }
}

/** A notification's time: the time of day if it is from today, otherwise its date (NFR-L10N-02). */
@Composable
private fun time(millis: Long, zone: ZoneId, clock: ClockInfo?): String {
    val locale = LocalConfiguration.current.locales[0]
    val instant = Instant.ofEpochMilli(millis)
    val today = clock?.let { it.now.atZone(zone).toLocalDate() }
    val skeleton = if (today == null || instant.atZone(zone).toLocalDate() == today) {
        ClockFormat.time(clock?.is24Hour ?: true)
    } else {
        ClockFormat.SHORT_DATE
    }
    val format = remember(locale, zone, skeleton) { ClockFormat.formatter(skeleton, locale, zone) }
    return format.format(Date.from(instant))
}

/** Nothing besides the wallpaper card (FR-EVT-08). */
@Composable
private fun NoNewEvents(modifier: Modifier) {
    LauncherText(
        text = stringResource(R.string.events_no_new_events),
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.Large),
        emphasis = Emphasis.Secondary,
        textAlign = TextAlign.Center,
    )
}

/** How many lines of a notification's text are shown. */
private const val TEXT_LINES = 3
