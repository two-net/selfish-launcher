package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppliedWallpaper
import io.github.twonet.selfish.domain.model.DownloadOn
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.PixelSize
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.model.WallpaperIndex
import io.github.twonet.selfish.domain.model.WallpaperStatus
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.ScheduledRun
import io.github.twonet.selfish.domain.ports.WallpaperId
import io.github.twonet.selfish.domain.ports.WallpaperScreens
import io.github.twonet.selfish.domain.wallpaper.CheckResult
import io.github.twonet.selfish.domain.wallpaper.CheckTrigger
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** The reducer's Daily Bing wallpaper rules (DESIGN §4.3). */
@Verifies("AR-02")
class WallpaperReducerTest {
    private val day = LocalDate.of(2026, 11, 13)
    private val at = Instant.parse("2026-11-13T12:00:00Z")

    private fun entry(day: Int) = WallpaperEntry(
        image = ImageId("202611${"%02d".format(day)}_en-US"),
        title = "Day $day",
        copyright = "© Day $day",
        link = null,
        date = LocalDate.of(2026, 11, day),
        market = Market("en-US"),
        size = PixelSize(1080, 1920),
        bytes = 400_000,
    )

    private val today = entry(13)
    private val yesterday = entry(12)

    /** Restored, on screen, with nothing cached. */
    private val empty = LauncherState(restored = true, homeShown = true)

    /** Restored, on screen, with yesterday's image applied and in use. */
    private val cached = empty.copy(
        wallpapers = listOf(yesterday),
        wallpaper = yesterday,
        wallpaperStatus = WallpaperStatus(applied = AppliedWallpaper(yesterday.image, WallpaperId(1))),
    )

    private fun checking(state: LauncherState, trigger: CheckTrigger = CheckTrigger.HomeShown): LauncherState =
        state.copy(wallpaperWork = state.wallpaperWork.copy(checking = 1, trigger = trigger, checks = 1))

    private fun checked(result: CheckResult, run: ScheduledRun? = null) = Action.WallpaperChecked(1, result, at, day, run)

    // Restoring (FR-WP-07, FR-WP-12, FR-WP-13)

    @Test
    @Verifies("FR-WP-08")
    fun `restoring puts the pinned image in use, or else the newest, and schedules the job`() {
        val index = WallpaperIndex(listOf(today, yesterday), pinned = yesterday.image)

        val next = reduce(LauncherState(), Action.Restored(null, Settings(), index))

        assertEquals(listOf(today, yesterday), next.state.wallpapers)
        assertEquals(yesterday.image, next.state.pinned)
        assertEquals(yesterday, next.state.wallpaper)
        assertEquals(Effect.ScheduleWallpaperJob(wifiOnly = false), next.effects.first())
        assertEquals(today, reduce(LauncherState(), Action.Restored(null, Settings(), index.copy(pinned = null))).state.wallpaper)
    }

    @Test
    @Verifies("FR-WP-01")
    fun `an image downloaded but never applied is applied at start, while the launcher is hidden`() {
        val index = WallpaperIndex(listOf(today, yesterday), status = WallpaperStatus(applied = AppliedWallpaper(yesterday.image, WallpaperId(1))))

        val next = reduce(LauncherState(), Action.Restored(null, Settings(), index))

        assertEquals(today.image, next.state.wallpaperWork.applying)
        assertTrue(Effect.ApplyWallpaper(today, WallpaperScreens.Home) in next.effects)
    }

    @Test
    @Verifies("FR-WP-13")
    fun `with Daily off, restoring cancels the job and deletes a cache left over`() {
        val off = Settings(dailyWallpaper = false)

        val leftOver = reduce(LauncherState(), Action.Restored(null, off, WallpaperIndex(listOf(today))))
        assertEquals(listOf(Effect.CancelWallpaperJob, Effect.ClearWallpaperCache), leftOver.effects)
        assertEquals(emptyList(), leftOver.state.wallpapers)
        assertNull(leftOver.state.wallpaper)

        assertEquals(listOf<Effect>(Effect.CancelWallpaperJob), reduce(LauncherState(), Action.Restored(null, off)).effects)
    }

    @Test
    fun `restoring while on screen checks at once`() {
        val next = reduce(LauncherState(homeShown = true), Action.Restored(null, Settings()))

        assertEquals(1, next.state.wallpaperWork.checking)
        assertTrue(next.effects.any { it is Effect.CheckWallpaper && it.trigger == CheckTrigger.HomeShown })
    }

