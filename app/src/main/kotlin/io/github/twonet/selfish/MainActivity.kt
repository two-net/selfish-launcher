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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.twonet.selfish.domain.state.Action
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
        // Dark status-bar icons over Home's light scrim (FR-HOME-01), since
        // the launcher is always Light until #17 resolves the appearance
        // (FR-THM-08). The navigation bar keeps the system's own contrast.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
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
            LauncherTheme {
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
