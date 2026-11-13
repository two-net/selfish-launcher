package io.github.twonet.selfish.adapters.bing

import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.ports.ImageVariant
import io.github.twonet.selfish.domain.wallpaper.WallpaperPolicy
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * What the launcher may ask Bing for, and where (DESIGN §5.2): the feed and
 * image addresses, the host allowlist and the redirect decision. Pure, so it
 * is tested on the JVM.
 */
internal object BingRequests {
    /** The feed is a few kilobytes; anything near this is not the feed (IF-WP-05). */
    const val MAX_FEED_BYTES = 256 * 1024

    /** The image limit of IF-WP-05, shared with the domain's own check. */
    const val MAX_IMAGE_BYTES = WallpaperPolicy.MAX_IMAGE_BYTES

    /** Redirects followed by hand, each one checked (IF-WP-02). */
    const val MAX_REDIRECTS = 3

    /** For connecting and for each read. */
    const val TIMEOUT_MS = 15_000

    /** The most entries the feed returns in one request (IF-WP-04). */
    const val MAX_ENTRIES = 8

    private val ORIGIN = URI("https://www.bing.com/")
    private val HOSTS = listOf("bing.com", "bing.net")

    /** The feed for [market]: only `n` and `mkt` ever vary (IF-WP-03). */
    fun feedUrl(market: Market, count: Int): URI {
        // The Charset overload is API 33; minSdk is 29.
        val mkt = URLEncoder.encode(market.code, "UTF-8")
        return ORIGIN.resolve("/HPImageArchive.aspx?format=js&idx=0&n=${count.coerceIn(1, MAX_ENTRIES)}&mkt=$mkt")
    }

    /**
     * [variant] of the image at [urlBase], the feed's address without a size,
     * such as `/th?id=OHR.Name_EN-US123`; `null` if it isn't a Bing address.
     */
    fun imageUrl(urlBase: String, variant: ImageVariant): URI? {
        val size = when (variant) {
            ImageVariant.Portrait -> "_1080x1920.jpg"
            ImageVariant.Landscape -> "_1920x1080.jpg"
        }
        return resolve(ORIGIN, urlBase + size)
    }

    /** Where a redirect from [from] to [location] leads, or `null` if it may not be followed (IF-WP-02). */
    fun redirect(from: URI, location: String?): URI? = location?.let { resolve(from, it) }

    /**
     * Whether [uri] may be requested: `https` on a Bing host or one of its
     * subdomains, on the default port and without user info (IF-WP-02,
     * NFR-PRIV-01).
     */
    fun allowed(uri: URI): Boolean {
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /** Names the launcher and its version instead of the platform's default, which names the device (IF-WP-03). */
    fun userAgent(appVersion: String): String = "SelfishLauncher/$appVersion"

    private fun resolve(base: URI, reference: String): URI? = try {
        base.resolve(reference).takeIf(::allowed)
    } catch (_: IllegalArgumentException) {
        // URI.resolve(String) reports a malformed reference this way.
        null
    }
}
