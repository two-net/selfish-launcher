package io.github.twonet.selfish.adapters.bing

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads the rest of this stream, failing as soon as it passes [limit] bytes,
 * so an oversized response is never held in memory (IF-WP-05).
 * `readAllBytes` and `readNBytes` would be simpler, but are API 33 on Android.
 */
internal fun InputStream.readLimited(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) return out.toByteArray()
        total += read
        if (total > limit) throw IOException("Response is over $limit bytes")
        out.write(buffer, 0, read)
    }
}
