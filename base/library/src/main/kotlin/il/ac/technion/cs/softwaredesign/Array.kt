package il.ac.technion.cs.softwaredesign


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


class Array(private val DB: KeyValueStore) : DataStructure(DB) {
    // Create a new slot
    fun newSlot(): Pair<KeyValueStore,Int>  {
        val position = count()
        setCount(position + 1)
        return Pair(DB.scope(position.toString()), position)
    }

    operator fun get(i: Int): KeyValueStore? {
        val size = count()
        if (i < 0 || i >= size) return null
        return DB.scope(i.toString())
    }

    fun clear() = setCount(0)

    fun forEach(action: (KeyValueStore) -> Unit) {
        repeat( count()) {
            action(get(it)!!)
        }
    }

    fun forEach(action: (KeyValueStore, Int) -> Unit) {
        repeat( count()) {
            action(get(it)!!, it)
        }
    }

    fun forEachFrom(action: (KeyValueStore) -> Unit, start: Int) {
        for (it in start until count()) {
            action(get(it)!!)
        }
    }

    fun forEachFrom(action: (KeyValueStore, Int) -> Unit, start: Int) {
        for (it in start until count()) {
            action(get(it)!!, it)
        }
    }
}
