package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.util.*
import java.util.concurrent.CompletableFuture

/** This is an adapter interface for the previous storage,
 *   and adapter classes for each conversion
 */

interface SyncStorage {
    fun read(key: ByteArray): ByteArray?
    fun write(key: ByteArray, value: ByteArray)
}

class AsyncStorageAdapter @Inject constructor(private val secureStorageFactory: SecureStorageFactory) : SyncStorage {
    val secureStorage = secureStorageFactory.open("main".toByteArray()).join()

    override fun read(key: ByteArray): ByteArray? {
        var bytes = readFromCache(key)
        if (bytes == null) {
            bytes = secureStorage.read(key).join()
            updateCache(key, bytes)
        }
        return bytes
    }


    override fun write(key: ByteArray, value: ByteArray) {
        secureStorage.write(key, value).join()!!
        updateCache(key, value)
    }



    private val CACHE_SIZE = 700
    private val cache = HashMap<ByteArray, ByteArray?>()
    private val cacheKeys = LinkedList<ByteArray>()


    @Synchronized
    private fun readFromCache(key: ByteArray) : ByteArray? {
        if (cache.contains(key)) {
            return cache[key]
        }
        return null
    }

    @Synchronized
    private fun updateCache(key: ByteArray, value: ByteArray?) {
        if (cache.size >= CACHE_SIZE) {
            val keyToRemove = cacheKeys.poll()
            cache.keys.remove(keyToRemove)
        }

        if (cache.containsKey(key)) {
            cacheKeys.remove(key) // This is O(N)
        }
        cacheKeys.add(key)


        cache[key] = value ?: ByteArray(0)
    }

}

