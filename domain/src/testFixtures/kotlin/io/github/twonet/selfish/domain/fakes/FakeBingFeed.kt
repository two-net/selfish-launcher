package io.github.twonet.selfish.domain.fakes

import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.ports.BingFeed
import io.github.twonet.selfish.domain.ports.FeedEntry

class FakeBingFeed : BingFeed {
    var feed: Result<List<FeedEntry>> = Result.success(emptyList())
    val images = mutableMapOf<String, ByteArray>()

    val feedRequests = mutableListOf<Pair<Market, Int>>()
    val imageRequests = mutableListOf<String>()

    override suspend fun fetchFeed(market: Market, count: Int): Result<List<FeedEntry>> {
        feedRequests += market to count
        return feed.map { it.take(count) }
    }

    override suspend fun fetchImage(url: String): Result<ByteArray> {
        imageRequests += url
        return images[url]?.let { Result.success(it) }
            ?: Result.failure(java.io.IOException("No image for $url"))
    }
}
