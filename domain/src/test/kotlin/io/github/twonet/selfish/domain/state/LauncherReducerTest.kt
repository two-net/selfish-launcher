package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.AppearanceMode
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.DefaultApps
import io.github.twonet.selfish.domain.ports.ScheduledRun
import io.github.twonet.selfish.domain.remorse.Destructive
import io.github.twonet.selfish.domain.remorse.PendingRemorse
import io.github.twonet.selfish.domain.remorse.REMORSE_DELAY
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Verifies("AR-02")
class LauncherReducerTest {
    private fun app(label: String) = AppEntry(AppId("pkg.$label", "Main", 0), label)

    @Test
    fun `catalog changes replace the apps in alphabetical order`() {
        val next = reduce(LauncherState(), Action.CatalogChanged(listOf(app("Maps"), app("calendar"), app("Clock"))))

        assertEquals(listOf("calendar", "Clock", "Maps"), next.state.apps.map { it.label })
        assertEquals(emptyList(), next.effects)
    }

    @Test
    @Verifies("FR-GEN-05")
    fun `a launch starts the app and a timeout`() {
        val state = LauncherState(apps = listOf(app("Maps")))
        val bounds = Bounds(1, 2, 3, 4)

        val next = reduce(state, Action.Launch(app("Maps").id, bounds))

        assertEquals(state.copy(launching = true, launches = 1), next.state)
        assertEquals(
            listOf(Effect.LaunchApp(app("Maps").id, bounds), Effect.DispatchAfter(LAUNCH_TIMEOUT, Action.LaunchTimedOut(1))),
            next.effects,
        )
    }

    @Test
    @Verifies("FR-GEN-05")
    fun `taps while an app is launching launch nothing`() {
        val launching = reduce(LauncherState(), Action.Launch(app("Maps").id, null)).state

        val again = reduce(launching, Action.Launch(app("Maps").id, null))
        val other = reduce(launching, Action.Launch(app("Clock").id, null))

        assertSame(launching, again.state)
        assertEquals(emptyList(), again.effects)
        assertSame(launching, other.state)
        assertEquals(emptyList(), other.effects)
    }

    @Test
    @Verifies("FR-GEN-05")
    fun `hiding the launcher ends the launch`() {
        val launching = reduce(LauncherState(homeShown = true), Action.Launch(app("Maps").id, null)).state

        val hidden = reduce(launching, Action.HomeHidden).state

        assertFalse(hidden.launching)
        assertEquals(1, reduce(hidden, Action.Launch(app("Maps").id, null)).effects.filterIsInstance<Effect.LaunchApp>().size)
    }

    @Test
    @Verifies("FR-GEN-05")
    fun `the timeout ends the launch`() {
        val launching = reduce(LauncherState(), Action.Launch(app("Maps").id, null)).state

        assertFalse(reduce(launching, Action.LaunchTimedOut(1)).state.launching)
    }

    @Test
    @Verifies("FR-GEN-05")
    fun `a stale timeout does not end a newer launch`() {
        var state = reduce(LauncherState(), Action.Launch(app("Maps").id, null)).state
        state = reduce(state, Action.HomeHidden).state
        state = reduce(state, Action.Launch(app("Clock").id, null)).state

        val next = reduce(state, Action.LaunchTimedOut(1))

        assertSame(state, next.state)
        assertTrue(next.state.launching)
    }

    @Test
    fun `a scheduled run is finished`() {
        val run = ScheduledRun { }

        val next = reduce(LauncherState(), Action.ScheduledRunDue(run))

        assertEquals(listOf<Effect>(Effect.FinishRun(run)), next.effects)
    }