    // Checking (FR-WP-02)

    @Test
    @Verifies("FR-WP-02")
    fun `showing Home and the background job start a check, one at a time`() {
        val shown = reduce(empty.copy(homeShown = false), Action.HomeShown)
        val check = shown.effects.filterIsInstance<Effect.CheckWallpaper>().single()
        assertEquals(CheckTrigger.HomeShown, check.trigger)
        assertEquals(1, shown.state.wallpaperWork.checking)

        val run = ScheduledRun { }
        // A run arriving during a check ends at once; the check covers it.
        assertEquals(listOf<Effect>(Effect.FinishRun(run)), reduce(shown.state, Action.ScheduledRunDue(run)).effects)

        val scheduled = reduce(empty, Action.ScheduledRunDue(run)).effects.filterIsInstance<Effect.CheckWallpaper>().single()
        assertEquals(CheckTrigger.Scheduled, scheduled.trigger)
        assertSame(run, scheduled.run)
    }

    @Test
    @Verifies("FR-WP-06")
    fun `a check carries the market and network settings`() {
        val state = empty.copy(settings = Settings(market = Market("de-DE"), downloadOn = DownloadOn.WifiOnly), wallpapers = listOf(today))

        val check = reduce(state, Action.RefreshWallpaper).effects.filterIsInstance<Effect.CheckWallpaper>().single()

        assertEquals(Effect.CheckWallpaper(1, CheckTrigger.User, Market("de-DE"), true, listOf(today), WallpaperStatus()), check)
    }

    @Test
    @Verifies("FR-WP-13")
    fun `nothing is checked with Daily off, and the job's run ends at once`() {
        val off = empty.copy(settings = Settings(dailyWallpaper = false))
        val run = ScheduledRun { }

        assertSame(off, reduce(off, Action.RefreshWallpaper).state)
        assertTrue(reduce(off.copy(homeShown = false), Action.HomeShown).effects.none { it is Effect.CheckWallpaper })
        assertEquals(listOf<Effect>(Effect.FinishRun(run)), reduce(off, Action.ScheduledRunDue(run)).effects)
    }

    @Test
    @Verifies("FR-WP-01", "FR-WP-07")
    fun `a new image joins the cache and is put in use`() {
        val next = reduce(checking(cached), checked(CheckResult.Checked(listOf(today), succeeded = true)))

        assertEquals(listOf(today, yesterday), next.state.wallpapers)
        assertEquals(today, next.state.wallpaper)
        assertEquals(at, next.state.wallpaperStatus.lastSuccess)
        assertNull(next.state.wallpaperWork.checking)
        assertEquals(today, next.state.wallpaperDue)
    }

    @Test
    @Verifies("FR-WP-08")
    fun `a new image doesn't replace a pinned one`() {
        val pinned = checking(cached.copy(pinned = yesterday.image))

        val next = reduce(pinned, checked(CheckResult.Checked(listOf(today), succeeded = true)))

        assertEquals(listOf(today, yesterday), next.state.wallpapers)
        assertEquals(yesterday, next.state.wallpaper)
        assertNull(next.state.wallpaperDue)
    }

    @Test
    @Verifies("FR-WP-11")
    fun `a failed check keeps the wallpaper and schedules a retry`() {
        val run = ScheduledRun { }

        val next = reduce(checking(cached), checked(CheckResult.Checked(emptyList(), succeeded = false), run))

        assertEquals(yesterday, next.state.wallpaper)
        assertEquals(at, next.state.wallpaperStatus.lastCheck)
        assertNull(next.state.wallpaperStatus.lastSuccess)
        assertEquals(listOf(Effect.FinishRun(run), Effect.ScheduleWallpaperRetry(15.minutes, wifiOnly = false)), next.effects)
    }

    @Test
    fun `a skipped check changes nothing but ends its run`() {
        val run = ScheduledRun { }
        val state = checking(cached)

        val next = reduce(state, checked(CheckResult.Skipped, run))

        assertEquals(cached.wallpaperStatus, next.state.wallpaperStatus)
        assertEquals(listOf<Effect>(Effect.FinishRun(run)), next.effects)
    }

