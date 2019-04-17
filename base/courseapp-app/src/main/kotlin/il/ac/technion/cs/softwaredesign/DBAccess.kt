package il.ac.technion.cs.softwaredesign

import java.nio.charset.Charset


// TODO
public var DB : DBAccess = DBAccess()

////


open class DBAccess {

    // TODO figure out if ASCII is good enough
    protected var encoding: Charset = Charsets.US_ASCII
    // constant [0x00] array.
    private var NullEntry = ByteArray(1, {x -> 0})

    //
    private var illeaglchars = arrayOf('/', '\u0000')

    /**
     * Returns true if any of the chars are illegal
     */
    private fun containsIllegalChars(str: String) : Boolean
    {
        if (str.any( {c -> illeaglchars.contains(c)} ))
            return true

        // TODO change if we change encoding
        if (str.any( { c -> c > '\u0128'} ))
            return true

        return false
    }

    /**
     *  verifies that the string does not contain illegal chars and converts it to a bytearray
     */
    private fun convertKeyToByteArray(key: Array<out String>) : ByteArray? {
        if (key.any( {s -> containsIllegalChars(s)} ))
            return null; // TODO use exceptions?

        return key.joinToString("/").toByteArray(encoding)
    }


    private fun convertValueToByteArray(value: String?) : ByteArray {
        if (value == null)
            return NullEntry

        return value.toByteArray(encoding)
    }


    public fun delete_string(vararg key: String) {
        write_string(*key, value=null)
    }

    public fun read_string(vararg key: String) : String? {
        var keyBytes: ByteArray = convertKeyToByteArray(key) ?: return null

        var res = this.read(keyBytes)
        if (res == null || res.contentEquals(NullEntry))
            return null

        return res.toString(encoding)
    }


    public fun write_string(vararg key: String, value: String?) {
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