    @Test
    fun `restoring sets the saved layout and settings`() {
        val layout = Layout(grid = listOf(GridItem.App(app("Maps").id)), home = listOf(app("Maps").id))
        val settings = Settings(appearance = AppearanceMode.Dark)

        val next = reduce(LauncherState(apps = listOf(app("Maps"))), Action.Restored(layout, settings))

        assertEquals(LauncherState(apps = listOf(app("Maps")), layout = layout, settings = settings, restored = true), next.state)
        assertEquals(emptyList(), next.effects)
    }

    @Test
    fun `restoring without a saved layout leaves it for the first run`() {
        val next = reduce(LauncherState(), Action.Restored(null, Settings()))

        assertEquals(null, next.state.layout)
        assertEquals(true, next.state.restored)
        assertEquals(true, next.state.firstRun)
    }

    // Navigation (FR-GEN-03, FR-GEN-04)

    private val folder = Overlay.Folder(FolderId(1))
    private val otherFolder = Overlay.Folder(FolderId(2))

    @Test
    @Verifies("FR-GEN-04")
    fun `back closes the topmost overlay, then the layer`() {
        var state = LauncherState(layer = Layer.AppGrid, overlays = listOf(folder, otherFolder))

        state = reduce(state, Action.Back).state
        assertEquals(LauncherState(layer = Layer.AppGrid, overlays = listOf(folder)), state)

        state = reduce(state, Action.Back).state
        assertEquals(LauncherState(layer = Layer.AppGrid), state)

        state = reduce(state, Action.Back).state
        assertEquals(LauncherState(layer = Layer.Home), state)
    }

    @Test
    @Verifies("FR-GEN-04")
    fun `back closes every layer to Home`() {
        for (layer in listOf(Layer.AppGrid, Layer.Events, Layer.TopMenu)) {
            assertEquals(Layer.Home, reduce(LauncherState(layer = layer), Action.Back).state.layer)
        }
    }

    @Test
    @Verifies("FR-GEN-04")
    fun `back on Home does nothing`() {
        val home = LauncherState(apps = listOf(app("Maps")))

        val next = reduce(home, Action.Back)

        assertSame(home, next.state)
        assertEquals(emptyList(), next.effects)
    }

    @Test
    @Verifies("FR-GEN-03")
    fun `home closes every overlay and layer at once`() {
        val state = LauncherState(layer = Layer.AppGrid, overlays = listOf(folder, otherFolder))

        val next = reduce(state, Action.HomePressed)

        assertEquals(LauncherState(), next.state)
        assertEquals(emptyList(), next.effects)
    }

    @Test
    @Verifies("FR-GEN-03")
    fun `home on Home does nothing`() {
        val home = LauncherState(apps = listOf(app("Maps")))

        val next = reduce(home, Action.HomePressed)

        assertSame(home, next.state)
        assertEquals(emptyList(), next.effects)
    }

    // Default Home role (FR-GEN-02)

    private fun offers(next: Next) = next.effects.count { it == Effect.OfferDefaultHome }

    @Test
    @Verifies("FR-GEN-02")
    fun `a first run offers the default Home role once the launcher is shown`() {
        val restored = reduce(LauncherState(), Action.Restored(null, Settings()))
        assertEquals(0, offers(restored))

        val shown = reduce(restored.state, Action.HomeShown)

        assertEquals(1, offers(shown))
        assertTrue(shown.state.defaultHomeOffered)
    }

    @Test
    @Verifies("FR-GEN-02")
    fun `a first run shown before the restore offers when it is restored`() {
        val shown = reduce(LauncherState(), Action.HomeShown)
        assertEquals(0, offers(shown))

        assertEquals(1, offers(reduce(shown.state, Action.Restored(null, Settings()))))
    }

    @Test
    @Verifies("FR-GEN-02")
    fun `the offer is made once per process`() {
        var state = reduce(reduce(LauncherState(), Action.Restored(null, Settings())).state, Action.HomeShown).state
        state = reduce(state, Action.HomeHidden).state

        assertEquals(0, offers(reduce(state, Action.HomeShown)))
    }

