package io.github.twonet.selfish.domain.ports

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

/**
 * Device-wide settings and state the launcher follows (SRS §5.2: dark theme,
 * system animation setting, network and power state, default Home role).
 * Haptics need no port: Presentation's view haptics already follow the system
 * touch-feedback setting (DESIGN §6.4).
 */
interface OsSettings {
    /** The device's dark-theme setting; `null` if the device has none (FR-THM-03, FR-THM-04). */
    val systemDark: Flow<Boolean?>

    /** Whether the system animator duration scale is 0 (FR-ANIM-15). */
    val animationsOff: Flow<Boolean>

    /**
     * Emits the current time on collection, then on every minute, time
     * change, time-zone change and 12/24-hour change (FR-HOME-02,
     * NFR-L10N-02). The store collects it only while the launcher is shown.
     */
    val clock: Flow<ClockInfo>

    /** The current network and Data Saver state, read when a download is considered (FR-WP-06). */
    suspend fun networkState(): NetworkState

    /** Whether Battery Saver is on (FR-WP-06). */
    suspend fun batterySaver(): Boolean

    /** Whether the launcher holds the default Home role (FR-GEN-02). */
    suspend fun isDefaultHome(): Boolean

    /** Shows the system's prompt to make the launcher the default Home app (FR-GEN-02). */
    fun requestDefaultHome()
}

data class NetworkState(
    val connected: Boolean,
    val metered: Boolean,
    val dataSaver: Boolean,
)

data class ClockInfo(
    val now: Instant,
    val zone: ZoneId,
    val is24Hour: Boolean,
)
