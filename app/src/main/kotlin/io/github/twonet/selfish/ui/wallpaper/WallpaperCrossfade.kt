package io.github.twonet.selfish.ui.wallpaper

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.wallpaperDue
import io.github.twonet.selfish.ui.components.LocalWallpaperImages
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The outgoing wallpaper, drawn over the system's while a new one is applied
 * underneath, then faded out over the Ambient duration (FR-ANIM-10, DESIGN
 * §6.6). Drawn by [drawOutgoing] behind every launcher surface and the dim.
 */
@Stable
internal class WallpaperCrossfade {
    /** The outgoing image, while it covers the change. */
    var image: ImageBitmap? by mutableStateOf(null)
        private set

    private val visible = Animatable(0f)

    internal suspend fun cover(image: ImageBitmap) {
        visible.snapTo(1f)
        this.image = image
    }

    internal suspend fun reveal(fade: suspend Animatable<Float, *>.() -> Unit) {
        if (image != null) visible.fade()
        image = null
        visible.snapTo(0f)
    }

    /**
     * Draws the outgoing image over the whole area, cut around its center to
     * the area's shape, as the system shows the cropped wallpaper (FR-WP-04).
     */
    fun DrawScope.drawOutgoing() {
        val shown = image ?: return
        val strength = visible.value
        if (strength <= 0f) return
        val scale = max(size.width / shown.width, size.height / shown.height)
        val sourceWidth = (size.width / scale).roundToInt().coerceIn(1, shown.width)
        val sourceHeight = (size.height / scale).roundToInt().coerceIn(1, shown.height)
        drawImage(
            image = shown,
            srcOffset = IntOffset((shown.width - sourceWidth) / 2, (shown.height - sourceHeight) / 2),
            srcSize = IntSize(sourceWidth, sourceHeight),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            alpha = strength,
        )
    }
}

/**
 * Applies the due wallpaper while the launcher is on screen (FR-ANIM-10):
 *
 * 1. It waits until [idle], so a wallpaper is never applied in the middle of
 *    a gesture.
 * 2. Where it can, it covers the screen with the outgoing image, decoded
 *    off the main thread at [screen] size (NFR-PERF-06). It can't when the
 *    outgoing wallpaper wasn't set by the launcher, or while [canCover] is
 *    false, such as under the App Grid's blur; the change is then instant.
 * 3. It sends [Action.ApplyWallpaperNow], and once the system has set the
 *    new image underneath, or refused it, fades the cover out.
 */
@Composable
internal fun rememberWallpaperCrossfade(
    state: LauncherState,
    dispatch: (Action) -> Unit,
    idle: () -> Boolean,
    canCover: () -> Boolean,
    screen: () -> IntSize,
): WallpaperCrossfade {
    val crossfade = remember { WallpaperCrossfade() }
    val images = LocalWallpaperImages.current
    val motion = LauncherTheme.motion
    val current by rememberUpdatedState(state)
    val currentDispatch by rememberUpdatedState(dispatch)
    val currentMotion by rememberUpdatedState(motion)
    val currentIdle by rememberUpdatedState(idle)
    val currentCanCover by rememberUpdatedState(canCover)
    val currentScreen by rememberUpdatedState(screen)
    LaunchedEffect(crossfade, images) {
        while (true) {
            val due = snapshotFlow { current.wallpaperDue?.image }.first { it != null }
            snapshotFlow { currentIdle() }.first { it }
            val outgoing = current.wallpaperStatus.applied?.image
            val size = currentScreen()
            val cover = if (outgoing != null && images != null && currentCanCover()) images.thumbnail(outgoing, size.width, size.height) else null
            // The decode takes a moment; a gesture may have started meanwhile.
            snapshotFlow { currentIdle() }.first { it }
            if (current.wallpaperDue?.image != due) continue
            if (cover != null) crossfade.cover(cover)
            currentDispatch(Action.ApplyWallpaperNow)
            // Set underneath, or refused: either way it is no longer waiting.
            snapshotFlow { current.wallpaperWork.applying != due && current.wallpaperDue?.image != due }.first { it }
            crossfade.reveal { animateTo(0f, currentMotion.wallpaper()) }
        }
    }
    return crossfade
}

/**
 * Keeps [down] true while any finger is on the screen. It only watches, in
 * the Initial pass, and takes nothing (FR-ANIM-10).
 */
internal fun Modifier.watchFingers(down: MutableState<Boolean>): Modifier = pointerInput(down) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            down.value = event.changes.any { it.pressed }
        }
    }
}