    @Test
    @Verifies("FR-GEN-02")
    fun `with a saved layout there is no offer`() {
        val restored = reduce(LauncherState(), Action.Restored(Layout(), Settings())).state

        assertEquals(0, offers(reduce(restored, Action.HomeShown)))
    }

    // Catalog (FR-GEN-06)

    @Test
    @Verifies("FR-GEN-06")
    fun `catalog changes reconcile the saved layout`() {
        val state = LauncherState(
            layout = Layout(grid = listOf(GridItem.App(app("Gone").id), GridItem.App(app("Maps").id)), home = listOf(app("Gone").id)),
            restored = true,
        )

        val next = reduce(state, Action.CatalogChanged(listOf(app("Maps"), app("Clock"))))

        assertEquals(Layout(grid = listOf(GridItem.App(app("Maps").id), GridItem.App(app("Clock").id))), next.state.layout)
    }

    // First run (FR-HOME-07)

    @Test
    @Verifies("FR-HOME-07")
    fun `catalog changes leave a missing layout for the first run, which looks up the default apps once`() {
        val next = reduce(LauncherState(restored = true, firstRun = true), Action.CatalogChanged(listOf(app("Maps"))))

        assertEquals(null, next.state.layout)
        assertTrue(next.state.resolvingDefaults)
        assertEquals(listOf<Effect>(Effect.ResolveDefaultApps), next.effects)
        assertEquals(emptyList(), reduce(next.state, Action.CatalogChanged(listOf(app("Maps"), app("Clock")))).effects)
    }

    @Test
    fun `the first run waits for the restore and for apps`() {
        assertEquals(emptyList(), reduce(LauncherState(), Action.CatalogChanged(listOf(app("Maps")))).effects)
        assertEquals(emptyList(), reduce(LauncherState(restored = true), Action.CatalogChanged(emptyList())).effects)
    }

    @Test
    @Verifies("FR-HOME-07", "FR-ONB-01")
    fun `the default apps build the first-run layout`() {
        val apps = listOf(app("Clock"), app("Maps"), app("Phone"))
        val state = LauncherState(apps = apps, restored = true, firstRun = true, resolvingDefaults = true)

        val next = reduce(state, Action.DefaultAppsResolved(DefaultApps(phone = app("Phone").id)))

        assertEquals(Layout(grid = apps.map { GridItem.App(it.id) }, home = listOf(app("Phone").id)), next.state.layout)
        assertFalse(next.state.resolvingDefaults)
    }

    @Test
    fun `default apps that arrive after a layout exists change nothing`() {
        val layout = Layout(grid = listOf(GridItem.App(app("Maps").id)))
        val state = LauncherState(apps = listOf(app("Maps")), layout = layout, restored = true, resolvingDefaults = true)

        val next = reduce(state, Action.DefaultAppsResolved(DefaultApps(phone = app("Maps").id)))

        assertEquals(layout, next.state.layout)
        assertFalse(next.state.resolvingDefaults)
    }

    // Home (FR-HOME-02, FR-HOME-05, FR-HOME-06)

    private val maps = app("Maps")
    private val clock = app("Clock")
    private val homeState = LauncherState(
        apps = listOf(clock, maps),
        layout = Layout(grid = listOf(GridItem.App(clock.id), GridItem.App(maps.id)), home = listOf(maps.id)),
        restored = true,
    )
    private val coverMenu = Overlay.ContextMenu(MenuTarget.Cover(maps.id))
    private val appMenu = Overlay.ContextMenu(MenuTarget.App(clock.id))

    @Test
    @Verifies("FR-HOME-03")
    fun `covers are Home's apps in order, once the catalog knows them`() {
        val state = homeState.copy(layout = homeState.layout!!.copy(home = listOf(app("Unknown").id, maps.id, clock.id)))

        assertEquals(listOf(maps, clock), state.covers)
        assertEquals(emptyList(), LauncherState().covers)
    }

