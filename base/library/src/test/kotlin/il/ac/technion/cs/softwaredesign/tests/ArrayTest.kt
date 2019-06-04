package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.Array
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArrayTest {
    private val array = Array(VolatileKeyValueStore())
    private val arrayInt = ArrayInt(VolatileKeyValueStore())

    class MockProxy(var DB : KeyValueStore) {
        val int1 = DB.getIntReference("int1")
    }


    private fun createNewMockProxySlot() : MockProxy {
        val (scopedDB, _) = array.newSlot()
        return MockProxy(scopedDB)
    }

    private fun readMockProxySlot(index : Int) : MockProxy? {
        return MockProxy(array[index] ?: return null)
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



        val each = mockk<(KeyValueStore) -> Unit>(relaxed = true)
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

        val each = mockk<(KeyValueStore) -> Unit>()
        array.forEach(each)

        verify { each.invoke(any()) wasNot called }

        confirmVerified()
    }


    @Test
    fun `ArrayInt insertion`() {
        arrayInt.push(3)

        assertEquals(1, arrayInt.size())
        assertEquals(3, arrayInt[0])
    }

    @Test
    fun `ArrayInt clear`() {
        arrayInt.push(3)
        arrayInt.clear()

        assertEquals(0, arrayInt.size())
    }
    @Test
    fun `ArrayInt for each`() {
        arrayInt.push(3)
        arrayInt.push(5)
        arrayInt.push(4)


        val each = mockk<(Int) -> Unit>(relaxed = true)
        arrayInt.forEach(each)

        verifySequence {
            each.invoke(3)
            each.invoke(5)
            each.invoke(4)
        }

        confirmVerified()
    }

}
