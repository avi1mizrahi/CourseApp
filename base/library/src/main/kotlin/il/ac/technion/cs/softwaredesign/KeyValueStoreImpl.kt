package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.LinkedHashMap

// can by anything, it is only to notify the array isn't empty, for supporting empty array values.
// the only thing important is to remove these bytes before loading
private const val validEntrySuffix = 1.toByte()

@Serializable
private data class Key(@SerialId(1) val c: List<String>)

private fun convertKeyToByteArray(key: List<String>): ByteArray =
        ProtoBuf.dump(Key.serializer(), Key(key))

class KeyValueStoreImpl @Inject constructor(private val storage: SyncStorage) : KeyValueStore {

    override fun <V> getReference(key: List<String>,
                                  serializer: Serializer<V>): KeyValueStore.Object<V> =
            Ref(key, serializer)

    private val CACHE_SIZE = 700
    private val cache = HashMap<ByteArray, ByteArray?>()
    private val cacheKeys = LinkedList<ByteArray>()

    private inner class Ref<V>(private val key: List<String>,
                               private val serializer: Serializer<V>) : KeyValueStore.Object<V> {

        override fun write(value: V) {
            val k = convertKeyToByteArray(key)
            val v = serializer.dump(value) + validEntrySuffix
            storage.write(k, v)
            updateCache(k, v)
        }


        override fun read(): V? {
            val k = convertKeyToByteArray(key)

            var bytes = readFromCache(k)
            if (bytes == null) {
                bytes = storage.read(k)
                updateCache(k, bytes)
            }

            return bytes?.takeIf(ByteArray::isNotEmpty)
                    ?.let { serializer.load(it.dropLast(1).toByteArray()) }
        }

        override fun delete() {
            val k = convertKeyToByteArray(key)
            storage.write(k, ByteArray(0))
            updateCache(k, ByteArray(0))
        }
    }


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