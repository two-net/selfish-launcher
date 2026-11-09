package io.github.twonet.selfish.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.ports.AppIconImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App icons ready to draw (DESIGN §5.1). The Domain hands out plain pixels
 * through [load]; this turns them into bitmaps off the main thread and keeps
 * the recent ones, so scrolling back doesn't load them again.
 *
 * Entries are keyed by [AppEntry.version], so an updated app's icon is
 * loaded again (FR-GEN-06).
 */
class AppIcons(private val load: suspend (AppId, Int) -> AppIconImage?) {
    private data class Key(val app: AppId, val version: Long, val sizePx: Int)

    private val cache = object : LruCache<Key, LoadedIcon>(MAX_BYTES) {
        override fun sizeOf(key: Key, value: LoadedIcon): Int = value.bytes
    }

    /** [app]'s icon if it is already loaded, for the first frame. */
    fun cached(app: AppEntry, sizePx: Int): LoadedIcon? = cache.get(Key(app.id, app.version, sizePx))

    /** [app]'s icon at [sizePx] square, or `null` if it can't be loaded. */
    suspend fun load(app: AppEntry, sizePx: Int): LoadedIcon? {
        val key = Key(app.id, app.version, sizePx)
        cache.get(key)?.let { return it }
        val image = load(app.id, sizePx) ?: return null
        val loaded = withContext(Dispatchers.Default) { image.toLoadedIcon() }
        cache.put(key, loaded)
        return loaded
    }

    private fun AppIconImage.toLoadedIcon() = LoadedIcon(
        icon = toImageBitmap(argb),
        shaped = shaped,
        badge = badge?.let { toImageBitmap(it) },
    )

    private fun AppIconImage.toImageBitmap(pixels: IntArray): ImageBitmap =
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap().also { it.prepareToDraw() }

    private companion object {
        /** Room for well over a screenful of icons at any density. */
        const val MAX_BYTES = 16 * 1024 * 1024
    }
}

/** An icon as [AppIcon] draws it. */
class LoadedIcon(val icon: ImageBitmap, val shaped: Boolean, val badge: ImageBitmap?) {
    internal val bytes: Int = (icon.width * icon.height + (badge?.let { it.width * it.height } ?: 0)) * Int.SIZE_BYTES
}

/** The launcher's [AppIcons]; `null` in previews and tests, where icons show their fallback. */
val LocalAppIcons = staticCompositionLocalOf<AppIcons?> { null }