    @Test
    @Verifies("FR-WP-07")
    fun `images that leave the cache are deleted`() {
        val nine = (12 downTo 4).map { entry(it) }
        val state = checking(empty.copy(wallpapers = nine, wallpaper = nine.first()))

        val next = reduce(state, checked(CheckResult.Checked(listOf(today), succeeded = true)))

        assertEquals(8, next.state.wallpapers.size)
        assertTrue(Effect.DeleteWallpaperImages(setOf(entry(5).image, entry(4).image)) in next.effects)
    }

    @Test
    fun `a stale result is ignored, and what it stored is deleted`() {
        val next = reduce(cached, checked(CheckResult.Checked(listOf(today), succeeded = true)))

        assertEquals(cached, next.state)
        assertEquals(listOf<Effect>(Effect.DeleteWallpaperImages(setOf(today.image))), next.effects)
    }

    @Test
    @Verifies("FR-WP-09")
    fun `Refresh now shows it is checking, and one asked for during another check runs after it`() {
        val refreshing = reduce(cached, Action.RefreshWallpaper).state
        assertTrue(refreshing.wallpaperWork.refreshing)
        assertTrue(!reduce(refreshing, checked(CheckResult.Skipped)).state.wallpaperWork.refreshing)

        val queued = reduce(checking(cached), Action.RefreshWallpaper)
        assertEquals(emptyList(), queued.effects)
        val after = reduce(queued.state, checked(CheckResult.Skipped))
        val rerun = after.effects.filterIsInstance<Effect.CheckWallpaper>().single()
        assertEquals(CheckTrigger.User, rerun.trigger)
        assertTrue(after.state.wallpaperWork.refreshing)
    }

    // Applying (FR-WP-01, FR-WP-05, FR-ANIM-10)

    @Test
    @Verifies("FR-ANIM-10")
    fun `while Home is shown, a new image waits until Presentation is ready, never mid-gesture`() {
        val due = reduce(checking(cached), checked(CheckResult.Checked(listOf(today), succeeded = true)))
        assertTrue(due.effects.none { it is Effect.ApplyWallpaper })

        val applying = reduce(due.state, Action.ApplyWallpaperNow)
        assertEquals(listOf<Effect>(Effect.ApplyWallpaper(today, WallpaperScreens.Home)), applying.effects)
        assertEquals(today.image, applying.state.wallpaperWork.applying)
        assertNull(applying.state.wallpaperDue)
        // Only one at a time.
        assertEquals(emptyList(), reduce(applying.state, Action.ApplyWallpaperNow).effects)

        val applied = reduce(applying.state, Action.WallpaperApplied(today.image, WallpaperId(2))).state
        assertEquals(AppliedWallpaper(today.image, WallpaperId(2)), applied.wallpaperStatus.applied)
        assertNull(applied.wallpaperWork.applying)
        assertNull(applied.wallpaperDue)
    }

    @Test
    @Verifies("FR-WP-01")
    fun `while hidden, a new image is applied at once, and leaving applies one that was waiting`() {
        val hidden = checking(cached.copy(homeShown = false))
        val next = reduce(hidden, checked(CheckResult.Checked(listOf(today), succeeded = true)))
        assertTrue(Effect.ApplyWallpaper(today, WallpaperScreens.Home) in next.effects)

        val waiting = reduce(checking(cached), checked(CheckResult.Checked(listOf(today), succeeded = true))).state
        assertEquals(listOf<Effect>(Effect.ApplyWallpaper(today, WallpaperScreens.Home)), reduce(waiting, Action.HomeHidden).effects)
    }

    @Test
    @Verifies("FR-WP-05")
    fun `with Also use on lock screen, the image goes on both screens`() {
        val state = cached.copy(settings = Settings(lockScreen = true), wallpapers = listOf(today, yesterday), wallpaper = today)

        assertEquals(listOf<Effect>(Effect.ApplyWallpaper(today, WallpaperScreens.HomeAndLock)), reduce(state, Action.ApplyWallpaperNow).effects)
    }

    @Test
    @Verifies("FR-WP-05")
    fun `turning on Also use on lock screen puts the current image on the lock screen`() {
        val next = reduce(cached, Action.SetWallpaperLockScreen(true))

        assertTrue(next.state.settings.lockScreen)
        assertEquals(listOf<Effect>(Effect.ApplyWallpaper(yesterday, WallpaperScreens.Lock)), next.effects)
        assertEquals(emptyList(), reduce(next.state, Action.SetWallpaperLockScreen(false)).effects)
        assertSame(next.state, reduce(next.state, Action.SetWallpaperLockScreen(true)).state)
    }

