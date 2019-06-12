package il.ac.technion.cs.softwaredesign

import java.nio.ByteBuffer

/**
 * An interface for serializing a DB key or value to a ByteArray.
 */
interface Serializer<T> {
    fun dump(t: T): ByteArray
    fun load(byteArray: ByteArray): T
}


class IntSerializer : Serializer<Int> {
    override fun dump(t: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(t).array()

    override fun load(byteArray: ByteArray): Int {
        assert(byteArray.size == Int.SIZE_BYTES)

        return ByteBuffer.wrap(byteArray).int
    }
}

private val encoding = Charsets.UTF_8

class StringSerializer : Serializer<String> {
    override fun dump(t: String): ByteArray = t.toByteArray()

    override fun load(byteArray: ByteArray): String = byteArray.toString(encoding)
}


class ByteArraySerializer : Serializer<ByteArray> {
    override fun dump(t: ByteArray): ByteArray = t
    override fun load(byteArray: ByteArray): ByteArray = byteArray
}