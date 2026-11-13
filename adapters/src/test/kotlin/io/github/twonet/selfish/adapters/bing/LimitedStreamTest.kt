package io.github.twonet.selfish.adapters.bing

import io.github.twonet.selfish.domain.Verifies
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Responses are read only up to their limit (IF-WP-05, DESIGN §5.2). */
class LimitedStreamTest {
    /** Serves [size] bytes, or endlessly, [chunk] at a time, counting what it served. */
    private class Source(private val size: Long = Long.MAX_VALUE, private val chunk: Int = Int.MAX_VALUE) : InputStream() {
        var served = 0L
            private set

        override fun read(): Int = if (served < size) 7.also { served++ } else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (served >= size) return -1
            val n = minOf(len.toLong(), chunk.toLong(), size - served).toInt()
            b.fill(7, off, off + n)
            served += n
            return n
        }
    }

    @Test
    @Verifies("IF-WP-05")
    fun `a response of exactly the limit is read in full`() {
        val bytes = ByteArray(5_000_000) { it.toByte() }

        assertContentEquals(bytes, ByteArrayInputStream(bytes).readLimited(5_000_000))
    }

    @Test
    @Verifies("IF-WP-05")
    fun `a response one byte over the limit is refused`() {
        assertFailsWith<IOException> { Source(size = 5_000_001).readLimited(5_000_000) }
    }

    @Test
    @Verifies("IF-WP-05")
    fun `an endless response is cut off near the limit`() {
        val source = Source()

        assertFailsWith<IOException> { source.readLimited(5_000_000) }
        assertTrue(source.served <= 5_000_000 + 8 * 1024, "Read ${source.served} bytes")
    }

    @Test
    @Verifies("IF-WP-05")
    fun `a response that trickles in counts every byte`() {
        assertEquals(1000, Source(size = 1000, chunk = 1).readLimited(1000).size)
        assertFailsWith<IOException> { Source(size = 1001, chunk = 1).readLimited(1000) }
    }

    @Test
    fun `an empty response is empty`() {
        assertEquals(0, ByteArrayInputStream(ByteArray(0)).readLimited(10).size)
    }
}
