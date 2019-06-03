package il.ac.technion.cs.softwaredesign


class Array(map: KeyValueStore) {
    private val count = map.getIntReference("count")
    private val map = map.getIntMapReference("values")

    init {
        if (count.read() == null) count.write(0)
    }

    fun push(i: Int): Int {
        val position = count.read()!!
        map.write(position.toString(), i)
        count.write(position + 1)
        return position
    }

    operator fun get(i: Int): Int = map.read(i.toString())!!

    fun clear() = count.write(0)

    fun size(): Int = count.read() ?: 0

    fun forEach(action: (Int) -> Unit) {
        repeat(count.read()!!) {
            action(map.read(it.toString())!!)
        }
    }
}
