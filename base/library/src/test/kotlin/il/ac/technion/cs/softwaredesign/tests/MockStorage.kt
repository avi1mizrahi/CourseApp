package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.util.*
import kotlin.collections.HashMap

class MockStorage : SecureStorage {
    private class BiteArray(private val byte: ByteArray) {
        override fun equals(other: Any?): Boolean =
                if (other is BiteArray) byte contentEquals other.byte else false

        override fun hashCode(): Int = Arrays.hashCode(byte)
    }

    private val map = HashMap<BiteArray, ByteArray>()

    override fun read(key: ByteArray): ByteArray? = map[BiteArray(key)]

    override fun write(key: ByteArray, value: ByteArray) = map.set(BiteArray(key), value)
}
