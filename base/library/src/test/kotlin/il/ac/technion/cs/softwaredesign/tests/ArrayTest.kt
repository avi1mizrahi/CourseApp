package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.Array
import il.ac.technion.cs.softwaredesign.VolatileKeyValueStore
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArrayTest {
    private val array = Array(VolatileKeyValueStore())

    @Test
    fun `push and get the right value`() {
        array.push(2)

        assertEquals(2, array[0])
    }

    @Test
    fun `empty is size 0`() {
        assertEquals(0, array.size())
    }

    @Test
    fun `push affect size`() {
        array.push(2)

        assertEquals(1, array.size())

        repeat(13) {
            array.push(2)
        }

        assertEquals(14, array.size())
    }

    @Test
    fun `push returns index`() {
        assertEquals(0, array.push(32))
        assertEquals(1, array.push(32))
    }

    @Test
    fun `clear clears`() {
        array.push(232)
        array.push(3)
        array.push(232)

        array.clear()

        assertEquals(0, array.size())
    }

    @Test
    fun `can insert after clear`() {
        array.push(232)
        array.push(3)
        array.push(232)

        array.clear()

        array.push(1)
        array.push(42)

        assertEquals(2, array.size())
    }

    @Test
    fun `for each`() {
        array.push(232)
        array.push(3)
        array.push(232)

        val each = mockk<(Int) -> Unit>(relaxed = true)
        array.forEach(each)

        verifySequence {
            each.invoke(232)
            each.invoke(3)
            each.invoke(232)
        }

        confirmVerified()
    }

    @Test
    fun `for each empty doesn't callback`() {
        array.push(232)
        array.push(3)
        array.push(232)
        array.clear()

        val each = mockk<(Int) -> Unit>()
        array.forEach(each)

        verify { each.invoke(any()) wasNot called }

        confirmVerified()
    }
}
