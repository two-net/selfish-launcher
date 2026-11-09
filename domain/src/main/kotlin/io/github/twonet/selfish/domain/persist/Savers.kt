package io.github.twonet.selfish.domain.persist

import io.github.twonet.selfish.domain.ports.DocName
import io.github.twonet.selfish.domain.ports.LocalStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * Writes each new value of this flow to [doc] through [storage], until
 * cancelled (DESIGN §3.3). Call it on a `StateFlow` projection, such as
 * `state.map { it.settings }`.
 *
 * - [alreadySaved] is what storage holds now, so nothing is written until the
 *   value differs from it. The comparison is with the last value written, not
 *   the first one collected, so a change made before collecting starts is
 *   still written.
 * - A `StateFlow` conflates, so a burst of changes writes only the latest.
 * - `null` is never written.
 * - A failed write is dropped, and the next change writes again.
 */
suspend fun <T : Any> Flow<T?>.saveTo(
    storage: LocalStorage,
    doc: DocName,
    document: VersionedDocument<T>,
    alreadySaved: T?,
) {
    var lastWritten = alreadySaved
    filterNotNull().distinctUntilChanged().collect { value ->
        if (value == lastWritten) return@collect
        try {
            storage.write(doc, document.encode(value))
            lastWritten = value
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Storage is full or failing. Keep running; the next change retries.
        }
    }
}
