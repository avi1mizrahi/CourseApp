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

    private val string1 = keyValueStore.getStringReference(listOf("string1"))
    private val string2 = keyValueStore.getStringReference(listOf("string2"))


    private val int1 = keyValueStore.getIntReference(listOf("int1"))


    // TODO add tests for map

    @Test
    fun `read the written`() {
        string1.write("bye")


        val ret = string1.read()

        assertEquals("bye", ret)
    }

    @Test
    fun `override entry returns last value`() {
        string1.write("bye")
        string1.write("guy")

        val ret = string1.read()

        assertEquals("guy", ret)
    }

    @Test
    fun `keys and values doesn't collide`() {
        string1.write("bye")
        string2.write("hi")

        val ret1 = string1.read()
        val ret2 = string2.read()

        assertEquals("bye", ret1)
        assertEquals("hi", ret2)
    }

    @Test
    fun `non-deleted key stays intact`() {
        string1.write("bye")
        string2.write("hi")

        string2.delete()
        val ret = string1.read()

        assertEquals("bye", ret)
    }

    @Test
    fun `deleted key is read as null`() {
        string1.write("hi")
        string1.delete()

        val ret = string1.read()

        assertNull(ret)
    }

    @Test
    fun `deleted key can be rewritten`() {
        string1.write("hi")
        string1.delete()
        string1.write("hi")

        val ret = string1.read()

        assertEquals("hi", ret)
    }

    @Test
    fun `null returned when reading non-exist key`() {
        val ret = string1.read()

        assertNull(ret)
    }

    @Test
    fun `null returned when reading non-exist key and there is another`() {
        string1.write("hi")
        val ret = string2.read()

        assertNull(ret)
    }

    @Test
    fun `data should be stored persistently`() {
        string1.write("hi")
        string2.write("why")
        val newKVwithOldStorage = KeyValueStoreImpl(storage)

        val ret1 = string2.read()
        val ret2 = string1.read()

        assertEquals("why", ret1)
        assertEquals("hi", ret2)
    }

    @Test
    fun `empty string doesnt cause issues`() {
        string1.write("")
        val ret1 = string1.read()

        assertEquals("", ret1)
    }


    @Test
    fun `read int returns null if key doesn't exist`(){
        val ret1 = int1.read()

        assertEquals(null, ret1)
    }

    @Test
    fun `write and read int32`(){
        int1.write(25)
        val ret1 = int1.read()

        assertEquals(25, ret1)
    }
}