package io.github.twonet.selfish.domain.theme

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Rgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** WCAG 2.1 relative luminance, contrast ratio and alpha compositing. */
class ContrastTest {
    private val white = Rgb(0xFFFFFF)
    private val black = Rgb(0x000000)

    @Test
    @Verifies("NFR-A11Y-03")
    fun `luminance runs from black to white`() {
        assertEquals(0.0, Contrast.luminance(black))
        assertEquals(1.0, Contrast.luminance(white), 1e-9)
        assertEquals(0.2126, Contrast.luminance(Rgb(0xFF0000)), 1e-9)
        assertEquals(0.7152, Contrast.luminance(Rgb(0x00FF00)), 1e-9)
        assertEquals(0.0722, Contrast.luminance(Rgb(0x0000FF)), 1e-9)
    }

    @Test
    @Verifies("NFR-A11Y-03")
    fun `ratio matches the WCAG reference values in either order`() {
        assertEquals(21.0, Contrast.ratio(white, black), 1e-9)
        assertEquals(21.0, Contrast.ratio(black, white), 1e-9)
        assertEquals(1.0, Contrast.ratio(white, white), 1e-9)
        // #777777 on white is the classic "just fails AA" gray.
        assertEquals(4.48, Contrast.ratio(Rgb(0x777777), white), 0.01)
        assertEquals(4.54, Contrast.ratio(Rgb(0x767676), white), 0.01)
    }

    @Test
    fun `composite blends the encoded channels`() {
        assertEquals(Rgb(0x808080), Contrast.composite(white, 0.5f, black))
        assertEquals(Rgb(0x804000), Contrast.composite(Rgb(0xFF8000), 0.5f, black))
    }

    @Test
    fun `composite at the alpha edges is the bottom or the top`() {
        val top = Rgb(0x123456)
        val bottom = Rgb(0xABCDEF)

        assertEquals(bottom, Contrast.composite(top, 0f, bottom))
        assertEquals(top, Contrast.composite(top, 1f, bottom))
    }

    @Test
    fun `composite rejects alpha outside 0 to 1`() {
        assertFailsWith<IllegalArgumentException> { Contrast.composite(white, 1.5f, black) }
        assertFailsWith<IllegalArgumentException> { Contrast.composite(white, -0.1f, black) }
    }
}
