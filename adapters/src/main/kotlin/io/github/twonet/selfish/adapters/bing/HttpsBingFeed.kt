package io.github.twonet.selfish.adapters.bing

import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.ports.BingFeed
import io.github.twonet.selfish.domain.ports.FeedEntry
import io.github.twonet.selfish.domain.ports.ImageVariant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * [BingFeed] over `HttpsURLConnection`, with the network policy of DESIGN §5.2:
 * `https` to Bing's hosts only, redirects followed by hand and checked, no
 * cookies or device details, and size limits on every response.
 *
 * Main-safe: requests run on [Dispatchers.IO] and stop when the caller is
 * cancelled. Nothing throws except for cancellation; a failure is a result.
 *
 * @param appVersion named in the User-Agent (IF-WP-03).
 * @param open opens a connection; tests pass a fake.
 */
class HttpsBingFeed internal constructor(
    appVersion: String,
    private val open: (URL) -> HttpURLConnection,
) : BingFeed {
    constructor(appVersion: String) : this(appVersion, { url -> url.openConnection() as HttpURLConnection })

    private val userAgent = BingRequests.userAgent(appVersion)

    override suspend fun fetchFeed(market: Market, count: Int): Result<List<FeedEntry>> = request {
        val body = get(BingRequests.feedUrl(market, count), BingRequests.MAX_FEED_BYTES)
        BingFeedParser.parse(body.decodeToString(), market).take(count)
    }

    override suspend fun fetchImage(entry: FeedEntry, variant: ImageVariant): Result<ByteArray> = request {
        val url = BingRequests.imageUrl(entry.urlBase, variant) ?: throw IOException("Not a Bing image address")
        get(url, BingRequests.MAX_IMAGE_BYTES)
    }

    private suspend fun <T> request(block: () -> T): Result<T> = try {
        Result.success(runInterruptible(Dispatchers.IO, block))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** The body at [start], following up to [BingRequests.MAX_REDIRECTS] allowed redirects. */
    private fun get(start: URI, limit: Int): ByteArray {
        var uri = start
        repeat(BingRequests.MAX_REDIRECTS + 1) {
            if (!BingRequests.allowed(uri)) throw IOException("Not an allowed address")
            val connection = open(uri.toURL())
            try {
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = BingRequests.TIMEOUT_MS
                connection.readTimeout = BingRequests.TIMEOUT_MS
                connection.setRequestProperty("User-Agent", userAgent)
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        if (connection.contentLengthLong > limit) throw IOException("Response is over $limit bytes")
                        return connection.inputStream.use { it.readLimited(limit) }
                    }
                    in REDIRECTS -> uri = BingRequests.redirect(uri, connection.getHeaderField("Location"))
                        ?: throw IOException("Redirect refused")
                    else -> throw IOException("HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    private companion object {
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
