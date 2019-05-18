package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoBuf.Companion.dump

// can by anything, it is only to notify the array isn't empty, for supporting empty array values.
// the only thing important is to remove these bytes before loading
private const val validEntrySuffix = 1.toByte()

@Serializable
private data class Key(@SerialId(1) val c: List<String>)

private fun convertKeyToByteArray(key: List<String>): ByteArray =
        dump(Key.serializer(), Key(key))

class KeyValueStoreImpl @Inject constructor(private val storage: SecureStorage) : KeyValueStore {

    override fun <V> getReference(key: List<String>,
                                  serializer: Serializer<V>): KeyValueStore.Object<V> =
            Ref(key, serializer)

    private inner class Ref<V>(private val key: List<String>,
                               private val serializer: Serializer<V>) : KeyValueStore.Object<V> {

        override fun write(value: V) =
                storage.write(convertKeyToByteArray(key), serializer.dump(value) + validEntrySuffix)

        override fun read(): V? =
                storage.read(convertKeyToByteArray(key))
                    ?.takeIf(ByteArray::isNotEmpty)
                    ?.let { serializer.load(it.dropLast(1).toByteArray()) }

        override fun delete() = storage.write(convertKeyToByteArray(
                key), ByteArray(0))
    }
}