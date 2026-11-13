package io.github.twonet.selfish.ui.motion

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import io.github.twonet.selfish.ui.findActivity
import io.github.twonet.selfish.ui.tokens.Sizes
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.function.Consumer
import kotlin.math.roundToInt

/**
 * Blurs the wallpaper behind the launcher's window in proportion to
 * [progress], from none at 0 to [Sizes.BlurRadius] at 1 (FR-GRID-01,
 * FR-ANIM-01, DESIGN §6.6). Only where the platform supports it: API 31+,
 * with cross-window blur enabled, which Battery Saver and some devices turn
 * off. Elsewhere the dim alone stays (NFR-COMP-03).
 *
 * The window's blur is part of the UI toolkit, so it belongs here rather
 * than in an adapter (DESIGN §3.1).
 */
@Composable
fun WindowBlur(progress: () -> Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BackgroundBlur(progress)
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun BackgroundBlur(progress: () -> Float) {
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.window } ?: return
    val windowManager = remember(context) { context.getSystemService(WindowManager::class.java) }
    var enabled by remember { mutableStateOf(windowManager.isCrossWindowBlurEnabled) }
    DisposableEffect(windowManager) {
        val listener = Consumer<Boolean> { enabled = it }
        windowManager.addCrossWindowBlurEnabledListener(listener)
        onDispose { windowManager.removeCrossWindowBlurEnabledListener(listener) }
    }
    val maxRadius = with(LocalDensity.current) { Sizes.BlurRadius.roundToPx() }
    LaunchedEffect(window, enabled, maxRadius) {
        if (!enabled) {
            window.setBlur(0)
            return@LaunchedEffect
        }
        snapshotFlow { (progress() * maxRadius).roundToInt() }
            .distinctUntilChanged()
            .collect { window.setBlur(it) }
    }
    DisposableEffect(window) { onDispose { window.setBlur(0) } }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Window.setBlur(radius: Int) = setBackgroundBlurRadius(radius)
