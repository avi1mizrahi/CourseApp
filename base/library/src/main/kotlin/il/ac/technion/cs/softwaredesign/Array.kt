package il.ac.technion.cs.softwaredesign


class ArrayInt(private val db: KeyValueStore) {

    private val internalArr = Array(db)
    fun push(value: Int) {
        val (DB, _) = internalArr.newSlot()
        DB.getIntReference("val").write(value)
    }
    operator fun get(i : Int) : Int? = internalArr[i]?.getIntReference("val")?.read()
    fun clear() = internalArr.clear()
    fun size() = internalArr.size()

    fun forEach(action: (Int) -> Unit) =
            internalArr.forEach { it->
                action(it.getIntReference("val").read()!!)
            }
    fun forEach(action: (Int, Int) -> Unit) =
            internalArr.forEach {
                p1, p2 ->
                action(p1.getIntReference("val").read()!!, p2)
            }
}

class Array(private val map: KeyValueStore) {
    private val count = map.getIntReference("count")

    // Create a new slot
    fun newSlot(): Pair<ScopedKeyValueStore,Int>  {
        val position = size()
        count.write(position + 1)
        return Pair(ScopedKeyValueStore(map, listOf(position.toString())), position)
    }

    operator fun get(i: Int): ScopedKeyValueStore? {
        if (i < 0 || i >= size()) return null
        return ScopedKeyValueStore(map, listOf(i.toString()))
    }

    fun clear() = count.write(0)

    fun size(): Int = count.read() ?: 0

    fun forEach(action: (ScopedKeyValueStore) -> Unit) {
        repeat( size()) {
            action(get(it)!!)
        }
    }

    fun forEach(action: (ScopedKeyValueStore, Int) -> Unit) {
        repeat( size()) {
            action(get(it)!!, it)
        }
    }
}
