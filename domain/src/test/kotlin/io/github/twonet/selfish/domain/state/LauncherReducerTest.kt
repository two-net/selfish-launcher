package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.ports.ScheduledRun
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `launch keeps the state and asks for a launch effect`() {
        val state = LauncherState(apps = listOf(app("Maps")))
        val bounds = Bounds(1, 2, 3, 4)

        val next = reduce(state, Action.Launch(app("Maps").id, bounds))

        assertEquals(state, next.state)
        assertEquals(listOf<Effect>(Effect.LaunchApp(app("Maps").id, bounds)), next.effects)
    }

    @Test
    fun `a scheduled run is finished`() {
        val run = ScheduledRun { }

        val next = reduce(LauncherState(), Action.ScheduledRunDue(run))

        assertEquals(listOf<Effect>(Effect.FinishRun(run)), next.effects)
    }
}