    @Test
    @Verifies("FR-HOME-06")
    fun `a long-press opens a context menu, replacing any other`() {
        val opened = reduce(homeState.copy(overlays = listOf(appMenu)), Action.OpenContextMenu(MenuTarget.Cover(maps.id))).state

        assertEquals(listOf<Overlay>(coverMenu), opened.overlays)
    }

    @Test
    @Verifies("FR-GEN-03", "FR-GEN-04")
    fun `back, home and a tap outside close the context menu`() {
        val open = homeState.copy(overlays = listOf(coverMenu))

        assertEquals(homeState, reduce(open, Action.Back).state)
        assertEquals(homeState, reduce(open, Action.HomePressed).state)
        assertEquals(homeState, reduce(open, Action.CloseContextMenu).state)
        assertSame(homeState, reduce(homeState, Action.CloseContextMenu).state)
    }

    @Test
    fun `launching an app closes the context menu`() {
        val next = reduce(homeState.copy(overlays = listOf(Overlay.Folder(FolderId(1)), coverMenu)), Action.Launch(clock.id, null))

        assertEquals(listOf<Overlay>(Overlay.Folder(FolderId(1))), next.state.overlays)
    }

    @Test
    @Verifies("FR-HOME-05")
    fun `add to Home adds the cover and closes the menu`() {
        val next = reduce(homeState.copy(overlays = listOf(appMenu)), Action.AddToHome(clock.id)).state

        assertEquals(listOf(maps.id, clock.id), next.layout!!.home)
        assertEquals(emptyList(), next.overlays)
    }

    @Test
    @Verifies("FR-HOME-05")
    fun `add to a full Home changes nothing, so the menu can explain`() {
        val full = homeState.copy(
            layout = homeState.layout!!.copy(home = (1..9).map { app("x$it").id }),
            overlays = listOf(appMenu),
        )

        assertTrue(full.homeFull)
        assertSame(full, reduce(full, Action.AddToHome(clock.id)).state)
    }

    @Test
    @Verifies("FR-HOME-05")
    fun `remove from Home removes the cover and closes the menu`() {
        val next = reduce(homeState.copy(overlays = listOf(coverMenu)), Action.RemoveFromHome(maps.id)).state

        assertEquals(emptyList(), next.layout!!.home)
        assertEquals(homeState.layout!!.grid, next.layout!!.grid)
        assertEquals(emptyList(), next.overlays)
    }

    @Test
    @Verifies("FR-HOME-06")
    fun `dropping a cover moves it`() {
        val state = homeState.copy(layout = homeState.layout!!.copy(home = listOf(maps.id, clock.id)))

        val next = reduce(state, Action.MoveCover(clock.id, 0)).state

        assertEquals(listOf(clock.id, maps.id), next.layout!!.home)
    }

    @Test
    fun `edits before the first-run layout exists change nothing`() {
        val state = LauncherState(apps = listOf(maps), restored = true)

        assertSame(state, reduce(state, Action.AddToHome(maps.id)).state)
        assertSame(state, reduce(state, Action.RemoveFromHome(maps.id)).state)
        assertSame(state, reduce(state, Action.MoveCover(maps.id, 0)).state)
    }

    @Test
    @Verifies("FR-GEN-06")
    fun `uninstalling the app whose menu is open closes the menu`() {
        val notes = app("Notes")
        val open = homeState.copy(
            apps = listOf(clock, maps, notes),
            layout = Layout(grid = listOf(GridItem.Folder(FolderId(1), apps = listOf(clock.id, notes.id)), GridItem.App(maps.id)), home = listOf(maps.id)),
            overlays = listOf(Overlay.Folder(FolderId(1)), coverMenu),
        )

        val next = reduce(open, Action.CatalogChanged(listOf(clock, notes))).state

        assertEquals(listOf<Overlay>(Overlay.Folder(FolderId(1))), next.overlays)
        assertEquals(emptyList(), next.layout!!.home)
    }

