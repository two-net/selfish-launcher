package io.github.twonet.selfish.adapters.bing

import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.ports.BingFeed
import io.github.twonet.selfish.domain.ports.FeedEntry
import io.github.twonet.selfish.domain.ports.ImageVariant
import java.io.IOException

/**
 * [BingFeed] over `HttpsURLConnection`, with the network policy of DESIGN §5.2.
 * Stub until #15: every request fails, so nothing leaves the device yet.
 */
class HttpsBingFeed : BingFeed {
    override suspend fun fetchFeed(market: Market, count: Int): Result<List<FeedEntry>> =
        Result.failure(IOException("The Bing feed client arrives with #15"))

    override suspend fun fetchImage(entry: FeedEntry, variant: ImageVariant): Result<ByteArray> =
        Result.failure(IOException("The Bing feed client arrives with #15"))
}
