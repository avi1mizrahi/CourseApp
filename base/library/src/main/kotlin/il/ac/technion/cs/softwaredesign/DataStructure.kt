package il.ac.technion.cs.softwaredesign


private const val COUNT_IDENTIFIER = "count"
private const val INITIALIZED_IDENTIFIER = "initialized"

const val EXISTS_IDENTIFIER = "exists"
const val NODES_IDENTIFIER = "nodes"

abstract class DataStructure(DB: KeyValueStore) {

    private var count = DB.getIntReference(COUNT_IDENTIFIER)
    private var cachedCount : Int = -1

    fun setCount(c: Int) {
        count.write(c)
        cachedCount = c
    }

    fun count(): Int {
        if (cachedCount == -1)
            cachedCount = count.read() ?: 0

        return cachedCount
    }

    abstract fun exists(id: Int): Boolean
}