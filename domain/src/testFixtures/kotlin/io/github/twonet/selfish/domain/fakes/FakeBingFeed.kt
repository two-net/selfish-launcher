package io.github.twonet.selfish.domain.fakes

import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.ports.BingFeed
import io.github.twonet.selfish.domain.ports.FeedEntry
import io.github.twonet.selfish.domain.ports.ImageVariant
import java.io.IOException

class FakeBingFeed : BingFeed {
    /** What [fetchFeed] answers; it returns at most the count asked for. */
    var feed: Result<List<FeedEntry>> = Result.success(emptyList())

    /** The bytes [fetchImage] returns per `urlBase` and variant; a missing one fails. */
    val images = mutableMapOf<Pair<String, ImageVariant>, ByteArray>()

    val feedRequests = mutableListOf<Pair<Market, Int>>()
    val imageRequests = mutableListOf<Pair<String, ImageVariant>>()

    override suspend fun fetchFeed(market: Market, count: Int): Result<List<FeedEntry>> {
        feedRequests += market to count
        return feed.map { it.take(count) }
    }

    override suspend fun fetchImage(entry: FeedEntry, variant: ImageVariant): Result<ByteArray> {
        val key = entry.urlBase to variant
        imageRequests += key
        return images[key]?.let { Result.success(it.copyOf()) }
            ?: Result.failure(IOException("No $variant image for ${entry.urlBase}"))
    }
}
