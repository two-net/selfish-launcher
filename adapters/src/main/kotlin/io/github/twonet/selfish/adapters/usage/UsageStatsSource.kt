package io.github.twonet.selfish.adapters.usage

import android.content.Context
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.ports.UsageSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * [UsageSource] over `UsageStatsManager` (DESIGN §5.1). Optional (AR-09):
 * nothing outside this package refers to it except the composition root.
 * A stub until recently used covers (FR-HOME-10, Could) are built.
 */
@Suppress("unused")
class UsageStatsSource(private val context: Context) : UsageSource {
    override val granted: Flow<Boolean> = flowOf(false)

    override suspend fun recent(limit: Int): List<AppId> = emptyList()
}
