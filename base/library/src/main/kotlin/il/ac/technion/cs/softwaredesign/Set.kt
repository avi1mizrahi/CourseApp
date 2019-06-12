package il.ac.technion.cs.softwaredesign

/** A Linked list implementation that uses the key-value DB.
 * O(1) find, add, and remove.
 * Allow to getAll
 * finding is O(1) because the nodes are named after the keys!
 * Duplicates will break the data structure.
 *
 *
**/

private const val PREVIOUS_IDENTIFIER = "previous"
private const val NEXT_IDENTIFIER = "next"
private const val FIRST_IDENTIFIER = "first"

class Set(private val DB: KeyValueStore) : DataStructure(DB) {

    private val first = DB.getIntReference(FIRST_IDENTIFIER)


    /**
     * Adds an item to the set. Duplicate additions will break the DB
     */
    fun add(id: Int) {
        //assert(!exists(id))

        setExists(id)

        val currentCount = count()
        if (currentCount == 0)
        {
            setFirst(id)
            setNext(id, null)
            setPrevious(id, null)
        }
        else
        {
            val oldFirst = getFirst()
            setFirst(id)
            setNext(id, oldFirst)
            setPrevious(id, null)

            if (oldFirst != null) setPrevious(oldFirst, id)
        }


        setCount(currentCount + 1)
    }

    /**
     * Removes an item from the set. Bad removals will break the DB
     */
    fun remove(id: Int)
    {
        //assert(exists(id))

        val currentCount = count()
        unsetExists(id)

        val prev = getPrevious(id)
        val next = getNext(id)

        // Update previous or first.
        if (prev == null) {// id was the first node, update first
            setFirst(next)
        }
        else { // there is a previous node
            setNext(prev, next)
        }

        // Update next to point to previous if exists
        if (next != null) {
            setPrevious(next, prev)
        }

        setCount(currentCount - 1)
    }


    /**
     * run an action on each of the items in the set
     */
    fun forEach(action: (Int) -> Unit) {
        var current = getFirst()
        while (current != null) {
            action(current)
            current = getNext(current)
        }
    }

    /**
     *  check if an item exists in the set
     */
    fun exists(id: Int) : Boolean {
        return DB.getStringReference(listOf(NODES_IDENTIFIER, id.toString(), EXISTS_IDENTIFIER)).read() != null
    }

    private fun setExists(id : Int)
    {
        DB.getStringReference(listOf(NODES_IDENTIFIER, id.toString(), EXISTS_IDENTIFIER)).write("")
    }

    private fun unsetExists(id : Int)
    {
        DB.getStringReference(listOf(NODES_IDENTIFIER, id.toString(), EXISTS_IDENTIFIER)).delete()
    }



    private fun setNext(id : Int, next : Int?)
    {
        val ref = DB.getIntReference(listOf(NODES_IDENTIFIER, id.toString(), NEXT_IDENTIFIER))
        if (next == null)
            ref.delete()
        else
            ref.write(next)
    }
    private fun getNext(id : Int) : Int?
    {
        return DB.getIntReference(listOf(NODES_IDENTIFIER, id.toString(), NEXT_IDENTIFIER)).read()
    }

    private fun setPrevious(id : Int, prev : Int?)
    {
        val ref = DB.getIntReference(listOf(NODES_IDENTIFIER, id.toString(), PREVIOUS_IDENTIFIER))
        if (prev == null)
            ref.delete()
        else
            ref.write(prev)
    }
    private fun getPrevious(id : Int) : Int?
    {
        return DB.getIntReference(listOf(NODES_IDENTIFIER, id.toString(), PREVIOUS_IDENTIFIER)).read()
    }

    private fun getFirst() : Int? {
        return first.read()
    }

    private fun setFirst(id : Int?){
        if (id == null)
            first.delete()
        else
            first.write(id)
    }
}