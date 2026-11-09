package io.github.twonet.selfish.domain.fakes

import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.ports.UsageSource
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUsageSource : UsageSource {
    override val granted = MutableStateFlow(true)
    var recentlyUsed: List<AppId> = emptyList()

    override suspend fun recent(limit: Int): List<AppId> = recentlyUsed.take(limit)
}
