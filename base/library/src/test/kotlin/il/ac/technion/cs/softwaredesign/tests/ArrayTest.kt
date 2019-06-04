package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.Array
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ArrayTest {
    private val array = Array(VolatileKeyValueStore())

    class mockProxy(var DB : ScopedKeyValueStore) {
        val int1 = DB.getIntReference("int1")
    }


    private fun createNewMockProxySlot() : mockProxy {
        val (scopedDB, index) = array.newSlot()
        return mockProxy(scopedDB)
    }

    private fun readMockProxySlot(index : Int) : mockProxy? {
        return mockProxy(array[index]?: return null)
    }

    @Test
    fun `push and get the right value`() {
        val obj = createNewMockProxySlot()
        obj.int1.write(2)

        assertEquals(2, readMockProxySlot(0)!!.int1.read())
    }

    @Test
    fun `empty is size 0`() {
        assertEquals(0, array.size())
    }

    @Test
    fun `push affect size`() {
        createNewMockProxySlot()

        assertEquals(1, array.size())

        repeat(13) {
            createNewMockProxySlot()
        }

        assertEquals(14, array.size())
    }

    @Test
    fun `push returns index`() {
        val (_, index1) = array.newSlot()
        val (_, index2) = array.newSlot()

        assertEquals(0, index1)
        assertEquals(1, index2)
    }

    @Test
    fun `clear clears`() {
        array.newSlot()
        array.newSlot()
        array.newSlot()
        array.clear()

        assertEquals(0, array.size())
    }

    @Test
    fun `can insert after clear`() {
        array.newSlot()
        array.newSlot()
        array.newSlot()

        array.clear()

        array.newSlot()
        array.newSlot()

        assertEquals(2, array.size())
    }

    @Test
    fun `for each`() {
        val p1 = createNewMockProxySlot()
        val p2 = createNewMockProxySlot()
        val p3 = createNewMockProxySlot()



        val each = mockk<(ScopedKeyValueStore) -> Unit>(relaxed = true)
        array.forEach(each)

        verifySequence {
            each.invoke(p1.DB)
            each.invoke(p2.DB)
            each.invoke(p3.DB)
        }

        confirmVerified()
    }

    @Test
    fun `for each empty doesn't callback`() {
        createNewMockProxySlot()
        createNewMockProxySlot()
        createNewMockProxySlot()
        array.clear()

        val each = mockk<(ScopedKeyValueStore) -> Unit>()
        array.forEach(each)

        verify { each.invoke(any()) wasNot called }

        confirmVerified()
    }
}
