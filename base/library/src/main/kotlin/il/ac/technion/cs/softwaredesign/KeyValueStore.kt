package il.ac.technion.cs.softwaredesign

import java.nio.ByteBuffer
import il.ac.technion.cs.softwaredesign.storage.SecureStorage



private val encoding = Charsets.UTF_8

private const val nullEntryPrefix = "0"
private const val validEntryPrefix = "1"

class KeyValueStore(private val storage: SecureStorage) {
    /**
     *  remove a key-value from the DB
     */
    fun delete(key: List<String>) {
        storage.write(convertKeyToByteArray(key), nullEntryPrefix.toByteArray(encoding))
    }

    /**
     *  read a value from the DB.
     *  @param key: list of strings, will be delimited by "/"
     */
    fun read(key: List<String>) : String? {
        val keyBytes = convertKeyToByteArray(key)
        val res = storage.read(keyBytes) ?: return null
        val outstr = res.toString(encoding)

        if (outstr.startsWith(nullEntryPrefix))
            return null
        return outstr.removeRange(0, 1)
    }

    /**
     *  read an int32 from the DB.
     *  @param key: list of strings, will be delimited by "/"
     *  @return value as int, or 0 if doesn't exist
     */
    fun read_int32(key: List<String>) : Int
    {
        val keyBytes = convertKeyToByteArray(key)
        val res = storage.read(keyBytes) ?: return 0

        // NOTE expecting keys of read_int32 to never conflict with the equivalent read string
        assert(res.size == 4)
        return ByteBuffer.wrap(res).int
    }


    /**
     *  write a value to the DB.
     *  @param key: list of strings, will be delimited by "/"
     *  @param value: value to write
     */
    fun write(key: List<String>, value: String) {
        val keyBytes: ByteArray = convertKeyToByteArray(key)
        val valueBytes = convertValueToByteArray(value)
        storage.write(keyBytes, valueBytes)
    }

    /**
     *  write an int32 to the DB.
     *  @param key: list of strings, will be delimited by "/"
     */
    fun write_int32(key: List<String>, value: Int)
    {
        val keyBytes: ByteArray = convertKeyToByteArray(key)
        val valueBytes = ByteBuffer.allocate(4).putInt(value).array()
        storage.write(keyBytes, valueBytes)
    }


}

private fun convertValueToByteArray(value: String) : ByteArray {
    return "$validEntryPrefix$value".toByteArray(encoding)
}

/**
 *  verifies that the string does not contain illegal chars and converts it to a ByteArray
 */
private fun convertKeyToByteArray(key: List<out String>) : ByteArray {
    // TODO handle slashes later
    return key.joinToString("/").toByteArray(encoding)
}