package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.KeyValueStore
import il.ac.technion.cs.softwaredesign.Storage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MockStorage : Storage {
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
    private val keyValueStore = KeyValueStore(storage)

    @Test
    fun `read the written`() {
        keyValueStore.write("hi", value = "bye")

        val ret = keyValueStore.read("hi")

        assertEquals("bye", ret)
    }

    @Test
    fun `override entry returns last value`() {
        keyValueStore.write("hi", value = "bye")
        keyValueStore.write("hi", value = "hi")
        keyValueStore.write("hi", value = "guy")

        val ret = keyValueStore.read("hi")

        assertEquals("guy", ret)
    }

    @Test
    fun `keys and values doesn't collide`() {
        keyValueStore.write("hi", value = "bye")
        keyValueStore.write("bye", value = "hi")

        val ret1 = keyValueStore.read("hi")
        val ret2 = keyValueStore.read("bye")

        assertEquals("bye", ret1)
        assertEquals("hi", ret2)
    }

    @Test
    fun `non-deleted key stays intact`() {
        keyValueStore.write("hi", value = "bye")
        keyValueStore.write("bye", value = "hi")

        keyValueStore.delete("bye")
        val ret = keyValueStore.read("hi")

        assertEquals("bye", ret)
    }

    @Test
    fun `deleted key is read as null`() {
        keyValueStore.write("bye", "hi", value = "hi")
        keyValueStore.delete("bye", "hi")

        val ret = keyValueStore.read("bye", "hi")

        assertNull(ret)
    }

    @Test
    fun `deleted key can be rewritten`() {
        keyValueStore.write("bye", "hi", value = "hi")
        keyValueStore.delete("bye", "hi")
        keyValueStore.write("bye", "hi", value = "hi")

        val ret = keyValueStore.read("bye", "hi")

        assertEquals("hi", ret)
    }

    @Test
    fun `null returned when reading non-exist key`() {
        val ret = keyValueStore.read("hi", "bye")

        assertNull(ret)
    }

    @Test
    fun `null returned when reading non-exist key and there is another`() {
        keyValueStore.write("lo lo", value = "hi")
        val ret = keyValueStore.read("hi")

        assertNull(ret)
    }

    @Test
    fun `data should be stored persistently`() {
        keyValueStore.write("lo lo", value = "hi")
        keyValueStore.write("lo", "lo", value = "why")
        val newKVwithOldStorage = KeyValueStore(storage)

        val ret1 = newKVwithOldStorage.read("lo", "lo")
        val ret2 = newKVwithOldStorage.read("lo lo")

        assertEquals("why", ret1)
        assertEquals("hi", ret2)
    }
}