package io.github.twonet.selfish.domain.ports

import io.github.twonet.selfish.domain.Verifies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Every external dependency has exactly one port, and every port has a fake (AR-03). */
@Verifies("AR-03")
class PortsHaveFakesTest {
    private val portTypes = Ports::class.java.declaredFields.map { it.type }

    @Test
    fun `there is one port per external dependency of SRS section 7`() {
        assertEquals(
            setOf(
                "AppCatalog", "LocalStorage", "SystemWallpaper", "BingFeed",
                "WallpaperScheduler", "OsSettings", "NotificationSource", "UsageSource",
            ),
            portTypes.map { it.simpleName }.toSet(),
        )
        assertEquals(portTypes.size, portTypes.toSet().size, "A port appears twice")
    }

    @Test
    fun `every port is an interface with a fake in the test fixtures`() {
        for (port in portTypes) {
            assertTrue(port.isInterface, "${port.simpleName} is not an interface")
            val fake = try {
                Class.forName("io.github.twonet.selfish.domain.fakes.Fake${port.simpleName}")
            } catch (_: ClassNotFoundException) {
                fail("No Fake${port.simpleName} in the test fixtures")
            }
            assertTrue(port.isAssignableFrom(fake), "${fake.simpleName} does not implement ${port.simpleName}")
        }
    }
}
