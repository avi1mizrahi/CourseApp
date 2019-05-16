package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import java.nio.ByteBuffer


private val encoding = Charsets.UTF_8

private const val validEntrySuffix = 1.toByte()


interface KeyValueStore {

    interface DBObject<V> {
        fun write(value: V)
        fun read() : V?
        fun delete()
    }

    interface DBMap<V>{
        fun write(key: String, value : V)
        fun read(key: String) : V?
        fun delete(key: String)
    }

    interface DBInt : DBObject<Int>
    fun getIntReference(key: List<String>) : DBInt

    interface DBString : DBObject<String>
    fun getStringReference(key: List<String>) : DBString

    interface DBIntMap : DBMap<Int>
    fun getIntMapReference(key: List<String>) : DBIntMap

    interface DBStringMap : DBMap<String>
    fun getStringMapReference(key: List<String>) : DBStringMap
}

class KeyValueStoreImpl(private val storage: SecureStorage) : KeyValueStore {

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

    override fun getIntReference(key: List<String>) = DBInt(key)
    inner class DBInt(private val key: List<String>) : KeyValueStore.DBInt {
        override fun write(value: Int) =
                storage.write(convertKeyToByteArray(key), ByteBuffer.allocate(4).putInt(value).array())

        override fun read() : Int? {
            val res = storage.read(convertKeyToByteArray(key)) ?: return null

            if (res.isEmpty()) return null // this is a deleted entry

            // NOTE expecting keys of read_int32 to never conflict with the equivalent read string
            assert(res.size == 4)

            return ByteBuffer.wrap(res).int
        }
        override fun delete() = storage.write(convertKeyToByteArray(key), ByteArray(0))
    }

    override fun getStringReference(key: List<String>) = DBString(key)
    inner class DBString(private val key: List<String>) : KeyValueStore.DBString {
        override fun write(value: String) = storage.write(convertKeyToByteArray(key), convertValueToByteArray(value))
        override fun read() : String? {
            val res = storage.read(convertKeyToByteArray(key)) ?: return null
            if (res.isEmpty()) return null

            return res.dropLast(1).toByteArray().toString(encoding)
        }
        override fun delete() = storage.write(convertKeyToByteArray(key), ByteArray(0))
    }

}

private fun convertValueToByteArray(value: String): ByteArray {
    return value.toByteArray(encoding) + validEntrySuffix
}

@Serializable
private data class Key(@SerialId(1) val c: List<String>)


private fun convertKeyToByteArray(key: List<String>): ByteArray {
    return ProtoBuf.dump(Key.serializer(), Key(key))
}