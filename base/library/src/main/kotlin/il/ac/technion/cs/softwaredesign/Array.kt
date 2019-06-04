package il.ac.technion.cs.softwaredesign


class Array(private val map: KeyValueStore) {
    private val count = map.getIntReference("count")

    init {
        if (count.read() == null) count.write(0)
    }

    // Create a new slot
    fun newSlot(): Pair<ScopedKeyValueStore,Int>  {
        val position = count.read()!!
        count.write(position + 1)
        return Pair(ScopedKeyValueStore(map, listOf(position.toString())), position)
    }

    operator fun get(i: Int): ScopedKeyValueStore? {
        if (i < 0 || i >= count.read()!!) return null
        return ScopedKeyValueStore(map, listOf(i.toString()))
    }

    fun clear() = count.write(0)

    fun size(): Int = count.read() ?: 0

    fun forEach(action: (ScopedKeyValueStore) -> Unit) {
        repeat(count.read()!!) {
            action(get(it)!!)
        }
    }
}
