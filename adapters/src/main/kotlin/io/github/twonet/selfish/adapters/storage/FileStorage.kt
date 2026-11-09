package io.github.twonet.selfish.adapters.storage

import android.content.Context
import io.github.twonet.selfish.domain.ports.DocName
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.LocalStorage

/**
 * [LocalStorage] over `filesDir`, `noBackupFilesDir` and `AtomicFile` (DESIGN §5.3).
 * Stub until #3: nothing is read or written yet.
 */
@Suppress("unused")
class FileStorage(private val context: Context) : LocalStorage {
    override suspend fun read(doc: DocName): String? = null

    override suspend fun write(doc: DocName, content: String) = Unit

    override suspend fun writeImage(id: ImageId, bytes: ByteArray) = Unit

    override suspend fun readImage(id: ImageId): ByteArray? = null

    override suspend fun deleteImage(id: ImageId) = Unit

    override suspend fun imageIds(): Set<ImageId> = emptySet()

    override suspend fun clearImages() = Unit
}
