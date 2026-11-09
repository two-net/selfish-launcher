package io.github.twonet.selfish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.twonet.selfish.ui.home.HomeScreen
import io.github.twonet.selfish.ui.tokens.LauncherTheme

/** The one Activity for every launcher surface (DESIGN §6.1). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = (application as SelfishApp).graph.store
        setContent {
            val state = store.state.collectAsStateWithLifecycle()
            LauncherTheme {
                HomeScreen(state.value, store::dispatch)
            }
        }
    }
}
