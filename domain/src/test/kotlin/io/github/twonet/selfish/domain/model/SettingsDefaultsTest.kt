package io.github.twonet.selfish.domain.model

import io.github.twonet.selfish.domain.Verifies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The defaults of SRS §4.13. */
class SettingsDefaultsTest {
    private val defaults = Settings()

    @Test
    @Verifies(
        "FR-SET-01", "FR-SET-02", "FR-SET-03", "FR-SET-04", "FR-SET-05", "FR-SET-07", "FR-SET-08",
        "FR-SET-09", "FR-SET-11", "FR-SET-12", "FR-SET-13", "FR-SET-14", "FR-SET-15", "FR-THM-02",
    )
    fun `every setting starts at its SRS default`() {
        assertEquals(AppearanceMode.System, defaults.appearance)
        assertEquals(true, defaults.dailyWallpaper)
        assertEquals(null, defaults.market, "Automatic")
        assertEquals(false, defaults.lockScreen)
        assertEquals(DownloadOn.AnyNetwork, defaults.downloadOn)
        assertEquals(true, defaults.showClock)
        assertEquals(CoversShow.Favorites, defaults.coversShow)
        assertEquals(4, defaults.gridColumns)
        assertEquals(SwipeDownOpens.TopMenu, defaults.swipeDownOpens)
        assertEquals(false, defaults.notificationsInEvents)
        assertEquals(false, defaults.notificationDots)
        assertEquals(Haptics.FollowSystem, defaults.haptics)
        assertEquals(Animations.FollowSystem, defaults.animations)
    }

    @Test
    @Verifies("FR-SET-09")
    fun `the App Grid has 3 to 5 columns`() {
        for (columns in 3..5) Settings(gridColumns = columns)
        for (columns in listOf(2, 6)) assertFailsWith<IllegalArgumentException> { Settings(gridColumns = columns) }
    }
}
