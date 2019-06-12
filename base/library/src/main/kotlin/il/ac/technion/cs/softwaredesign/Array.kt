package il.ac.technion.cs.softwaredesign

/**
 *  Int-only implementation of the below Array
 */
class ArrayInt(private val db: KeyValueStore) {

    private val internalArr = Array(db)
    fun push(value: Int) {
        val (DB, _) = internalArr.newSlot()
        DB.getIntReference("val").write(value)
    }
    operator fun get(i : Int) : Int? = internalArr[i]?.getIntReference("val")?.read()
    fun clear() = internalArr.clear()
    fun count() = internalArr.count()

    fun forEach(action: (Int) -> Unit) =
            internalArr.forEach { it->
                action(it.getIntReference("val").read()!!)
            }
    fun forEach(action: (Int, Int) -> Unit) =
            internalArr.forEach {
                p1, p2 ->
                action(p1.getIntReference("val").read()!!, p2)
            }


    fun forEachFrom(action: (Int) -> Unit, start:Int) =
            internalArr.forEachFrom ({ it->
                action(it.getIntReference("val").read()!!)
            } , start)
    fun forEachFrom(action: (Int, Int) -> Unit, start:Int) =
            internalArr.forEachFrom ({
                p1, p2 ->
                action(p1.getIntReference("val").read()!!, p2)
            }, start)
}


/**
 *     an array of unique scoped KeyValueStore that do not conflict with each other.
 *
 *     @param DB a scoped DB under which the further scopes will be made
 *     @constructor creates a new array
 *
 */
class Array(private val DB: KeyValueStore) : DataStructure(DB) {


    /**
     * Pushes a new "folder" on the DB and returns a pair of <ScopedKeyValueStore,Index>.
     */
    fun newSlot(): Pair<KeyValueStore,Int>  {
        val position = count()
        setCount(position + 1)
        return Pair(DB.scope(position.toString()), position)
    }


    /**
     * get the scoped DB at the given index or return null
     * @param i index of the item
     */
    operator fun get(i: Int): KeyValueStore? {
        val size = count()
        if (i < 0 || i >= size) return null
        return DB.scope(i.toString())
    }

    /**
     * clear the DB.
     * NOTE: the "folders" are not erased
     * any new data structures underneath must initialize properly and override all existing data
     */
    fun clear() = setCount(0)


    /**
     * Run an action on all scoped DBs
     */
    fun forEach(action: (KeyValueStore) -> Unit) {
        repeat( count()) {
            action(get(it)!!)
        }
    }


    /**
     * Run an action on all scoped DBs and their index
     */
    fun forEach(action: (KeyValueStore, Int) -> Unit) {
        repeat( count()) {
            action(get(it)!!, it)
        }
    }


    /**
     * Run an action on all scoped DBs from a specific index
     */
    fun forEachFrom(action: (KeyValueStore) -> Unit, start: Int) {
        for (it in start until count()) {
            action(get(it)!!)
        }
    }

    /**
     * Run an action on all scoped DBs and their index from a specific index
     */
    fun forEachFrom(action: (KeyValueStore, Int) -> Unit, start: Int) {
        for (it in start until count()) {
            action(get(it)!!, it)
        }
    }
}
