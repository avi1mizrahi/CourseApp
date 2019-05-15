package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import java.nio.ByteBuffer


private val encoding = Charsets.UTF_8

private const val deletedInt32 = ""
private const val validEntrySuffix = 1.toByte()


interface KeyValueStore {
    /**
     *  remove a key-value from the DB
     */
    fun delete(key: List<String>)

    /**
     *  remove a key-value from the DB
     */
    fun deleteInt32(key: List<String>)

    /**
     *  read a value from the DB.
     *  @param key: list of strings
     */
    fun read(key: List<String>): String?

    /**
     *  read an int32 from the DB.
     *  @param key: list of strings
     *  @return value as int, or -1 if doesn't exist
     */
    fun readInt32(key: List<String>): Int?

    /**
     *  write a value to the DB.
     *  @param key: list of strings
     *  @param value: value to write
     */
    fun write(key: List<String>, value: String)

    /**
     *  write an int32 to the DB.
     *  @param key: list of strings
     */
    fun writeInt32(key: List<String>, value: Int?)
}

class KeyValueStoreImpl(private val storage: SecureStorage) : KeyValueStore {

    override fun delete(key: List<String>) {
        storage.write(convertKeyToByteArray(key), ByteArray(0))
    }

    override fun deleteInt32(key: List<String>) {
        storage.write(convertKeyToByteArray(key), deletedInt32.toByteArray(encoding))
    }

    override fun read(key: List<String>): String? {
        val res = storage.read(convertKeyToByteArray(key)) ?: return null
        if (res.isEmpty()) return null

        return res.dropLast(1).toByteArray().toString(encoding)
    }

    override fun readInt32(key: List<String>): Int? {
        val res = storage.read(convertKeyToByteArray(key)) ?: return null

        if (res.size == deletedInt32.length) return null // this is a deleted entry

        // NOTE expecting keys of read_int32 to never conflict with the equivalent read string
        assert(res.size == 4)

        return ByteBuffer.wrap(res).int
    }

    override fun write(key: List<String>, value: String) {
        storage.write(convertKeyToByteArray(key), convertValueToByteArray(value))
    }

    override fun writeInt32(key: List<String>, value: Int?) {
        if (value == null) {
            storage.write(convertKeyToByteArray(key), deletedInt32.toByteArray(encoding))
        } else {
            val valueBytes = ByteBuffer.allocate(4).putInt(value).array()
            storage.write(convertKeyToByteArray(key), valueBytes)
        }
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