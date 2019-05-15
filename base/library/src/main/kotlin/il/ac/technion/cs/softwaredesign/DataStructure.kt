package il.ac.technion.cs.softwaredesign


private const val COUNT_IDENTIFIER = "count"
private const val INITIALIZED_IDENTIFIER = "initialized"

const val EXISTS_IDENTIFIER = "exists"
const val NODES_IDENTIFIER = "nodes"

abstract class DataStructure(private val DB: KeyValueStore, private val name : String) {

    protected var cachedcount = 0
    init {
        initIfNotInitialized()

        cachedcount = count()
    }

    protected fun setCount(count: Int)
    {
        DB.writeInt32(listOf(name, COUNT_IDENTIFIER), count)
        cachedcount = count
    }

    fun count() : Int {
        return DB.readInt32(listOf(name, COUNT_IDENTIFIER))!!
    }

    private fun initIfNotInitialized(){
        if (!getIsInitialized()) {
            setCount(0)
            setInitialized()
        }
    }

    private fun getIsInitialized() : Boolean {
        return DB.read(listOf(name, INITIALIZED_IDENTIFIER)) != null
    }

    private fun setInitialized() {
        DB.write(listOf(name, INITIALIZED_IDENTIFIER), "")
    }



    abstract fun exists(id: Int) : Boolean

}