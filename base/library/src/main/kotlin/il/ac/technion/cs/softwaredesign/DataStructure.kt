package il.ac.technion.cs.softwaredesign


private const val COUNT_IDENTIFIER = "count"
private const val INITIALIZED_IDENTIFIER = "initialized"

const val EXISTS_IDENTIFIER = "exists"
const val NODES_IDENTIFIER = "nodes"

abstract class DataStructure(private val DB: KeyValueStore, private val name : String) {

    protected var isInitializd = DB.getStringReference(listOf(name, INITIALIZED_IDENTIFIER))
    protected var count = DB.getIntReference(listOf(name, COUNT_IDENTIFIER))
    protected var cachedcount = -1
    init {
        initIfNotInitialized()
        if (cachedcount == -1) cachedcount = count.read()!!
    }

    protected fun setCount(c: Int)
    {
        count.write(c)
        cachedcount = c
    }


    fun count() : Int {
        return cachedcount
    }

    private fun initIfNotInitialized(){
        if (!getIsInitialized()) {
            setCount(0)
            setInitialized()
        }
    }

    private fun getIsInitialized() : Boolean {
        return isInitializd.read() != null
    }

    private fun setInitialized() {
        isInitializd.write("")
    }

    abstract fun exists(id: Int) : Boolean

}