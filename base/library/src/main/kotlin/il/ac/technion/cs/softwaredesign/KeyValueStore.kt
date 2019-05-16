package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf


private const val validEntrySuffix = 1.toByte()


interface KeyValueStore {

    interface DBObject<V> {
        fun write(value: V)
        fun read(): V?
        fun delete()
    }

    interface DBMap<V>{
        fun write(key: String, value : V)
        fun read(key: String) : V?
        fun delete(key: String)
    }

    fun <V> getReference(key: List<String>, serializer: Serializer<V>): DBObject<V>

    interface DBIntMap : DBMap<Int>
    fun getIntMapReference(key: List<String>) : DBIntMap

    interface DBStringMap : DBMap<String>
    fun getStringMapReference(key: List<String>) : DBStringMap
}


fun KeyValueStore.getIntReference(key: List<String>): KeyValueStore.DBObject<Int> =
        getReference(key, IntSerializer())

fun KeyValueStore.getStringReference(key: List<String>): KeyValueStore.DBObject<String> =
        getReference(key, StringSerializer())


class KeyValueStoreImpl(private val storage: SecureStorage) : KeyValueStore {
    override fun <V> getReference(key: List<String>, serializer: Serializer<V>) =
            Ref(key, serializer)

    inner class Ref<V>(private val key: List<String>, private val serializer: Serializer<V>) :
            KeyValueStore.DBObject<V> {
        override fun write(value: V) =
                storage.write(convertKeyToByteArray(key), serializer.dump(value) + validEntrySuffix)

        override fun read(): V? =
                storage.read(convertKeyToByteArray(key))
                    ?.takeIf(ByteArray::isNotEmpty)
                    ?.let { serializer.load(it.dropLast(1).toByteArray()) }

        override fun delete() = storage.write(convertKeyToByteArray(key), ByteArray(0))
    }


    private fun addlists(key : List<String>, k: String) : List<String> {
        val list = ArrayList<String>(key)
        list.add(k)
        return list
    }

    override fun getIntMapReference(key: List<String>) : KeyValueStore.DBIntMap = DBIntMap(key)
    inner class DBIntMap(private val key: List<String>) : KeyValueStore.DBIntMap {
        override fun write(k: String, value : Int) = getIntReference(addlists(key, k)).write(value)
        override fun read(k: String) : Int? = getIntReference(addlists(key, k)).read()
        override fun delete(k: String) = getIntReference(addlists(key, k)).delete()
    }

    override fun getStringMapReference(key: List<String>) : KeyValueStore.DBStringMap = DBStringMap(key)
    inner class DBStringMap(private val key: List<String>) : KeyValueStore.DBStringMap {
        override fun write(k: String, value : String) = getStringReference(addlists(key, k)).write(value)
        override fun read(k: String) : String? = getStringReference(addlists(key, k)).read()
        override fun delete(k: String) = getStringReference(addlists(key, k)).delete()
    }
}

@Serializable
private data class Key(@SerialId(1) val c: List<String>)

private fun convertKeyToByteArray(key: List<String>): ByteArray {
    return ProtoBuf.dump(Key.serializer(), Key(key))
}