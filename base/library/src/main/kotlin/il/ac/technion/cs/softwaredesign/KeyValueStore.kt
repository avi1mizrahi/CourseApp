package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf


interface KeyValueStore {

    interface DBObject<V> {
        fun write(value: V)
        fun read(): V?
        fun delete()
    }

    interface DBMap<V> {
        fun write(key: String, value: V)
        fun read(key: String): V?
        fun delete(key: String)
    }

    fun <V> getReference(key: List<String>, serializer: Serializer<V>): DBObject<V>
    fun <V> getMapReference(key: List<String>, serializer: Serializer<V>): DBMap<V>
}


class ScopedKeyValueStore(private val prefix: List<String>, private val parent: KeyValueStore) :
        KeyValueStore {
    override fun <V> getReference(key: List<String>,
                                  serializer: Serializer<V>): KeyValueStore.DBObject<V> =
            parent.getReference(prefix + key, serializer)
}


fun KeyValueStore.getIntReference(key: List<String>): KeyValueStore.DBObject<Int> =
        getReference(key, IntSerializer())

fun KeyValueStore.getStringReference(key: List<String>): KeyValueStore.DBObject<String> =
        getReference(key, StringSerializer())


fun KeyValueStore.getIntMapReference(key: List<String>): KeyValueStore.DBMap<Int> =
        getMapReference(key, IntSerializer())


private const val validEntrySuffix = 1.toByte()

class KeyValueStoreImpl(private val storage: SecureStorage) : KeyValueStore {
    override fun <V> getReference(key: List<String>,
                                  serializer: Serializer<V>): KeyValueStore.DBObject<V> =
            Ref(key, serializer)

    private inner class Ref<V>(private val key: List<String>, private val serializer: Serializer<V>) :
            KeyValueStore.DBObject<V> {

        override fun write(value: V) =
                storage.write(convertKeyToByteArray(key), serializer.dump(value) + validEntrySuffix)

        override fun read(): V? =
                storage.read(convertKeyToByteArray(key))
                    ?.takeIf(ByteArray::isNotEmpty)
                    ?.let { serializer.load(it.dropLast(1).toByteArray()) }

        override fun delete() = storage.write(convertKeyToByteArray(key), ByteArray(0))
    }


    override fun <V> getMapReference(key: List<String>,
                                     serializer: Serializer<V>): KeyValueStore.DBMap<V> =
            DBMap(key, serializer)

    private inner class DBMap<V>(private val key: List<String>,
                                 private val serializer: Serializer<V>) :
            KeyValueStore.DBMap<V> {

        private fun concatKeys(pref: List<String>, key: String): List<String> =
                pref.toMutableList().apply { add(key) }

        override fun write(key: String, value: V) =
                getReference(concatKeys(this.key, key), serializer).write(value)

        override fun read(key: String): V? =
                getReference(concatKeys(this.key, key), serializer).read()

        override fun delete(key: String) =
                getReference(concatKeys(this.key, key), serializer).delete()
    }
}

@Serializable
private data class Key(@SerialId(1) val c: List<String>)

private fun convertKeyToByteArray(key: List<String>): ByteArray {
    return ProtoBuf.dump(Key.serializer(), Key(key))
}