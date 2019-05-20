package il.ac.technion.cs.softwaredesign


private const val COUNT_IDENTIFIER = "count"
private const val INITIALIZED_IDENTIFIER = "initialized"

const val EXISTS_IDENTIFIER = "exists"
const val NODES_IDENTIFIER = "nodes"

abstract class DataStructure(DB: KeyValueStore) {

    private var initialized = DB.getStringReference(INITIALIZED_IDENTIFIER)
    private var count = DB.getIntReference(COUNT_IDENTIFIER)

    fun initialize() {
        if (!isInitialized()) {
            setCount(0)
            setInitialized()
        }
    }

    private fun isInitialized(): Boolean = initialized.read() != null

    private fun setInitialized() = initialized.write("")

    fun setCount(c: Int) = count.write(c)

    fun count(): Int = count.read()!!

    abstract fun exists(id: Int): Boolean
}