    // App Grid (FR-GRID-01, FR-GRID-04 to FR-GRID-09)

    private val gridMenu = Overlay.ContextMenu(MenuTarget.App(maps.id))

    @Test
    @Verifies("FR-GRID-01", "FR-GRID-04")
    fun `a settled swipe moves to its layer and closes the menu of the one it left`() {
        val opened = reduce(homeState.copy(overlays = listOf(coverMenu)), Action.LayerSettled(Layer.AppGrid)).state

        assertEquals(homeState.copy(layer = Layer.AppGrid), opened)

        val closed = reduce(opened.copy(overlays = listOf(gridMenu)), Action.LayerSettled(Layer.Home)).state
        assertEquals(homeState, closed)

        val open = homeState.copy(layer = Layer.AppGrid, overlays = listOf(gridMenu))
        assertSame(open, reduce(open, Action.LayerSettled(Layer.AppGrid)).state, "Settling where it already is changes nothing")
    }

    @Test
    @Verifies("FR-GEN-03", "FR-GEN-04")
    fun `back closes the App Grid's menu, then the grid, and Home closes both at once`() {
        val open = homeState.copy(layer = Layer.AppGrid, overlays = listOf(gridMenu))

        val back = reduce(open, Action.Back).state
        assertEquals(homeState.copy(layer = Layer.AppGrid), back)
        assertEquals(homeState, reduce(back, Action.Back).state)
        assertEquals(homeState, reduce(open, Action.HomePressed).state)
    }

    @Test
    @Verifies("FR-GRID-05", "FR-GRID-07")
    fun `dropping a grid item moves it and closes the menu`() {
        val next = reduce(homeState.copy(layer = Layer.AppGrid, overlays = listOf(gridMenu)), Action.MoveGridItem(GridItem.App(clock.id), 1)).state

        assertEquals(listOf(GridItem.App(maps.id), GridItem.App(clock.id)), next.layout!!.grid)
        assertEquals(emptyList(), next.overlays)
    }

    @Test
    @Verifies("FR-GRID-08", "FR-FOLD-01")
    fun `dropping an app onto another makes a folder`() {
        val next = reduce(homeState.copy(layer = Layer.AppGrid), Action.DropOnto(clock.id, maps.id)).state

        assertEquals(listOf<GridItem>(GridItem.Folder(FolderId(1), apps = listOf(maps.id, clock.id))), next.layout!!.grid)
        assertEquals(
            listOf<GridCell>(GridCell.Folder(next.layout.grid.single() as GridItem.Folder, listOf(maps, clock))),
            next.gridCells,
        )
    }

    @Test
    @Verifies("FR-GRID-06")
    fun `app info and uninstall close the menu and ask the system`() {
        val open = homeState.copy(layer = Layer.AppGrid, overlays = listOf(gridMenu))

        val info = reduce(open, Action.OpenAppInfo(maps.id))
        assertEquals(homeState.copy(layer = Layer.AppGrid), info.state)
        assertEquals(listOf<Effect>(Effect.OpenAppInfo(maps.id)), info.effects)

        val uninstall = reduce(open, Action.Uninstall(maps.id))
        assertEquals(homeState.copy(layer = Layer.AppGrid), uninstall.state)
        assertEquals(listOf<Effect>(Effect.RequestUninstall(maps.id)), uninstall.effects)
    }

    @Test
    @Verifies("FR-GRID-02")
    fun `grid cells are the grid's items in order, once the catalog knows their apps`() {
        val unknown = app("Unknown").id
        val folder = GridItem.Folder(FolderId(3), "Tools", listOf(unknown, clock.id))
        val state = homeState.copy(
            layout = Layout(grid = listOf(GridItem.App(maps.id), GridItem.App(unknown), folder, GridItem.Folder(FolderId(4), apps = listOf(unknown)))),
        )

        assertEquals(listOf(GridCell.App(maps), GridCell.Folder(folder, listOf(clock))), state.gridCells)
        assertEquals(emptyList(), LauncherState().gridCells)
    }

