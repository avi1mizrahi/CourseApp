package il.ac.technion.cs.softwaredesign

import java.nio.charset.Charset



open class DBAccess {

    protected var encoding: Charset = Charsets.UTF_8

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
        val res = this.read(keyBytes) ?: return null
        val outstr = res.toString(encoding)

        if (outstr.startsWith("0"))
            return null;
        return outstr.removeRange(0, 1)
    }


    /**
     *  write a value to the DB.
     *  @param key: list of strings, will be delimited by "/"
     *  @param value: value to write, null to delete the key.
     */
    public fun write(vararg key: String, value: String?) {
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