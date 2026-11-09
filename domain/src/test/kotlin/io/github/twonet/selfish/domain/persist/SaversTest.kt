package io.github.twonet.selfish.domain.persist

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.fakes.FakeLocalStorage
import io.github.twonet.selfish.domain.model.AppearanceMode
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.ports.DocName
import io.github.twonet.selfish.domain.ports.LocalStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Verifies("FR-GEN-07")
@OptIn(ExperimentalCoroutinesApi::class)
class SaversTest {
    private val light = Settings(appearance = AppearanceMode.Light)
    private val dark = Settings(appearance = AppearanceMode.Dark)

    private val fake = FakeLocalStorage()
    private var failing = false
    private val storage = object : LocalStorage by fake {
        override suspend fun write(doc: DocName, content: String) {
            if (failing) error("Disk full")
            fake.write(doc, content)
        }
    }

    private fun writtenSettings() = fake.writes.map { (doc, text) -> doc to SettingsDoc.decode(text) }

    private fun TestScope.save(values: MutableStateFlow<Settings?>, alreadySaved: Settings?) {
        backgroundScope.launch { values.saveTo(storage, DocName.Settings, SettingsDoc, alreadySaved) }
        runCurrent()
    }

    @Test
    fun `nothing is written at start or while the value is unchanged`() = runTest {
        val values = MutableStateFlow<Settings?>(light)
        save(values, alreadySaved = light)

        values.value = light.copy()
        runCurrent()

        assertEquals(emptyList(), fake.writes)
    }

    @Test
    fun `each change is written to its document`() = runTest {
        val values = MutableStateFlow<Settings?>(light)
        save(values, alreadySaved = light)

        values.value = dark
        runCurrent()
        values.value = light
        runCurrent()

        assertEquals(listOf(DocName.Settings to dark, DocName.Settings to light), writtenSettings())
    }

    @Test
    fun `a burst of changes writes the latest`() = runTest {
        val values = MutableStateFlow<Settings?>(light)
        save(values, alreadySaved = light)

        values.value = dark
        values.value = Settings(gridColumns = 5)
        values.value = Settings(gridColumns = 3)
        runCurrent()

        assertEquals(listOf(DocName.Settings to Settings(gridColumns = 3)), writtenSettings())
    }

    @Test
    fun `a change made before saving starts is still written`() = runTest {
        save(MutableStateFlow<Settings?>(dark), alreadySaved = light)

        assertEquals(listOf(DocName.Settings to dark), writtenSettings())
    }

    @Test
    fun `a failed write is retried with the next change`() = runTest {
        val values = MutableStateFlow<Settings?>(light)
        save(values, alreadySaved = light)

        failing = true
        values.value = dark
        runCurrent()
        failing = false
        values.value = Settings(gridColumns = 5)
        runCurrent()

        assertEquals(listOf(DocName.Settings to Settings(gridColumns = 5)), writtenSettings())
    }

    @Test
    fun `after a failed write, going back to the saved value writes nothing`() = runTest {
        val values = MutableStateFlow<Settings?>(light)
        save(values, alreadySaved = light)

        failing = true
        values.value = dark
        runCurrent()
        failing = false
        values.value = light
        runCurrent()

        assertEquals(emptyList(), fake.writes)
    }

    @Test
    fun `null is never written`() = runTest {
        val values = MutableStateFlow<Layout?>(null)
        backgroundScope.launch { values.saveTo(storage, DocName.Layout, LayoutDoc, alreadySaved = null) }
        runCurrent()

        values.value = Layout()
        runCurrent()
        values.value = null
        runCurrent()

        assertEquals(listOf(DocName.Layout to LayoutDoc.encode(Layout())), fake.writes)
    }
}