    // Folders (FR-FOLD-03 to FR-FOLD-07, FR-GEST-06)

    private val notes = app("Notes")
    private val tools = GridItem.Folder(FolderId(1), "Tools", listOf(clock.id, notes.id))
    private val folderState = LauncherState(
        apps = listOf(clock, maps, notes),
        layout = Layout(grid = listOf(tools, GridItem.App(maps.id))),
        restored = true,
        layer = Layer.AppGrid,
    )
    private val openTools = Overlay.Folder(tools.id)
    private val menuInTools = Overlay.ContextMenu(MenuTarget.App(notes.id))

    @Test
    @Verifies("FR-FOLD-03")
    fun `a tap opens a folder over the App Grid, closing any menu`() {
        val opened = reduce(folderState.copy(overlays = listOf(gridMenu)), Action.OpenFolder(tools.id)).state

        assertEquals(listOf<Overlay>(openTools), opened.overlays)
        assertEquals(GridCell.Folder(tools, listOf(clock, notes)), opened.openFolder)
        assertSame(opened, reduce(opened, Action.OpenFolder(tools.id)).state, "Already open")

        val onHome = folderState.copy(layer = Layer.Home)
        assertSame(onHome, reduce(onHome, Action.OpenFolder(tools.id)).state, "Folders open only in the App Grid")
        assertSame(folderState, reduce(folderState, Action.OpenFolder(FolderId(9))).state, "No such folder")
    }

    @Test
    @Verifies("FR-FOLD-03", "FR-GEN-03", "FR-GEN-04")
    fun `back closes a menu in the folder, then the folder, and home or a tap outside closes both`() {
        val open = folderState.copy(overlays = listOf(openTools, menuInTools))

        val back = reduce(open, Action.Back).state
        assertEquals(listOf<Overlay>(openTools), back.overlays)
        assertEquals(folderState, reduce(back, Action.Back).state)
        assertEquals(folderState.copy(layer = Layer.Home), reduce(open, Action.HomePressed).state)
        assertEquals(folderState, reduce(open, Action.CloseFolder).state)
        assertSame(folderState, reduce(folderState, Action.CloseFolder).state)
    }

    @Test
    @Verifies("FR-FOLD-05", "FR-FOLD-06", "AC-08")
    fun `taking an app out of a two-app folder closes it and leaves the other app in its place`() {
        val open = folderState.copy(overlays = listOf(openTools))

        val next = reduce(open, Action.TakeOutOfFolder(notes.id, 2)).state

        assertEquals(listOf(GridItem.App(clock.id), GridItem.App(maps.id), GridItem.App(notes.id)), next.layout!!.grid)
        assertEquals(emptyList(), next.overlays)
        assertEquals(null, next.openFolder)
    }

    @Test
    @Verifies("FR-FOLD-06", "FR-GEN-06")
    fun `a folder dissolved by an uninstall closes`() {
        val open = folderState.copy(overlays = listOf(openTools, menuInTools))

        val next = reduce(open, Action.CatalogChanged(listOf(clock, maps))).state

        assertEquals(listOf(GridItem.App(clock.id), GridItem.App(maps.id)), next.layout!!.grid)
        assertEquals(emptyList(), next.overlays)
    }

