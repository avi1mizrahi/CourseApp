package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.util.HashMap
import java.util.LinkedList
import java.util.concurrent.CompletableFuture

class CachedStorage(private val secureStorage : SecureStorage) : SecureStorage by secureStorage {

    @Synchronized
    override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
        var bytes = readFromCache(key)
        if (bytes == null) {
            bytes = secureStorage.read(key).join()
            bytes?.let { updateCache(key, it) }
        }

        return CompletableFuture.completedFuture(bytes)


    }

    @Synchronized
    override fun write(key: ByteArray, value: ByteArray): CompletableFuture<Unit> {
        secureStorage.write(key, value).join()
        updateCache(key, value)

        return CompletableFuture.completedFuture(Unit)
    }


    private val CACHE_SIZE = 2000
    private val cache = HashMap<String, ByteArray>()
    private val cacheKeys = LinkedList<String>()


    private fun readFromCache(key: ByteArray) : ByteArray? {
        val keyS = key.toString(Charsets.UTF_8)
        return cache[keyS]
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