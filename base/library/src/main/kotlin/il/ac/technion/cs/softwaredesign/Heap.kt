package il.ac.technion.cs.softwaredesign

private const val OBJECTS_IDENTIFIER = "objects"

class Heap(private val DB: KeyValueStore, private val name : String,
           private val PrimaryKeySource: List<String>, private val SecondaryKeySource: List<String>) :
        DataStructure(DB, name){


//$name/Count -> int32
//$name/nodes/$index-> int32 (id)
//$name/objects/$id -> int32 (index)
//Throughout the whole file, Index refers to node. id refers to object
//Nodes are Implemented as an Array.


    fun add(id: Int) {
        assert(!exists(id))

        val index = cachedcount
        updateNode(index, id)
        setCount(index + 1)
        pushUp(index, id)
    }

    fun remove(id: Int){
        assert(exists(id))

        var currentIndex = getObjectsNode(id)!!

        // push node to root. don't bother updating it
        while (currentIndex != 0) {
            val parentIndex = getParent(currentIndex)!!
            val parentID = getNodesObject(parentIndex)!!
            updateNode(currentIndex, parentID)
            currentIndex = parentIndex
        }

        var replacementNodeIndex = cachedcount - 1
        var replacementNodeID = getNodesObject(replacementNodeIndex)!!

        updateNode(0, replacementNodeID)
        setCount(cachedcount - 1)
        pushDown(0, replacementNodeID)


        deleteObjectsNode(id)
    }

    fun getTop10() : List<Int> {
        val ret = ArrayList<Int>()
        if (cachedcount == 0)
            return ret

        // Index -> ID
        var candidates = HashMap<Int, Int>()
        // ID -> Index
        var candidatesReversed = HashMap<Int, Int>()
        // ID -> PrimaryKey, SecondaryKey
        var keycache = HashMap<Int, Pair<Int, String>>()
        fun addCandidate(index: Int?) {
            index ?: return

            if (candidates.containsKey(index)) return

            var id = getNodesObject(index)!!
            var p = getPrimaryKey(id)
            var s = getSecondaryKey(id)

            candidates[index] = id
            candidatesReversed[id] = index
            keycache[id] = Pair(p,s)
        }
        fun removeCandidate(index : Int) {
            keycache.remove(candidates[index])
            candidatesReversed.remove(candidates[index])
            candidates.remove(index)
        }



        addCandidate(0)
        repeat (10) {
            if (candidates.count() == 0) // no more candidates
                return ret

            // Find largest out of candidates
            val maxentry = keycache.maxWith(Comparator({p1,p2 -> comparePair(p1.value,p2.value)}))!!
            val maxid = maxentry.key
            val maxindex = candidatesReversed[maxid]!!

            // add to output, remove from candidates
            ret.add(maxid)
            removeCandidate(maxindex)

            // add children
            addCandidate(getLeft(maxindex))
            addCandidate(getRight(maxindex))
        }

        return ret


    }

    fun IDIncremented(id : Int) {
        pushUp(getObjectsNode(id)!!, id)
    }

    fun IDDecremented(id : Int) {
        pushDown(getObjectsNode(id)!!, id)
    }



    private fun pushDown(index : Int, id: Int) {
        val primary = getPrimaryKey(id)
        val secondary = getSecondaryKey(id)

        var currentIndex = index

        while (true) {
            var leftIndex = getLeft(currentIndex)
            var rightIndex = getRight(currentIndex)
            var leftID: Int? = null
            var rightID: Int? = null

            if (leftIndex != null) leftID = getNodesObject(leftIndex)!!
            if (rightIndex != null) rightID = getNodesObject(rightIndex)!!


            var swapwithIndex : Int? = null
            var swapwithID : Int? = null

            if (leftIndex != null && rightIndex != null) {
                var largest = findLargest(primary, secondary, leftID!!, rightID!!)
                if (largest == 1) return // parent bigger than both sons
                if (largest == 2) {
                    swapwithIndex = leftIndex
                    swapwithID = leftID
                }
                if (largest == 3) {
                    swapwithIndex = rightIndex
                    swapwithID = rightID
                }
            }
            else if (leftIndex == null && rightIndex == null) return
            else {
                if (leftIndex != null) {
                    if (isLargerThanID(primary, secondary, leftID!!)) return // bigger than only son

                    swapwithIndex = leftIndex
                    swapwithID = leftID
                }
                else if (rightIndex != null) {
                    if (isLargerThanID(primary, secondary, rightID!!)) return
                    swapwithIndex = rightIndex
                    swapwithID = rightID

                }
            }
            updateNode(swapwithIndex!!, id)
            updateNode(currentIndex, swapwithID!!)
            currentIndex = swapwithIndex

        }
    }

    private fun pushUp(index : Int, id: Int) {
        val primary = getPrimaryKey(id)
        val secondary = getSecondaryKey(id)

        var currentIndex = index

        var parentIndex = getParent(currentIndex) ?: return
        var parentID = getNodesObject(parentIndex)!!

        while (isLargerThanID(primary, secondary, parentID)) {
            updateNode(parentIndex, id)
            updateNode(currentIndex, parentID)

            currentIndex = parentIndex
            parentIndex = getParent(currentIndex) ?: return
            parentID = getNodesObject(parentIndex)!!

        }

    }


    private fun getLeft(index : Int) : Int? {
        var left = index * 2 + 1
        if (left >= cachedcount) return null
        return left
    }
    private fun getRight(index : Int) : Int? {
        var right = index * 2 + 2
        if (right >= cachedcount) return null
        return right
    }
    private fun getParent(index : Int) : Int? {
        if (index == 0) return null
        return (index - 1) / 2
    }


    private fun updateNode(index: Int, id : Int) {
        setNodesObject(index, id)
        setObjectsNode(id, index)
    }


    private fun getObjectsNode(id: Int) : Int? {
        return DB.read_int32(listOf(name, OBJECTS_IDENTIFIER, id.toString()))
    }
    private fun setObjectsNode(id: Int, index: Int){
        DB.write_int32(listOf(name, OBJECTS_IDENTIFIER, id.toString()), index)
    }
    private fun deleteObjectsNode(id: Int){
        DB.delete_int32(listOf(name, OBJECTS_IDENTIFIER, id.toString()))
    }

    private fun getNodesObject(index: Int) : Int? {
        return DB.read_int32(listOf(name, NODES_IDENTIFIER, index.toString()))
    }
    private fun setNodesObject(index: Int, id: Int) {
        DB.write_int32(listOf(name, NODES_IDENTIFIER, index.toString()), id)
    }



    private fun comparePair(pair1 : Pair<Int, String>, pair2: Pair<Int, String>) : Int {
        isPrimaryKeyLarger(pair1.first, pair2.first) ?: return if (isSecondaryKeyLarger(pair1.second, pair2.second)) 1 else -1
        return if (isPrimaryKeyLarger(pair1.first, pair2.first)!!) 1 else -1
    }


    // find largest between 3 keys. First key is provided and keys of id1,id2 keys will be fetched as needed.
    // secondary keys will only be fetched if needed
    private fun findLargest(p1 : Int, s1 : String, id2 : Int, id3 : Int) : Int {
        var p2 = getPrimaryKey(id2)
        var p3 = getPrimaryKey(id3)
        var s2 : String? = null
        var s3 : String? = null


        var res = isPrimaryKeyLarger(p1, p2)
        if (res == null)
        {
            s2 = getSecondaryKey(id2)
            res = isSecondaryKeyLarger(s1, s2)
        }

        // id1 is larger
        if (res) {
            res = isPrimaryKeyLarger(p1, p3)
            if (res == null)
            {
                s3= getSecondaryKey(id3)
                res = isSecondaryKeyLarger(s1, s3)
            }

            return if (res) 1 else 3

        }
        // id2 is larger
        else {
            res = isPrimaryKeyLarger(p2, p3)
            if (res == null)
            {
                s3 = getSecondaryKey(id3)
                if (s2 == null) s2 = getSecondaryKey(id2)
                res = isSecondaryKeyLarger(s2, s3)
            }

            return if (res) 2 else 3
        }
    }

    private fun isLargerThanID(primary : Int, secondary : String, otherid : Int) : Boolean {
        return isPrimaryKeyLarger(primary, getPrimaryKey(otherid))
                ?: return isSecondaryKeyLarger(secondary, getSecondaryKey(otherid))
    }

//    private fun isLargerThanOtherKey(p1 : Int, s1 : String, p2 : Int, s2 : String) : Boolean {
//        return isPrimaryKeyLarger(p1, p2)
//                ?: return isSecondaryKeyLarger(s1, s2)
//    }

    private fun isPrimaryKeyLarger(p1 : Int, p2: Int) : Boolean? {
        if (p1 > p2) return true
        if (p1 < p2) return false
        return null
    }

    private fun isSecondaryKeyLarger(s1 : String, s2: String) : Boolean {
        return (s1 > s2)
    }




    // TODO maybe generalize key types later
    private fun getPrimaryKey(id: Int) : Int {
        var query = ArrayList(PrimaryKeySource)
        query[query.indexOf("%s")] = id.toString()

        return DB.read_int32(query)!!

    }
    private fun getSecondaryKey(id: Int) : String {
        var query = ArrayList(SecondaryKeySource)
        query[query.indexOf("%s")] = id.toString()
        return DB.read(query)!!
    }


    override fun exists(id: Int) : Boolean {
        return getObjectsNode(id) != null
    }


}