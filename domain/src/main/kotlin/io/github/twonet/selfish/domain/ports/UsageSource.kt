package io.github.twonet.selfish.domain.ports

import io.github.twonet.selfish.domain.model.AppId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Recently used apps, with usage access granted by the user (SRS §5.2; optional, AR-09). */
interface UsageSource {
    /** Whether the user has granted usage access (FR-HOME-10, FR-ONB-04). */
    val granted: Flow<Boolean>

    /** Up to [limit] recently used apps, most recent first (FR-HOME-10). */
    suspend fun recent(limit: Int): List<AppId>

    /** Used while the feature is off or access is not granted (DESIGN §5.5). */
    object Disabled : UsageSource {
        override val granted: Flow<Boolean> = flowOf(false)
        override suspend fun recent(limit: Int): List<AppId> = emptyList()
    }
}
