package io.github.twonet.selfish.domain.ports

import io.github.twonet.selfish.domain.model.Market

/** The Bing image-of-the-day feed (SRS §5.3, IF-WP-01 to IF-WP-06). */
interface BingFeed {
    /** The newest [count] entries for [market], newest first (IF-WP-01, IF-WP-04, IF-WP-06). */
    suspend fun fetchFeed(market: Market, count: Int): Result<List<FeedEntry>>

    /** Downloads one image, within the size limit of IF-WP-05. */
    suspend fun fetchImage(url: String): Result<ByteArray>
}

/** One feed entry. Only [startDate] and [urlBase] are required (IF-WP-06). */
data class FeedEntry(
    /** `yyyyMMdd`, as the feed reports it. */
    val startDate: String,
    val urlBase: String,
    val market: String,
    val hash: String? = null,
    val title: String? = null,
    val copyright: String? = null,
    val link: String? = null,
)
