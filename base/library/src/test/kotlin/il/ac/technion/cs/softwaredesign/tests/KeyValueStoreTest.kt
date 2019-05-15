package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.KeyValueStoreImpl
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test


class MockStorage : SecureStorage {
    private val encoding = Charsets.UTF_8

    private val keyvalDB = HashMap<String, ByteArray>()

    override fun read(key: ByteArray): ByteArray? {
        return keyvalDB[key.toString(encoding)]
    }

    override fun write(key: ByteArray, value: ByteArray) {
        keyvalDB[key.toString(encoding)] = value
    }
}

internal class KeyValueStoreTest {

    private val storage = MockStorage()
    private val keyValueStore = KeyValueStoreImpl(storage)

    @Test
    fun `read the written`() {
        keyValueStore.write(listOf("hi"), value = "bye")

        val ret = keyValueStore.read(listOf("hi"))

        assertEquals("bye", ret)
    }

    @Test
    fun `override entry returns last value`() {
        keyValueStore.write(listOf("hi"), value = "bye")
        keyValueStore.write(listOf("hi"), value = "hi")
        keyValueStore.write(listOf("hi"), value = "guy")

        val ret = keyValueStore.read(listOf("hi"))

        assertEquals("guy", ret)
    }

    @Test
    fun `keys and values doesn't collide`() {
        keyValueStore.write(listOf("hi"), value = "bye")
        keyValueStore.write(listOf("bye"), value = "hi")

        val ret1 = keyValueStore.read(listOf("hi" ))
        val ret2 = keyValueStore.read(listOf("bye"))

        assertEquals("bye", ret1)
        assertEquals("hi", ret2)
    }

    @Test
    fun `non-deleted key stays intact`() {
        keyValueStore.write(listOf("hi" ), value = "bye")
        keyValueStore.write(listOf("bye"), value = "hi")

        keyValueStore.delete(listOf("bye"))
        val ret = keyValueStore.read(listOf("hi"))

        assertEquals("bye", ret)
    }

    @Test
    fun `deleted key is read as null`() {
        keyValueStore.write(listOf("bye", "hi"), value = "hi")
        keyValueStore.delete(listOf("bye", "hi"))

        val ret = keyValueStore.read(listOf("bye", "hi"))

        assertNull(ret)
    }

    @Test
    fun `deleted key can be rewritten`() {
        keyValueStore.write(listOf("bye", "hi"), value = "hi")
        keyValueStore.delete(listOf("bye", "hi"))
        keyValueStore.write(listOf("bye", "hi"), value = "hi")

        val ret = keyValueStore.read(listOf("bye", "hi"))

        assertEquals("hi", ret)
    }

    @Test
    fun `null returned when reading non-exist key`() {
        val ret = keyValueStore.read(listOf("hi", "bye"))

        assertNull(ret)
    }

    @Test
    fun `null returned when reading non-exist key and there is another`() {
        keyValueStore.write(listOf("lo lo"), value = "hi")
        val ret = keyValueStore.read(listOf("hi"))

        assertNull(ret)
    }

    @Test
    fun `data should be stored persistently`() {
        keyValueStore.write(listOf("lo lo"), value = "hi")
        keyValueStore.write(listOf("lo", "lo"), value = "why")
        val newKVwithOldStorage = KeyValueStoreImpl(storage)

        val ret1 = newKVwithOldStorage.read(listOf("lo", "lo"))
        val ret2 = newKVwithOldStorage.read(listOf("lo lo"))

        assertEquals("why", ret1)
        assertEquals("hi", ret2)
    }

    @Test
    fun `empty string doesnt cause issues`() {
        keyValueStore.write(listOf("a"), value = "")
        val newKVwithOldStorage = KeyValueStoreImpl(storage)

        val ret1 = newKVwithOldStorage.read(listOf("a"))

        assertEquals("", ret1)
    }


    @Test
    fun `read int32 returns null if key doesn't exist`(){
        val newKVwithOldStorage = KeyValueStoreImpl(storage)

        val ret1 = newKVwithOldStorage.readInt32(listOf("a"))

        assertEquals(null, ret1)
    }

    @Test
    fun `write and read int32`(){
        keyValueStore.writeInt32(listOf("a"), value = 25)
        val newKVwithOldStorage = KeyValueStoreImpl(storage)


        val ret1 = newKVwithOldStorage.readInt32(listOf("a"))

        assertEquals(25, ret1)
    }
}