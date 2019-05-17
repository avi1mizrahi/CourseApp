package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.*

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class HeapTest {
    private val storage = MockStorage()
    private val keyValueStore = KeyValueStoreImpl(storage)
    private val innerKeyValueStore = ScopedKeyValueStore(keyValueStore, listOf("Test"))
    private val heap = Heap(innerKeyValueStore, listOf("primary","%s"), listOf("secondary","%s"))


    @BeforeEach
    fun `Set up primary and secondary keys`() {
        for (i in 1..2048) {

            innerKeyValueStore.getIntReference(listOf("primary", i.toString())).write(i / 10)
            innerKeyValueStore.getIntReference(listOf("secondary", i.toString())).write((i % 10))
        }
    }

    @Test
    fun `IDIncremented works`() {
        for (i in 1..256)
            heap.add(i)

        innerKeyValueStore.getIntReference(listOf("primary", "50")).write(50000)
        heap.idIncremented(50)

        assert(heap.getTop10()[0] == 50)
    }

    @Test
    fun `IDDecremented works`() {
        for (i in 1..256)
            heap.add(i)

        innerKeyValueStore.getIntReference(listOf("primary", "256")).write(0)
        heap.idDecremented(256)

        assert(heap.getTop10()[0] == 255)
    }

    @Test
    fun `adds throws no assertion failures`() {
        for (i in 1..10)
            heap.add(i)

    }

    @Test
    fun `top10 with less than 10`() {
        for (i in 1..3)
            heap.add(i)

        val list = heap.getTop10()


        assert(list.size == 3)
        assert(list[0] == 3)
        assert(list[1] == 2)
        assert(list[2] == 1)


    }

    @Test
    fun `randomized entry with top10`() {
        repeat(5) {
            val a = ArrayList<Int>()
            for (i in 1..256)
                a.add(i)

            for (i in 1..256) {
                val entry = Random.nextInt(a.size)
                heap.add(a[entry])
                a.remove(a[entry])
            }


            val res = heap.getTop10()
            assert(res.size == 10)
            for (i in 247..256)
                assert(res[256 - i] == i)


            for (i in 1..256)
                heap.remove(i)
        }
    }

    @Test
    fun `added values exist`() {
        for (i in 1..3)
            heap.add(i)
        for (i in 7 downTo 4)
            heap.add(i)
        heap.add(9)
        heap.add(8)
        heap.add(10)
        for (i in 1..10)
            assert(heap.exists(i))

    }

    @Test
    fun `removed values don't exist`() {
        for (i in 1..3)
            heap.add(i)
        for (i in 7 downTo 5)
            heap.add(i)
        heap.add(9)
        heap.add(8)
        heap.add(10)
        heap.add(4)

        for (i in 1..10)
            assert(heap.exists(i))

        heap.remove(4)
        heap.remove(8)
        heap.remove(3)

        for (i in 1..2)
            assert(heap.exists(i))
        for (i in 5..7)
            assert(heap.exists(i))
        for (i in 9..10)
            assert(heap.exists(i))

    }
}