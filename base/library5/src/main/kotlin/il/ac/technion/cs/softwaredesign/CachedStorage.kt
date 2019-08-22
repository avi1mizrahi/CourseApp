package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.util.HashMap
import java.util.LinkedList
import java.util.concurrent.CompletableFuture

class CachedStorage(private val secureStorage : SecureStorage) : SecureStorage by secureStorage {

    override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
        val bytes = readFromCache(key)
        if (bytes == null) {
            return secureStorage.read(key).thenApply {
                it?.let { updateCache(key, it) }

                it
            }

        }
        else {
            return CompletableFuture.completedFuture(bytes)
        }

    }

    override fun write(key: ByteArray, value: ByteArray): CompletableFuture<Unit> {
        return secureStorage.write(key, value).thenApply {
            updateCache(key, value)

            Unit
        }
    }


    private val CACHE_SIZE = 2000
    private val cache = HashMap<String, ByteArray?>()
    private val cacheKeys = LinkedList<String>()


    @Synchronized
    private fun readFromCache(key: ByteArray) : ByteArray? {
        val keyS = key.toString(Charsets.UTF_8)
        if (cache.contains(keyS)) {
            return cache[keyS]
        }
        return null
    }

    @Synchronized
    private fun updateCache(key: ByteArray, value: ByteArray) {
        val keyS = key.toString(Charsets.UTF_8)

        if (cache.size >= CACHE_SIZE) {
            val keyToRemove = cacheKeys.poll()
            cache.keys.remove(keyToRemove)
        }

        if (cache.containsKey(keyS)) {
            cacheKeys.remove(keyS) // This is O(N)
        }
        cacheKeys.addLast(keyS)


        cache[keyS] = value
    }

}