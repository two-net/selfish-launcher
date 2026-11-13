package io.github.twonet.selfish.domain.persist

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.AppearanceMode
import io.github.twonet.selfish.domain.model.Animations
import io.github.twonet.selfish.domain.model.CoversShow
import io.github.twonet.selfish.domain.model.DownloadOn
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Haptics
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.model.SwipeDownOpens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * The saved documents (SRS §8, DESIGN §4.7).
 *
 * The frozen samples under `src/test/resources/persist/` are files that
 * released versions wrote. Never edit what is in them. Adding a field? Add it
 * to the newest sample with a non-default value. Renaming, removing or
 * restructuring one? Add a migration and a new sample, and keep the old ones.
 */
@Verifies("FR-GEN-07", "AR-07", "NFR-REL-03")
class PersistenceTest {
    private val maps = AppId("com.example.maps", "com.example.maps.MainActivity", 0)
    private val mail = AppId("com.example.mail", "com.example.mail.Inbox", 10)
    private val chat = AppId("com.example.chat", "com.example.chat.Main", 0)

    private val sampleLayout = Layout(
        grid = listOf(GridItem.App(maps), GridItem.Folder(FolderId(7), "Work", listOf(mail, chat))),
        home = listOf(mail),
    )

    private val sampleSettings = Settings(
        appearance = AppearanceMode.Dark,
        dailyWallpaper = false,
        market = Market("de-DE"),
        lockScreen = true,
        downloadOn = DownloadOn.WifiOnly,
        showClock = false,
        coversShow = CoversShow.RecentlyUsed,
        gridColumns = 5,
        swipeDownOpens = SwipeDownOpens.NotificationShade,
        notificationsInEvents = true,
        notificationDots = true,
        haptics = Haptics.Off,
        animations = Animations.Reduced,
        notificationsCardDismissed = true,
    )

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResource("/persist/$name")) { "Missing sample $name" }.readText()

    /** Every top-level field of the sample differs from the default, so a rename can't pass unnoticed. */
    private fun <T : Any> assertSampleSetsEveryField(document: VersionedDocument<T>, sample: T) {
        val defaults = Json.parseToJsonElement(document.encode(document.default)).jsonObject
        val values = Json.parseToJsonElement(document.encode(sample)).jsonObject
        for (key in defaults.keys - "schemaVersion") {
            assertNotEquals(defaults[key], values[key], "The sample leaves `$key` at its default; set it in the newest frozen sample")
        }
    }

    @Test
    fun `the frozen version 1 layout still decodes`() {
        assertEquals(sampleLayout, LayoutDoc.decodeOrNull(resource("layout-v1.json")))
        assertSampleSetsEveryField(LayoutDoc, sampleLayout)
    }

    @Test
    fun `the frozen version 1 settings still decode`() {
        assertEquals(sampleSettings, SettingsDoc.decodeOrNull(resource("settings-v1.json")))
        assertSampleSetsEveryField(SettingsDoc, sampleSettings)
    }

    @Test
    fun `both documents round-trip`() {
        assertEquals(sampleLayout, LayoutDoc.decodeOrNull(LayoutDoc.encode(sampleLayout)))
        assertEquals(sampleSettings, SettingsDoc.decodeOrNull(SettingsDoc.encode(sampleSettings)))
        assertEquals(Settings(), SettingsDoc.decodeOrNull(SettingsDoc.encode(Settings())))
    }

    @Test
    fun `no usable layout means a first run`() {
        for (text in listOf(null, "", "{", "[]", """{"schemaVersion":2}""")) {
            assertNull(LayoutDoc.decodeOrNull(text), "for $text")
        }
    }

    @Test
    fun `no usable settings means the defaults`() {
        for (text in listOf(null, "", "{", "[]", """{"schemaVersion":2,"gridColumns":3}""")) {
            assertEquals(Settings(), SettingsDoc.decode(text), "for $text")
        }
    }

    @Test
    fun `an invalid grid item is dropped and the rest is kept`() {
        val text = """
            {"schemaVersion":1,"grid":[
              {"type":"app","id":{"packageName":"com.example.maps","activityName":"com.example.maps.MainActivity","userSerial":0}},
              {"type":"widget","id":1},
              {"type":"app","id":{"packageName":"com.example.broken"}},
              {"type":"folder","id":7,"name":"Work","apps":[
                {"packageName":"com.example.mail","activityName":"com.example.mail.Inbox","userSerial":10},
                {"packageName":42},
                {"packageName":"com.example.chat","activityName":"com.example.chat.Main","userSerial":0}
              ]}
            ],"home":[
              "com.example.mail",
              {"packageName":"com.example.mail","activityName":"com.example.mail.Inbox","userSerial":10}
            ]}
        """.trimIndent()

        assertEquals(sampleLayout, LayoutDoc.decodeOrNull(text))
    }

    @Test
    fun `a folder without a name gets the default name`() {
        val text = """{"grid":[{"type":"folder","id":1,"apps":[
            {"packageName":"a","activityName":"A","userSerial":0},{"packageName":"b","activityName":"B","userSerial":0}]}]}"""

        val folder = LayoutDoc.decodeOrNull(text)?.grid?.single() as GridItem.Folder

        assertEquals(GridItem.Folder.DEFAULT_NAME, folder.name)
    }

    @Test
    fun `a saved layout is normalized on load`() {
        val text = LayoutDoc.encode(Layout(grid = listOf(GridItem.App(maps), GridItem.App(maps)), home = listOf(maps, maps)))

        assertEquals(Layout(grid = listOf(GridItem.App(maps)), home = listOf(maps)), LayoutDoc.decodeOrNull(text))
    }

    @Test
    fun `an invalid setting falls back alone`() {
        val text = """{"schemaVersion":1,"appearance":"dark","gridColumns":9,"dailyWallpaper":"maybe","haptics":"buzz","market":null}"""

        assertEquals(Settings(appearance = AppearanceMode.Dark), SettingsDoc.decode(text))
    }
}