    @Test
    @Verifies("FR-FOLD-04", "FR-FOLD-05")
    fun `apps are added to and moved within a folder, and folders are renamed`() {
        val added = reduce(folderState.copy(overlays = listOf(gridMenu)), Action.AddToFolder(maps.id, tools.id)).state
        assertEquals(listOf<GridItem>(tools.copy(apps = listOf(clock.id, notes.id, maps.id))), added.layout!!.grid)
        assertEquals(emptyList(), added.overlays, "Dragging closed the menu")

        val open = added.copy(overlays = listOf(openTools, menuInTools))
        val moved = reduce(open, Action.MoveInFolder(maps.id, 0)).state
        assertEquals(listOf(maps.id, clock.id, notes.id), (moved.layout!!.grid.single() as GridItem.Folder).apps)
        assertEquals(listOf<Overlay>(openTools), moved.overlays, "The folder stays open")

        val renamed = reduce(moved, Action.RenameFolder(tools.id, " Games ")).state
        assertEquals("Games", (renamed.layout!!.grid.single() as GridItem.Folder).name)
    }

    @Test
    @Verifies("FR-FOLD-07", "FR-GEST-06")
    fun `removing a folder closes it and waits five seconds before its apps return`() {
        val open = folderState.copy(overlays = listOf(openTools))

        val next = reduce(open, Action.RemoveFolder(tools.id))

        assertEquals(folderState.copy(remorse = PendingRemorse(1, Destructive.RemoveFolder(tools.id)), remorses = 1), next.state)
        assertEquals(tools.id, next.state.pendingRemoval)
        assertEquals(listOf<Effect>(Effect.DispatchAfter(REMORSE_DELAY, Action.RemorseDue(1))), next.effects)

        val due = reduce(next.state, Action.RemorseDue(1)).state
        assertEquals(listOf(clock, notes, maps).map { GridItem.App(it.id) }, due.layout!!.grid)
        assertEquals(null, due.remorse)
    }

    @Test
    @Verifies("FR-FOLD-07", "FR-GEST-06")
    fun `a cancelled removal keeps the folder, and its timer runs nothing`() {
        val waiting = reduce(folderState, Action.RemoveFolder(tools.id)).state

        val cancelled = reduce(waiting, Action.CancelRemorse).state

        assertEquals(folderState.copy(remorses = 1), cancelled)
        assertSame(cancelled, reduce(cancelled, Action.RemorseDue(1)).state)
        assertSame(cancelled, reduce(cancelled, Action.CancelRemorse).state)
    }

    @Test
    @Verifies("FR-FOLD-07", "FR-GEST-06")
    fun `a second removal runs the first at once, and the first timer is then stale`() {
        val games = GridItem.Folder(FolderId(2), "Games", listOf(maps.id, app("Chess").id))
        val state = folderState.copy(layout = Layout(grid = listOf(tools, games)))
        val first = reduce(state, Action.RemoveFolder(tools.id)).state

        val second = reduce(first, Action.RemoveFolder(games.id))

        assertEquals(listOf(GridItem.App(clock.id), GridItem.App(notes.id), games), second.state.layout!!.grid)
        assertEquals(PendingRemorse(2, Destructive.RemoveFolder(games.id)), second.state.remorse)
        assertEquals(listOf<Effect>(Effect.DispatchAfter(REMORSE_DELAY, Action.RemorseDue(2))), second.effects)
        assertSame(second.state, reduce(second.state, Action.RemorseDue(1)).state)
    }

    @Test
    @Verifies("FR-FOLD-07")
    fun `a folder waiting for removal can't be opened, added to or removed again`() {
        val waiting = reduce(folderState, Action.RemoveFolder(tools.id)).state

        assertSame(waiting, reduce(waiting, Action.OpenFolder(tools.id)).state)
        assertSame(waiting, reduce(waiting, Action.AddToFolder(maps.id, tools.id)).state)
        val again = reduce(waiting, Action.RemoveFolder(tools.id))
        assertSame(waiting, again.state)
        assertEquals(emptyList(), again.effects)
    }

    @Test
    @Verifies("FR-HOME-02")
    fun `the clock is kept in the state`() {
        val time = ClockInfo(Instant.ofEpochSecond(60), ZoneOffset.UTC, is24Hour = false)

        assertEquals(time, reduce(homeState, Action.ClockChanged(time)).state.clock)
    }
}
