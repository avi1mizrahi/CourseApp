package il.ac.technion.cs.softwaredesign


private const val COUNT_IDENTIFIER = "count"
private const val INITIALIZED_IDENTIFIER = "initialized"

const val EXISTS_IDENTIFIER = "exists"
const val NODES_IDENTIFIER = "nodes"

abstract class DataStructure(DB: KeyValueStore) {

    private var count = DB.getIntReference(COUNT_IDENTIFIER)


    fun setCount(c: Int) = count.write(c)

    fun count(): Int = count.read() ?: 0

    abstract fun exists(id: Int): Boolean
}