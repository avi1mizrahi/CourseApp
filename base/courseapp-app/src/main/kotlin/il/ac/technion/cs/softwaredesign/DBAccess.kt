package il.ac.technion.cs.softwaredesign

import java.nio.charset.Charset



open class DBAccess {

    // TODO figure out if ASCII is good enough
    protected var encoding: Charset = Charsets.UTF_8
    // constant [0x00] array.
    private var nullEntry = ByteArray(1) {0}

    //
    private var illeaglchars = arrayOf('/', '\u0000')

    /**
     * Returns true if any of the chars are illegal
     */
    private fun containsIllegalChars(str: String) : Boolean
    {
        if (str.any { c -> illeaglchars.contains(c)})
            return true

        return false
    }

    /**
     *  verifies that the string does not contain illegal chars and converts it to a bytearray
     */
    private fun convertKeyToByteArray(key: Array<out String>) : ByteArray? {
        if (key.any { s -> containsIllegalChars(s)})
            return null // TODO use exceptions?

        return key.joinToString("/").toByteArray(encoding)
    }


    private fun convertValueToByteArray(value: String?) : ByteArray {
        if (value == null)
            return nullEntry

        return value.toByteArray(encoding)
    }


    /**
     *  remove a key-value from the DB
     */
    public fun deleteString(vararg key: String) {
        writeString(*key, value=null)
    }

    /**
     *  read a value from the DB.
     *  @param key: list of strings, will be delimited by "/"
     */
    public fun readString(vararg key: String) : String? {
        var keyBytes: ByteArray = convertKeyToByteArray(key) ?: return null

        var res = this.read(keyBytes)
        if (res == null || res.contentEquals(nullEntry))
            return null

        return res.toString(encoding)
    }


    /**
     *  write a value to the DB.
     *  @param key: list of strings, will be delimited by "/"
     *  @param value: value to write, null to delete the key.
     */
    public fun writeString(vararg key: String, value: String?) {
        var keyBytes: ByteArray = convertKeyToByteArray(key) ?: return

        var valueBytes = convertValueToByteArray(value)
        this.write(keyBytes, valueBytes)
    }


    // Overridable read/write functions for tests
    protected open fun read(key: ByteArray): ByteArray? {
        return il.ac.technion.cs.softwaredesign.storage.read(key)
    }

    protected open fun write(key: ByteArray, value: ByteArray) {
        il.ac.technion.cs.softwaredesign.storage.write(key, value)
    }

}