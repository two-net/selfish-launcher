package io.github.twonet.selfish.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppliedWallpaper
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.WallpaperStatus
import io.github.twonet.selfish.domain.ports.WallpaperId
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import io.github.twonet.selfish.ui.folder.FolderTags
import io.github.twonet.selfish.ui.grid.AppGridTags
import io.github.twonet.selfish.ui.home.HomeTags
import io.github.twonet.selfish.ui.tokens.DarkColors
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.LightColors
import io.github.twonet.selfish.ui.tokens.Motion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The animations SRS §4.9 times (level I, DESIGN §8.3), stepped through
 * frame by frame on the test clock. How each one looks is checked by hand
 * (REVIEW-CHECKLIST.md); the release spring and the drags are in
 * `NavigationTest`, and the tokens themselves in `MotionTokensTest`.
 */
@RunWith(AndroidJUnit4::class)
class MotionTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val launcher = TestLauncher(rule)
    private val apps = (1..30).map { app("App %02d".format(it)) }

    private fun gridApp(label: String) = hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(AppGridTags.GRID))

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    @Verifies("FR-ANIM-04")
    fun aPressGlowsFromTheNextFrameInsideTheScrollingGridAndFadesAfterRelease() {
        launcher.show(launcher(apps, layer = Layer.AppGrid))
        val app = rule.onNode(gridApp("App 05"))
        rule.mainClock.autoAdvance = false
        val before = app.centerPixel()

        app.performTouchInput { down(center) }
        rule.mainClock.advanceTimeByFrame()
        val pressed = app.centerPixel()

        app.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(Motion.SHORT_MILLIS.toLong() + FRAME_MILLIS)
        val after = app.centerPixel()

        assertNotEquals(before, pressed, "Glows on the first frame, well within a clickable's tap timeout")
        assertEquals(before, after, "Gone a Short duration after release")
    }

    @Test
    @Verifies("FR-ANIM-03")
    fun returningToTheLauncherSettlesHomeFrom96Percent() {
        launcher.show(launcher(apps, home = apps.take(2)))
        val atRest = bounds(HomeTags.SCREEN)

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.mainClock.autoAdvance = false
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.mainClock.advanceTimeByFrame()
        val returning = bounds(HomeTags.SCREEN)
        rule.mainClock.advanceTimeBy(Motion.MEDIUM_MILLIS.toLong() + FRAME_MILLIS)
        val settled = bounds(HomeTags.SCREEN)

        val startScale = returning.width / atRest.width
        assertTrue(abs(startScale - Motion.HOME_RETURN_SCALE) < SCALE_TOLERANCE, "Starts at about 96%, was $startScale")
        assertEquals(atRest, settled, "Settled after the Medium duration")
    }

    @Test
    fun theFirstStartDoesNotSettle() {
        rule.mainClock.autoAdvance = false
        launcher.show(launcher(apps, home = apps.take(2)))
        rule.mainClock.advanceTimeByFrame()

        assertEquals(rule.onNodeWithTag(LauncherTags.ROOT).fetchSemanticsNode().boundsInRoot.width, bounds(HomeTags.SCREEN).width)
    }

    @Test
    @Verifies("FR-ANIM-11")
    fun anAppearanceChangeCrossfadesEveryColorOverTheLongDurationWithoutReloading() {
        var dark by mutableStateOf(false)
        var shown = Color.Unspecified
        val kept = mutableListOf<Any>()
        rule.setContent {
            LauncherTheme(dark = dark) {
                kept += remember { Any() }
                shown = LauncherTheme.colors.textPrimary
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false

        dark = true
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(Motion.LONG_MILLIS / 2L)
        val halfway = shown
        rule.mainClock.advanceTimeBy(Motion.LONG_MILLIS / 2L + FRAME_MILLIS)

        assertTrue(halfway.red > LightColors.textPrimary.red && halfway.red < DarkColors.textPrimary.red, "Between the two: $halfway")
        assertEquals(DarkColors.textPrimary, shown)
        assertSame(kept.first(), kept.last(), "Nothing was reloaded")
    }

    @Test
    @Verifies("FR-ANIM-10")
    fun aNewHighlightMovesThereOverTheAmbientDuration() {
        var highlight by mutableStateOf(LightColors.highlightDefault)
        var shown = Color.Unspecified
        rule.setContent {
            LauncherTheme(highlight = highlight) { shown = LauncherTheme.highlight }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false

        highlight = DarkColors.highlightDefault
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(Motion.LONG_MILLIS.toLong())
        val afterLong = shown
        rule.mainClock.advanceTimeBy((Motion.AMBIENT_MILLIS - Motion.LONG_MILLIS).toLong() + FRAME_MILLIS)

        assertNotEquals(DarkColors.highlightDefault, afterLong, "Still moving after the Long duration")
        assertEquals(DarkColors.highlightDefault, shown)
    }

    @Test
    @Verifies("FR-ANIM-15", "NFR-A11Y-05")
    fun underReducedMotionAnAppearanceChangeTakesAtMost100Ms() {
        var dark by mutableStateOf(false)
        var shown = Color.Unspecified
        rule.setContent {
            LauncherTheme(dark = dark, reducedMotion = true) { shown = LauncherTheme.colors.textPrimary }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false

        dark = true
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(Motion.REDUCED_MILLIS.toLong())

        assertEquals(DarkColors.textPrimary, shown)
    }

    @Test
    @Verifies("FR-ANIM-15", "FR-ANIM-07")
    fun underReducedMotionAFolderAppearsInPlaceInsteadOfGrowingFromItsTile() {
        val tools = GridItem.Folder(FolderId(1), "Tools", listOf(apps[0].id, apps[1].id))
        val state = launcher(apps, layer = Layer.AppGrid).let { it.copy(layout = it.layout!!.copy(grid = listOf(tools) + it.layout!!.grid.drop(2))) }
        launcher.show(state.copy(systemAnimationsOff = true))
        rule.mainClock.autoAdvance = false

        rule.onNode(folderTile("Tools", 2)).performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(Motion.REDUCED_MILLIS / 2L)
        val opening = bounds(FolderTags.PANEL)
        rule.mainClock.advanceTimeBy(Motion.REDUCED_MILLIS.toLong())

        assertEquals(bounds(FolderTags.PANEL), opening, "Halfway through its fade, already its full size and place")
    }

    private fun folderTile(name: String, count: Int): SemanticsMatcher {
        val description = rule.activity.resources.getQuantityString(R.plurals.folder_description, count, name, count)
        return hasContentDescription(description) and hasAnyAncestor(hasTestTag(AppGridTags.GRID))
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.centerPixel(): Color {
        val pixels = captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    @Test
    @Verifies("FR-ANIM-10")
    fun aNewWallpaperIsAppliedOnlyOnceTheFingerLifts() {
        val today = TestLauncher.wallpaper("20261113", "Today", "© Today")
        val yesterday = TestLauncher.wallpaper("20261112", "Yesterday", "© Yesterday", date = LocalDate.of(2026, 11, 12))
        launcher.show(
            launcher(apps).copy(
                wallpapers = listOf(today, yesterday),
                wallpaper = yesterday,
                wallpaperStatus = WallpaperStatus(applied = AppliedWallpaper(yesterday.image, WallpaperId(1))),
            ),
        )
        val root = rule.onNodeWithTag(LauncherTags.ROOT)

        root.performTouchInput { down(center) }
        rule.waitForIdle()
        // A new image arrives in the middle of a gesture.
        launcher.state = launcher.state.copy(wallpaper = today)
        rule.waitForIdle()
        assertFalse(Action.ApplyWallpaperNow in launcher.dispatched)

        root.performTouchInput { up() }
        rule.waitForIdle()
        assertTrue(Action.ApplyWallpaperNow in launcher.dispatched)
        assertEquals(today.image, launcher.state.wallpaperWork.applying)
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val SCALE_TOLERANCE = 0.01f
    }
}
