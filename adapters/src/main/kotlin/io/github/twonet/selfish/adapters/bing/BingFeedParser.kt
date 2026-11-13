package io.github.twonet.selfish.adapters.bing

import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.ports.FeedEntry
import io.github.twonet.selfish.domain.ports.ImageVariant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

/**
 * Reads the image-of-the-day feed (DESIGN §5.2, IF-WP-01, IF-WP-06). The feed
 * is read as a JSON tree rather than into classes, so unknown keys and fields
 * of an unexpected type are ignored instead of failing the whole feed.
 *
 * Fields are passed on as the feed has them. The domain decides what a
 * missing one means (`WallpaperPolicy.entryOf`): without a title, the
 * copyright text is shown; a link that isn't `https` is dropped.
 */
internal object BingFeedParser {
    /** A size suffix in the feed's `url`, such as `_1920x1080.jpg`. */
    private val SIZE_SUFFIX = Regex("""_(?:\d+x\d+|UHD)\.jpg""")

    /**
     * The entries in [body] for [market], newest first. An entry without a
     * start date or a Bing image address is left out. Throws if [body] isn't
     * the feed at all.
     */
    fun parse(body: String, market: Market): List<FeedEntry> {
        val root = Json.parseToJsonElement(body) as? JsonObject ?: throw IOException("The feed is not a JSON object")
        val images = root["images"] as? JsonArray ?: throw IOException("The feed has no images")
        return images
            .mapNotNull { (it as? JsonObject)?.let { image -> entryOf(image, market) } }
            .sortedByDescending { it.startDate }
    }

    private fun entryOf(image: JsonObject, market: Market): FeedEntry? {
        val startDate = image.string("startdate") ?: return null
        val urlBase = (image.string("urlbase") ?: image.string("url")?.let(::baseOf))
            ?.takeIf { BingRequests.imageUrl(it, ImageVariant.Portrait) != null }
            ?: return null
        return FeedEntry(
            startDate = startDate,
            urlBase = urlBase,
            market = market.code,
            hash = image.string("hsh"),
            title = image.string("title"),
            copyright = image.string("copyright"),
            link = image.string("copyrightlink"),
        )
    }

    /** The address in a sized `url` up to its size, as `urlbase` would give it. */
    private fun baseOf(url: String): String? = SIZE_SUFFIX.find(url)?.let { url.substring(0, it.range.first) }

    /** The field [key] if it is a string with something in it. */
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
}
