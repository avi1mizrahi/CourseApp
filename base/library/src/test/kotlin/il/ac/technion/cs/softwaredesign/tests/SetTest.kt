package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.Set
import il.ac.technion.cs.softwaredesign.VolatileKeyValueStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SetTest {

    private val list = Set(VolatileKeyValueStore())

    @Test
    fun `nothing exists in empty list`() {
        val ret = list.exists(5)

        assertEquals(false, ret)
    }

    @Test
    fun `add makes item exist and different item not`() {
        list.add(5)

        val ret1 = list.exists(5)
        val ret2 = list.exists(6)

        assertEquals(true, ret1)
        assertEquals(false, ret2)
    }

    @Test
    fun `test basic remove`() {
        list.add(5)
        list.remove(5)

        val ret = list.exists(5)

        assertEquals(false, ret)
    }

    @Test
    fun `adding twice does not cause duplicates`() {
        list.add(5)
        list.add(5)
        list.remove(5)

        val ret = list.exists(5)

        assertEquals(false, ret)
    }

    @Test
    fun `basic getAll`() {
        list.add(1)
        list.add(2)
        list.add(3)

        val ret = mutableListOf<Int>()
        list.forEach { ret.add(it) }

        assertEquals(3, ret.size)
        assertTrue(ret.contains(1))
        assertTrue(ret.contains(2))
        assertTrue(ret.contains(3))
    }

    @Test
    fun `add a few and remove between, first, and last`() {
        for (i in 1..10)
            list.add(i)

        list.remove(5)
        list.remove(1)
        list.remove(10)
        list.remove(7)

        val ret = mutableListOf<Int>()
        list.forEach { ret.add(it) }
        val count = list.count()

        assertEquals(6, count) // count()
        assertEquals(6, ret.size) // getAll() size
        for (i in listOf(2,3,4,6,8,9))
            assertTrue(ret.contains(i))
        for (i in listOf(1,5,7,10))
            assertFalse(ret.contains(i))
    }

    @Test
    fun `add a few and remove all, then add again`() {
        for (i in 1..10)
            list.add(i)
        for (i in 1..10)
            list.remove(i)
        list.add(1)
        list.add(2)

        val ret = mutableListOf<Int>()
        list.forEach { ret.add(it) }

        assertEquals(2, ret.size)
        assertTrue(ret.contains(1))
        assertTrue(ret.contains(2))
        assertFalse(ret.contains(3))
    }
}
