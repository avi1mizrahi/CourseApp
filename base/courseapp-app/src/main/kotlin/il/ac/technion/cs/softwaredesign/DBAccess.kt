package il.ac.technion.cs.softwaredesign

interface Storage {
    fun read(key: ByteArray): ByteArray?
    fun write(key: ByteArray, value: ByteArray)
}

class DBAccess(private val storage: Storage) {

    private val encoding = Charsets.UTF_8

    /**
     *  verifies that the string does not contain illegal chars and converts it to a bytearray
     */
    private fun convertKeyToByteArray(key: Array<out String>) : ByteArray {
        // TODO handle slashes later

        return key.joinToString("/").toByteArray(encoding)
    }


    private fun convertValueToByteArray(value: String?) : ByteArray {
        if (value == null)
            return "0".toByteArray(encoding)

        return ("1$value").toByteArray(encoding)
    }


    /**
     *  remove a key-value from the DB
     */
    public fun delete(vararg key: String) {
        write(*key, value=null)
    }

    /**
     *  read a value from the DB.
     *  @param key: list of strings, will be delimited by "/"
     */
    public fun read(vararg key: String) : String? {
        val keyBytes: ByteArray = convertKeyToByteArray(key)
        val res = storage.read(keyBytes) ?: return null
        val outstr = res.toString(encoding)

        if (outstr.startsWith("0"))
            return null
        return outstr.removeRange(0, 1)
    }


    /**
     *  write a value to the DB.
     *  @param key: list of strings, will be delimited by "/"
     *  @param value: value to write, null to delete the key.
     */
    public fun write(vararg key: String, value: String?) {
        val keyBytes: ByteArray = convertKeyToByteArray(key)

        val valueBytes = convertValueToByteArray(value)
        storage.write(keyBytes, valueBytes)
    }
}