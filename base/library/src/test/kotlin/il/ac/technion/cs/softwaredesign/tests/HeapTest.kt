package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.*
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test
import kotlin.random.Random

class HeapTest {
    private val heap = Heap(VolatileKeyValueStore(), { id -> primary(id)}, { id -> id % 10})

    private var getPrimaryOverride : Function1<Int, Int>? = null
    private fun primary(id : Int) : Int {
        getPrimaryOverride ?: return id / 10

        return getPrimaryOverride!!.invoke(id)
    }

    @BeforeEach
    fun `init`() {
        heap.initialize()
    }

    @Test
    fun `IDIncremented works`() {
        for (i in 1..256)
            heap.add(i)

        fun get(id : Int) : Int {
            return if (id == 50) 500000
            else id / 10
        }
        getPrimaryOverride = { id -> get(id)}

        heap.idIncremented(50)

        assert(heap.getTop10()[0] == 50)
    }

    @Test
    fun `IDDecremented works`() {
        for (i in 1..256)
            heap.add(i)

        fun get(id : Int) : Int {
            return if (id == 256) 0
            else id / 10
        }
        getPrimaryOverride = { id -> get(id)}
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
