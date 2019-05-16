package il.ac.technion.cs.softwaredesign


private const val COUNT_IDENTIFIER = "count"
private const val INITIALIZED_IDENTIFIER = "initialized"

const val EXISTS_IDENTIFIER = "exists"
const val NODES_IDENTIFIER = "nodes"

abstract class DataStructure(DB: KeyValueStore, name : String) {

    protected var isInitialized = DB.getStringReference(listOf(name, INITIALIZED_IDENTIFIER))
    protected var count = DB.getIntReference(listOf(name, COUNT_IDENTIFIER))
    protected var cachedCount = -1
    init {
        initIfNotInitialized()
        if (cachedCount == -1) cachedCount = count.read()!!
    }

    protected fun setCount(c: Int)
    {
        count.write(c)
        cachedCount = c
    }


    fun count() : Int {
        return cachedCount
    }

    private fun initIfNotInitialized(){
        if (!getIsInitialized()) {
            setCount(0)
            setInitialized()
        }
    }

    private fun getIsInitialized() : Boolean {
        return isInitialized.read() != null
    }

    private fun setInitialized() {
        isInitialized.write("")
    }

    abstract fun exists(id: Int) : Boolean

}