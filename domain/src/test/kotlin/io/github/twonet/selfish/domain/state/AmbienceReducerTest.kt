package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppliedWallpaper
import io.github.twonet.selfish.domain.model.HighlightSetting
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.PixelSize
import io.github.twonet.selfish.domain.model.Rgb
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.model.WallpaperStatus
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.WallpaperId
import io.github.twonet.selfish.domain.theme.Highlight
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The reducer's ambience rules and the highlight derived from state (SRS §4.11, DESIGN §4.4). */
@Verifies("AR-02")
class AmbienceReducerTest {
    private val orange = Rgb(0xC8641E)
    private val green = Rgb(0x2E8B57)
    private val purple = Rgb(0x7B3FC4)

    private fun entry(day: Int, highlight: Rgb?) = WallpaperEntry(
        image = ImageId("202611${"%02d".format(day)}_en-US"),
        title = "Day $day",
        copyright = "© Day $day",
        link = null,
        date = LocalDate.of(2026, 11, day),
        market = Market("en-US"),
        size = PixelSize(1080, 1920),
        bytes = 400_000,
        highlight = highlight,
    )

    private val today = entry(13, green)
    private val yesterday = entry(12, orange)

    /** Restored and on screen, with yesterday's image applied as ID 1 and today's cached and due. */
    private val state = LauncherState(
        restored = true,
        homeShown = true,
        wallpapers = listOf(today, yesterday),
        wallpaper = today,
        wallpaperStatus = WallpaperStatus(applied = AppliedWallpaper(yesterday.image, WallpaperId(1))),
    )

    /** [state] once the system answers its latest read with [id] and [colors]. */
    private fun read(state: LauncherState, id: Int?, colors: List<Rgb> = emptyList()): LauncherState =
        reduce(state, Action.SystemWallpaperRead(state.systemWallpaperReads, id?.let(::WallpaperId), colors)).state

    @Test
    @Verifies("FR-AMB-01", "FR-AMB-02", "FR-ANIM-10", "AC-03")
    fun `the highlight comes from the applied image, and moves when the next one is applied`() {
        assertEquals(today, state.wallpaperDue)
        assertEquals(Highlight.of(orange), state.highlight, "Still the applied image's while the new one is due")

        val applied = reduce(state, Action.WallpaperApplied(today.image, WallpaperId(2))).state

        assertEquals(Highlight.of(green), applied.highlight)
    }

    @Test
    @Verifies("FR-AMB-01")
    fun `an image without a prominent color gets the default`() {
        val gray = state.copy(wallpapers = listOf(today, yesterday.copy(highlight = null)))

        assertNull(read(gray, 1, listOf(purple)).highlight, "Not the system's colors: they come from the same image")
    }

    @Test
    @Verifies("FR-AMB-03")
    fun `coming on screen asks the system which wallpaper it shows`() {
        val hidden = state.copy(homeShown = false)

        val next = reduce(hidden, Action.HomeShown)

        assertEquals(1, next.state.systemWallpaperReads)
        assertTrue(Effect.ReadSystemWallpaper(1) in next.effects, "${next.effects}")
    }

    @Test
    @Verifies("FR-AMB-03")
    fun `a wallpaper set elsewhere gives the highlight from the system's colors`() {
        val asked = reduce(state, Action.HomeShown).state

        assertEquals(Highlight.of(orange), read(asked, 1, listOf(purple)).highlight, "Still the launcher's image")
        val elsewhere = read(asked, 7, listOf(Rgb(0x808080), purple))
        assertEquals(Highlight.of(purple), elsewhere.highlight)
        assertNull(elsewhere.launcherWallpaper)
        assertNull(read(asked, 7, listOf(Rgb(0x808080))).highlight, "No colorful color reported: the default")
    }

    @Test
    @Verifies("FR-AMB-03")
    fun `until the system answers, the launcher's own image is assumed`() {
        assertNull(state.systemWallpaper)
        assertEquals(yesterday, state.launcherWallpaper)
        assertEquals(yesterday, read(reduce(state, Action.HomeShown).state, null).launcherWallpaper, "No ID reported")
    }

    @Test
    @Verifies("FR-AMB-03")
    fun `without anything applied by the launcher the system's colors are used`() {
        val nothingApplied = LauncherState(restored = true)

        assertNull(nothingApplied.highlight)
        assertEquals(Highlight.of(purple), read(reduce(nothingApplied, Action.HomeShown).state, 3, listOf(purple)).highlight)
    }

    @Test
    @Verifies("FR-AMB-03")
    fun `a read overtaken by the launcher applying an image is ignored`() {
        val asked = reduce(state, Action.HomeShown).state
        val applied = reduce(asked, Action.WallpaperApplied(today.image, WallpaperId(2))).state

        // The read reports the system's old wallpaper, from before the apply.
        val late = reduce(applied, Action.SystemWallpaperRead(asked.systemWallpaperReads, WallpaperId(1), listOf(purple)))

        assertSame(applied, late.state)
        assertEquals(WallpaperId(2), late.state.systemWallpaper?.id)
        assertEquals(Highlight.of(green), late.state.highlight)
    }

    @Test
    @Verifies("FR-AMB-03", "FR-WP-13")
    fun `turning Daily off takes the highlight from the system's colors for the image left in place`() {
        val off = reduce(state, Action.SetDailyWallpaper(false))

        assertEquals(listOf(Effect.ReadSystemWallpaper(1)), off.effects.filterIsInstance<Effect.ReadSystemWallpaper>())
        assertNull(off.state.launcherWallpaper)
        assertEquals(Highlight.of(purple), read(off.state, 1, listOf(purple)).highlight)
    }

    @Test
    @Verifies("FR-AMB-03")
    fun `an image applied after Daily was turned off asks the system again`() {
        val off = reduce(state.copy(wallpaperWork = WallpaperWork(applying = today.image)), Action.SetDailyWallpaper(false)).state

        val applied = reduce(off, Action.WallpaperApplied(today.image, WallpaperId(2)))

        assertEquals(listOf(Effect.ReadSystemWallpaper(2)), applied.effects)
        assertNull(applied.state.wallpaperStatus.applied)
    }

    @Test
    @Verifies("FR-AMB-05")
    fun `a preset replaces the automatic highlight, and Automatic brings it back`() {
        val preset = reduce(state, Action.SetHighlight(HighlightSetting.Teal)).state

        assertEquals(HighlightSetting.Teal, preset.settings.highlight, "Saved with the settings")
        assertEquals(Highlight.of(HighlightSetting.Teal.color!!), preset.highlight)
        assertEquals(Highlight.of(HighlightSetting.Teal.color!!), read(preset, 7, listOf(purple)).highlight, "Also over a wallpaper set elsewhere")
        assertEquals(Highlight.of(HighlightSetting.Teal.color!!), preset.copy(wallpaperStatus = WallpaperStatus()).highlight, "And with no wallpaper")

        val automatic = reduce(preset, Action.SetHighlight(HighlightSetting.Automatic)).state

        assertEquals(Highlight.of(orange), automatic.highlight)
        assertSame(automatic, reduce(automatic, Action.SetHighlight(HighlightSetting.Automatic)).state)
    }
}
