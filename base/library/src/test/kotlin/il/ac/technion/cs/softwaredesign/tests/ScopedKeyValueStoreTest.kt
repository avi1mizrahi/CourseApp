package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.KeyValueStore
import il.ac.technion.cs.softwaredesign.ScopedKeyValueStore
import il.ac.technion.cs.softwaredesign.Serializer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ScopedKeyValueStoreTest {
    @Test
    fun `keys are appended by scope order`() {
        val kv = mockk<KeyValueStore>()
        val obj = mockk<KeyValueStore.Object<String>>(relaxed = true)
        val serializer = mockk<Serializer<String>>(relaxed = true)
        val key = slot<List<String>>()

        every { kv.getReference(capture(key), serializer) } returns obj

        val scoped = ScopedKeyValueStore(kv, listOf("pre-prefix!", "prefix"))
        val strRef = scoped.getReference(listOf("suffix :)"), serializer)

        strRef.read()

        assertEquals(listOf("pre-prefix!", "prefix", "suffix :)"), key.captured)
    }

    @Test
    fun `scoped of scoped chain key properly`() {
        val kv = mockk<KeyValueStore>()
        val obj = mockk<KeyValueStore.Object<String>>(relaxed = true)
        val serializer = mockk<Serializer<String>>(relaxed = true)
        val key = slot<List<String>>()

        every { kv.getReference(capture(key), serializer) } returns obj

        val scoped =
                ScopedKeyValueStore(
                        ScopedKeyValueStore(
                                ScopedKeyValueStore(kv,
                                                    listOf("1",
                                                           "1.1")),
                                listOf("2!")),
                        listOf("3", "3.1"))
        val strRef = scoped.getReference(listOf("my key"), serializer)

        strRef.write("8h")

        assertEquals(listOf("1", "1.1", "2!", "3", "3.1", "my key"), key.captured)
    }
}