package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.KeyValueStoreImpl
import il.ac.technion.cs.softwaredesign.Serializer
import il.ac.technion.cs.softwaredesign.getIntReference
import il.ac.technion.cs.softwaredesign.getStringReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


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

        val newKvWithOldStorage = KeyValueStoreImpl(storage)

        val ret1 = newKvWithOldStorage.getStringReference(listOf("string2")).read()
        val ret2 = newKvWithOldStorage.getStringReference(listOf("string1")).read()

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
    fun `write and read int`(){
        int1.write(25)
        val ret1 = int1.read()

        assertEquals(25, ret1)
    }

    @Test
    fun `get with given key returns the same reference`() {
        val str = keyValueStore.getStringReference(listOf("why", "oh", "why"))
        val sameStr = keyValueStore.getStringReference(listOf("why", "oh", "why"))

        str.write("this is a new string, never was here before")

        assertEquals("this is a new string, never was here before", sameStr.read())
    }

    data class MyClass(val a: Int, val b: Int)

    @Nested inner class CustomSerializer {

        inner class MyClassSerializer : Serializer<MyClass> {
            override fun dump(t: MyClass) = byteArrayOf(t.a.toByte(), t.b.toByte())

            override fun load(byteArray: ByteArray) = MyClass(byteArray[0].toInt(),
                                                              byteArray[1].toInt())
        }

        @Test
        fun `null returned when reading non-exist key`() {
            val ref = keyValueStore.getReference(listOf("try", "this"), MyClassSerializer())

            val read = ref.read()

            assertNull(read)
        }

        @Test
        fun `read the written`() {
            val ref = keyValueStore.getReference(listOf("try", "this"), MyClassSerializer())

            ref.write(MyClass(3, 14))

            val ret = ref.read()

            assertEquals(MyClass(3, 14), ret)
        }

        @Test
        fun `override entry returns last value`() {
            val ref = keyValueStore.getReference(listOf("shasha"), MyClassSerializer())

            ref.write(MyClass(3, 14))
            ref.write(MyClass(14, 3))

            val ret = ref.read()

            assertEquals(MyClass(14, 3), ret)
        }

    }
}