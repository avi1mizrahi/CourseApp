package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.KeyValueStore

// A Linked list implementation that uses the key-value DB.
// O(1) find, add, and remove.
// Allow to getAll
// finding is O(1) because the nodes are named after the keys!
// Works as a set, no duplicates.

//$ListName/Count -> int32
//$ListName/First -> int32
//$ListName/nodes/$id/Exists -> bool
//$ListName/nodes/$id/Previous -> int32
//$ListName/nodes/$id/Next -> int32

private const val NODES_IDENTIFIER = "nodes"
private const val EXISTS_IDENTIFIER = "exists"
private const val COUNT_IDENTIFIER = "count"
private const val FIRST_IDENTIFIER = "first"
private const val PREVIOUS_IDENTIFIER = "previous"
private const val NEXT_IDENTIFIER = "next"
private const val NULL_NODE_ID = -1

class Set(private val DB: KeyValueStore, private val TreeName : String) {

    fun exists(id: Int) : Boolean {
        return DB.read(listOf(TreeName, NODES_IDENTIFIER, id.toString(), EXISTS_IDENTIFIER)) != null
    }

    fun count() : Int {
        return DB.read_int32(listOf(TreeName, COUNT_IDENTIFIER))
    }

    fun add(id: Int) {
        if (exists(id)) return


        setExists(id)

        val currentCount = count()
        if (currentCount == 0)
        {
            setFirst(id)
            setNext(id, NULL_NODE_ID)
            setPrevious(id, NULL_NODE_ID)
        }
        else
        {
            val oldFirst = getFirst()
            setFirst(id)
            setNext(id, oldFirst)
            setPrevious(id, NULL_NODE_ID)

            setPrevious(oldFirst, id)
        }


        setCount(currentCount + 1)
    }

    fun remove(id: Int)
    {
        if (!exists(id)) return

        val currentCount = count()
        unsetExists(id)

        val prev = getPrevious(id)
        val next = getNext(id)

        // Update previous or first.
        if (prev == NULL_NODE_ID) {// id was the first node, update first
            setFirst(next)
        }
        else { // there is a previous node
            setNext(prev, next)
        }

        // Update next to point to previous if exists
        if (next != NULL_NODE_ID) {
            setPrevious(next, prev)
        }

        setCount(currentCount - 1)
    }


    fun getAll() : List<Int> {

        var out = ArrayList<Int>()

        var current = getFirst()
        while (current != NULL_NODE_ID)
        {
            out.add(current)
            current = getNext(current)
        }
        return out
    }


    private fun setCount(count: Int)
    {
        DB.write_int32(listOf(TreeName, COUNT_IDENTIFIER), count)
    }

    private fun setNext(id : Int, next : Int)
    {
        DB.write_int32(listOf(TreeName, NODES_IDENTIFIER, id.toString(), NEXT_IDENTIFIER), next)
    }
    private fun getNext(id : Int) : Int
    {
        return DB.read_int32(listOf(TreeName, NODES_IDENTIFIER, id.toString(), NEXT_IDENTIFIER))
    }

    private fun setPrevious(id : Int, prev : Int)
    {
        DB.write_int32(listOf(TreeName, NODES_IDENTIFIER, id.toString(), PREVIOUS_IDENTIFIER), prev)
    }
    private fun getPrevious(id : Int) : Int
    {
        return DB.read_int32(listOf(TreeName, NODES_IDENTIFIER, id.toString(), PREVIOUS_IDENTIFIER))
    }

    private fun setExists(id : Int)
    {
        DB.write(listOf(TreeName, NODES_IDENTIFIER, id.toString(), EXISTS_IDENTIFIER), "")
    }

    private fun unsetExists(id : Int)
    {
        DB.delete(listOf(TreeName, NODES_IDENTIFIER, id.toString(), EXISTS_IDENTIFIER))
    }


    private fun getFirst() : Int {
        return DB.read_int32(listOf(TreeName, FIRST_IDENTIFIER))
    }

    private fun setFirst(id : Int){
        DB.write_int32(listOf(TreeName, FIRST_IDENTIFIER), id)
    }

}