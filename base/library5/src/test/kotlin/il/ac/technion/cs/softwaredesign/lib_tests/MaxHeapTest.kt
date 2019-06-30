package il.ac.technion.cs.softwaredesign.lib_tests

import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import il.ac.technion.cs.softwaredesign.FakeStorage
import il.ac.technion.cs.softwaredesign.FakeStorageModule
import il.ac.technion.cs.softwaredesign.MaxHeapFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

internal class MaxHeapTest {
    private val injector = Guice.createInjector(FakeStorageModule())

    private val factory = injector.getInstance<MaxHeapFactory>()
    val heap = factory.newMaxHeap()

    @BeforeEach
    fun prepDb() {
        heap.add("1")
        heap.changeScore("1", 3)
        heap.add("2")
        heap.changeScore("2", 3)
        heap.add("3")
        heap.add("4")
        heap.changeScore("4", 3)
        heap.add("5")
        heap.add("6")
        heap.add("7")
        heap.add("8")
        heap.add("10")
        heap.changeScore("10", 2)
        heap.add("9")
        heap.changeScore("9", 2)
        heap.add("11")
        heap.add("12")
    }

    @AfterEach
    fun clearDb() {
        FakeStorage("".toByteArray()).clear()
    }

    @Test
    fun `no change`() {
        val expected = LinkedList<String>()
        expected.add("1")
        expected.add("2")
        expected.add("4")
        expected.add("10")
        expected.add("9")
        expected.add("3")
        expected.add("5")
        expected.add("6")
        expected.add("7")
        expected.add("8")
        assertEquals(heap.topTen(), expected)
    }

    @Test
    fun `removing 2`() {
        val expected = LinkedList<String>()
        expected.add("1")
        expected.add("4")
        expected.add("10")
        expected.add("9")
        expected.add("3")
        expected.add("5")
        expected.add("6")
        expected.add("7")
        expected.add("8")
        expected.add("11")
        heap.remove("2")
        assertEquals(heap.topTen(), expected)
    }

    @Test
    fun `remove and add 2`() {
        val expected = LinkedList<String>()
        expected.add("1")
        expected.add("4")
        expected.add("2")
        expected.add("10")
        expected.add("9")
        expected.add("3")
        expected.add("5")
        expected.add("6")
        expected.add("7")
        expected.add("8")
        heap.remove("2")
        heap.add("2")
        heap.changeScore("2", 3)
        assertEquals(heap.topTen(), expected)
    }

    @Test
    fun `remove a lot`() {
        val expected = LinkedList<String>()
        expected.add("1")
        expected.add("4")
        expected.add("10")
        expected.add("3")
        expected.add("5")
        heap.remove("2")
        heap.remove("6")
        heap.remove("7")
        heap.remove("8")
        heap.remove("9")
        heap.remove("11")
        heap.remove("12")
        assertEquals(heap.topTen(), expected)
    }

    @Test
    fun `increase score`() {
        val expected = LinkedList<String>()
        expected.add("7")
        expected.add("1")
        expected.add("2")
        expected.add("4")
        expected.add("6")
        expected.add("10")
        expected.add("9")
        expected.add("12")
        expected.add("3")
        expected.add("5")
        heap.changeScore("7", 5)
        heap.changeScore("6", 2)
        heap.changeScore("12", 1)
        assertEquals(heap.topTen(), expected)
    }

    @Test
    fun `decrease score`() {
        val expected = LinkedList<String>()
        expected.add("4")
        expected.add("1")
        expected.add("9")
        expected.add("10")
        expected.add("2")
        expected.add("3")
        expected.add("5")
        expected.add("6")
        expected.add("7")
        expected.add("8")
        heap.changeScore("1", (-1))
        heap.changeScore("2", (-3))
        heap.changeScore("10", (-1))
        assertEquals(heap.topTen(), expected)
    }

    @Test
    fun `add a lot`() {
        for (i in 13..100000) {
            heap.add(i.toString())
        }
        val expected = LinkedList<String>()
        expected.add("4")
        expected.add("1")
        expected.add("9")
        expected.add("10")
        expected.add("2")
        expected.add("3")
        expected.add("5")
        expected.add("6")
        expected.add("7")
        expected.add("8")
        heap.changeScore("1", (-1))
        heap.changeScore("2", (-3))
        heap.changeScore("10", (-1))
        heap.changeScore("13", (1))
        heap.changeScore("24", (1))
        heap.changeScore("1000000", (1))
        heap.changeScore("13", (-1))
        heap.changeScore("24", (-1))
        heap.changeScore("1000000", (-1))
        heap.changeScore("13", (1))
        heap.changeScore("24", (1))
        heap.changeScore("100", (1))
        heap.changeScore("13", (-1))
        heap.changeScore("24", (-1))
        heap.changeScore("100", (-1))
        heap.remove("99")
        assertEquals(heap.topTen(), expected)
    }


    @Test
    fun `big scores`() {
        for (i in 3000..3020) {
            heap.add(i.toString())
        }
        val expected = listOf(
                "3004",
                "3001",
                "3009",
                "3010",
                "3002",
                "3003",
                "3005",
                "3006",
                "3007",
                "3008")

        heap.changeScore("3009", 10000000)
        heap.changeScore("3005", 1000)
        heap.changeScore("3001", 100000000)
        heap.changeScore("3002", 100000)
        heap.changeScore("3008", 9)
        heap.changeScore("3010", 1000000)
        heap.changeScore("3004", 1000000000)
        heap.changeScore("3007", 10)
        heap.changeScore("3003", 10000)
        heap.changeScore("3006", 100)

        assertEquals(expected, heap.topTen())
    }

    @Test
    fun `restoring list`() {
        var id = ""
        if (true) {
            val lib = factory.newMaxHeap()
            id = lib.getId()
            lib.add("world")
            lib.changeScore("world", 3)
            lib.add("hello")
            lib.changeScore("hello", 50)
            lib.add("admin")
            lib.changeScore("admin", 20)
        }
        val restored = factory.restoreMaxHeap(id)
        val expected = LinkedList<String>()
        expected.add("hello")
        expected.add("admin")
        expected.add("world")
        restored.changeScore("hello", 50)
        assertEquals(restored.topTen(), expected)
    }
}