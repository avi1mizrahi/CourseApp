package il.ac.technion.cs.softwaredesign

interface Storage {
    fun read(key: ByteArray): ByteArray?
    fun write(key: ByteArray, value: ByteArray)
}

private val encoding = Charsets.UTF_8

private const val nullEntryPrefix = "0"
private const val validEntryPrefix = "1"

class KeyValueStore(private val storage: Storage) {
    /**
     *  remove a key-value from the DB
     */
    fun delete(vararg key: String) {
        storage.write(convertKeyToByteArray(key), nullEntryPrefix.toByteArray(encoding))
    }

    /**
     *  read a value from the DB.
     *  @param key: list of strings, will be delimited by "/"
     */
    fun read(vararg key: String) : String? {
        val keyBytes = convertKeyToByteArray(key)
        val res = storage.read(keyBytes) ?: return null
        val outstr = res.toString(encoding)

        if (outstr.startsWith(nullEntryPrefix))
            return null
        return outstr.removeRange(0, 1)
    }


    /**
     *  write a value to the DB.
     *  @param key: list of strings, will be delimited by "/"
     *  @param value: value to write
     */
    fun write(vararg key: String, value: String) {
        val keyBytes: ByteArray = convertKeyToByteArray(key)

        val valueBytes = convertValueToByteArray(value)
        storage.write(keyBytes, valueBytes)
    }
}

private fun convertValueToByteArray(value: String) : ByteArray {
    return "$validEntryPrefix$value".toByteArray(encoding)
}

/**
 *  verifies that the string does not contain illegal chars and converts it to a ByteArray
 */
private fun convertKeyToByteArray(key: Array<out String>) : ByteArray {
    // TODO handle slashes later
    return key.joinToString("/").toByteArray(encoding)
}