    @Test
    @Verifies("FR-WP-11")
    fun `an image the system refuses keeps the current one and isn't tried again until chosen`() {
        val applying = reduce(cached.copy(wallpapers = listOf(today, yesterday), wallpaper = today), Action.ApplyWallpaperNow).state

        val failed = reduce(applying, Action.WallpaperApplyFailed(today.image)).state

        assertEquals(yesterday.image, failed.wallpaperStatus.applied?.image)
        assertNull(failed.wallpaperDue)
        assertEquals(emptyList(), reduce(failed, Action.ApplyWallpaperNow).effects)
        // Choosing it again tries again.
        assertEquals(today, reduce(failed, Action.PinWallpaper(today.image)).state.wallpaperDue)
    }

    @Test
    @Verifies("FR-WP-08")
    fun `pinning and Daily put their image in use, applied once Presentation is ready`() {
        val both = cached.copy(wallpapers = listOf(today, yesterday), wallpaper = yesterday, pinned = yesterday.image)

        val daily = reduce(both, Action.ResumeDaily).state
        assertEquals(today, daily.wallpaperDue)

        val hidden = reduce(both.copy(homeShown = false), Action.ResumeDaily)
        assertEquals(listOf<Effect>(Effect.ApplyWallpaper(today, WallpaperScreens.Home)), hidden.effects)
    }

    @Test
    @Verifies("FR-WP-12")
    fun `nothing is applied until the first image is cached`() {
        assertNull(empty.wallpaperDue)
        assertEquals(emptyList(), reduce(empty, Action.ApplyWallpaperNow).effects)
        assertEquals(emptyList(), reduce(empty.copy(homeShown = true), Action.HomeHidden).effects)
    }

    // Settings (FR-WP-03, FR-WP-06, FR-WP-13)

    @Test
    @Verifies("FR-WP-13")
    fun `turning Daily off stops checks, deletes the cache and leaves the system wallpaper alone`() {
        val state = checking(cached.copy(layer = Layer.WallpaperDetails, pinned = yesterday.image))

        val next = reduce(state, Action.SetDailyWallpaper(false))

        // Nothing applied is remembered now, so the system is asked what it
        // shows, for the highlight (FR-AMB-03).
        assertEquals(listOf(Effect.CancelWallpaperJob, Effect.ClearWallpaperCache, Effect.ReadSystemWallpaper(1)), next.effects)
        assertEquals(emptyList(), next.state.wallpapers)
        assertNull(next.state.wallpaper)
        assertNull(next.state.pinned)
        assertEquals(WallpaperStatus(), next.state.wallpaperStatus)
        assertTrue(!next.state.showsAmbiences)
        assertEquals(Layer.Home, next.state.layer)
        // The check that was running is stale now.
        val late = reduce(next.state, checked(CheckResult.Checked(listOf(today), succeeded = true)))
        assertEquals(next.state, late.state)
    }

    @Test
    @Verifies("FR-WP-02")
    fun `turning Daily on schedules the job and checks at once`() {
        val off = empty.copy(settings = Settings(dailyWallpaper = false))

        val next = reduce(off, Action.SetDailyWallpaper(true))

        assertEquals(Effect.ScheduleWallpaperJob(wifiOnly = false), next.effects.first())
        assertEquals(CheckTrigger.User, next.effects.filterIsInstance<Effect.CheckWallpaper>().single().trigger)
    }

    @Test
    @Verifies("FR-WP-03")
    fun `a new market is checked at once`() {
        val next = reduce(cached, Action.SetMarket(Market("fr-FR")))

        assertEquals(Market("fr-FR"), next.state.settings.market)
        assertEquals(Market("fr-FR"), next.effects.filterIsInstance<Effect.CheckWallpaper>().single().market)
        assertSame(next.state, reduce(next.state, Action.SetMarket(Market("fr-FR"))).state)
    }

    @Test
    @Verifies("FR-WP-06")
    fun `Wi-Fi only changes the job's network condition`() {
        val next = reduce(cached, Action.SetDownloadOn(DownloadOn.WifiOnly))

        assertEquals(DownloadOn.WifiOnly, next.state.settings.downloadOn)
        assertEquals(listOf<Effect>(Effect.ScheduleWallpaperJob(wifiOnly = true)), next.effects)
    }
}
