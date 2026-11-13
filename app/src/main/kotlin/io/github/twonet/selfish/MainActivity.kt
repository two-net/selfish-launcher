package io.github.twonet.selfish

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.twonet.selfish.domain.model.Appearance
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.appearance
import io.github.twonet.selfish.domain.state.highlight
import io.github.twonet.selfish.domain.state.reducedMotion
import io.github.twonet.selfish.ui.components.AppIcons
import io.github.twonet.selfish.ui.components.LocalAppIcons
import io.github.twonet.selfish.ui.components.LocalWallpaperImages
import io.github.twonet.selfish.ui.components.WallpaperImages
import io.github.twonet.selfish.ui.LauncherScreen
import io.github.twonet.selfish.ui.tokens.LauncherTheme

/**
 * The one Activity for every launcher surface (DESIGN §6.1). It turns the
 * system's Home and Back into actions, and tells the store when the launcher
 * is on screen.
 */
class MainActivity : ComponentActivity() {
    private val graph: AppGraph
        get() = (application as SelfishApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent bars, with icons for the system's theme until the first
        // frame: System is the default appearance. From then on `SystemBars`
        // sets them for the appearance in use (FR-THM-08, FR-HOME-01). The
        // navigation bar keeps the system's enforced contrast, a scrim that
        // only 3-button navigation gets, which follows the same icon setting.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        val store = graph.store
        graph.attach(this)

        // Always enabled, so Back never leaves the launcher; on bare Home the
        // store ignores it (FR-GEN-04). This also serves predictive back.
        onBackPressedDispatcher.addCallback(this) { store.dispatch(Action.Back) }

        // As the Home app in singleTask mode, every Home press arrives here
        // (FR-GEN-03). The intent that created the Activity doesn't.
        addOnNewIntentListener { intent ->
            if (intent.isHomePress()) store.dispatch(Action.HomePressed)
        }

        val icons = AppIcons(store::icon)
        val wallpapers = WallpaperImages(store::wallpaperImage)
        setContent {
            val state = store.state.collectAsStateWithLifecycle()
            // Read apart from the rest of the state, so the theme changes only when these do.
            val reducedMotion by remember { derivedStateOf { state.value.reducedMotion } }
            val highlight by remember { derivedStateOf { state.value.highlight } }
            val dark by remember { derivedStateOf { state.value.appearance == Appearance.Dark } }
            LauncherTheme(dark = dark, highlight = highlight, reducedMotion = reducedMotion) {
                CompositionLocalProvider(LocalAppIcons provides icons, LocalWallpaperImages provides wallpapers) {
                    LauncherScreen(state.value, store::dispatch)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        graph.store.dispatch(Action.HomeShown)
    }

    override fun onStop() {
        graph.store.dispatch(Action.HomeHidden)
        super.onStop()
    }

    override fun onDestroy() {
        graph.detach(this)
        super.onDestroy()
    }

    private fun Intent.isHomePress(): Boolean = action == Intent.ACTION_MAIN && hasCategory(Intent.CATEGORY_HOME)
}
