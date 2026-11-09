package io.github.twonet.selfish.domain.fakes

import io.github.twonet.selfish.domain.ports.DocName
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.LocalStorage

class FakeLocalStorage : LocalStorage {
    val docs = mutableMapOf<DocName, String>()
    val images = mutableMapOf<ImageId, ByteArray>()

    override suspend fun read(doc: DocName): String? = docs[doc]

    override suspend fun write(doc: DocName, content: String) {
        docs[doc] = content
    }

    override suspend fun writeImage(id: ImageId, bytes: ByteArray) {
        images[id] = bytes.copyOf()
    }

    override suspend fun readImage(id: ImageId): ByteArray? = images[id]?.copyOf()

    override suspend fun deleteImage(id: ImageId) {
        images -= id
    }

    override suspend fun imageIds(): Set<ImageId> = images.keys.toSet()

    override suspend fun clearImages() {
        images.clear()
    }
}
