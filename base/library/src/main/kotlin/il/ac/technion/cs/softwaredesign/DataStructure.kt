package il.ac.technion.cs.softwaredesign


private const val COUNT_IDENTIFIER = "count"
private const val INITIALIZED_IDENTIFIER = "initialized"

const val EXISTS_IDENTIFIER = "exists"
const val NODES_IDENTIFIER = "nodes"

abstract class DataStructure(DB: KeyValueStore) {

    private var isInitialized = DB.getStringReference(INITIALIZED_IDENTIFIER)
    private var count = DB.getIntReference(COUNT_IDENTIFIER)

    protected fun setCount(c: Int)
    {
        count.write(c)
    }

    fun initialize() {
        if (!getIsInitialized()) {
            setCount(0)
            setInitialized()
        }
    }

    fun count() : Int {
        return count.read()!!
    }

    private fun getIsInitialized() : Boolean {
        return isInitialized.read() != null
    }

    private fun setInitialized() {
        isInitialized.write("")
    }

    abstract fun exists(id: Int) : Boolean

}