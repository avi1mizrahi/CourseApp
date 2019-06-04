package il.ac.technion.cs.softwaredesign.dataTypeProxies

import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.Set
import il.ac.technion.cs.softwaredesign.exceptions.NameFormatException
import il.ac.technion.cs.softwaredesign.dataTypeProxies.UserManager.User
import org.junit.jupiter.api.Assertions.assertEquals





// Valid names for channels start with `#`, then have any number of English alphanumeric characters, underscores
// (`_`) and hashes (`#`).
private fun isBadChannelName(name : String) : Boolean {
    val alphanumeric = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    if (!name.startsWith("#"))
        return true

    if (name.drop(1).any { char -> !alphanumeric.contains(char)})
        return true
    return false
}


class ChannelManager(private val DB: KeyValueStore) {

    private val allChannels = Array(ScopedKeyValueStore(DB, listOf("channels", "allChannels")))
    private val nameToId = DB.getIntMapReference(listOf("channels", "nameToId"))

    private val allChannelsByUserCount = Heap(ScopedKeyValueStore(DB, listOf("ChannelsByUserCount")),
            {id -> getChannelById(id).getUserCount()},
            {id -> -id})

    private val allChannelsByActiveCount = Heap(ScopedKeyValueStore(DB, listOf("ChannelsByActiveUserCount")),
            {id -> getChannelById(id).getActiveCount()},
            {id -> -id})


    init {
        allChannelsByUserCount.initialize()
        allChannelsByActiveCount.initialize()
    }



    fun getTop10ChannelsByUserCount() : List<String> {
        return allChannelsByUserCount.getTop10().map { id -> getChannelById(id).getName() }
    }

    fun getTop10ChannelsByActiveUserCount() : List<String> {
        return allChannelsByActiveCount.getTop10().map { id -> getChannelById(id).getName() }
    }

    fun createNewChannel(name : String) : Channel {
        assert(getChannelByName(name) == null)


        val (channelDB, id) = allChannels.newSlot()
        val channel = Channel(channelDB, id)

        channel.initialize(name)


        nameToId.write(name, id)
        allChannelsByUserCount.add(id)
        allChannelsByActiveCount.add(id)

        return channel
    }

    fun throwIfBadChannelName(name : String) {
        if (isBadChannelName(name)) throw NameFormatException()
    }

    fun getChannelByName(name : String) : Channel? {
        val channelID = nameToId.read(name) ?: return null
        return Channel(allChannels[channelID]!!, channelID)
    }


    fun getChannelById(channelID : Int) : Channel {
        return Channel(allChannels[channelID]!!, channelID)
    }

//
//    fun channelExists(name : String) : Boolean {
//        return allChannels.read(name) != null
//    }

    fun removeChannel(c : Channel) {
        assert(c.getUserCount() == 0)

        nameToId.delete(c.getName())
        allChannelsByUserCount.remove(c.getID())
        allChannelsByActiveCount.remove(c.getID())
    }

    inner class Channel(DB: ScopedKeyValueStore, private val id: Int) {

        private val userList = Set(ScopedKeyValueStore(DB, listOf("users")))
        private val activeList = Set(ScopedKeyValueStore(DB, listOf("activeUsers")))
        private val operatorList = Set(ScopedKeyValueStore(DB, listOf("operators")))
        private val name = DB.getStringReference("name")

        fun initialize(s : String) {
            name.write(s)
            userList.initialize()
            activeList.initialize()
            operatorList.initialize()
        }


        fun getID() = id


        fun getName() = name.read()!!

        fun getUserCount() : Int {
            return userList.count()
        }
        fun getActiveCount() : Int {
            return activeList.count()
        }

        fun addOp(user : User) {
            assert(!isOp(user))
            operatorList.add(user.id())
        }

        fun isOp(user : User) : Boolean = operatorList.exists(user.id())


        fun addActive(user : User) {
            assert(!isActive(user))
            activeList.add(user.id())
            allChannelsByActiveCount.idIncremented(id)
        }

        fun removeActive(user : User) {
            assert(isActive(user))
            activeList.remove(user.id())
            allChannelsByActiveCount.idDecremented(id)
        }

        private fun isActive(user : User) : Boolean = activeList.exists(user.id())

        fun addUser(user : User) {
            assert(!hasUser(user))
            val userid = user.id()

            userList.add(userid)
            allChannelsByUserCount.idIncremented(id)

            if (user.isLoggedIn()) {
                activeList.add(userid)
                allChannelsByActiveCount.idIncremented(id)
            }

        }

        fun removeUser(user : User) {
            assert(hasUser(user))
            val userid = user.id()

            userList.remove(userid)
            allChannelsByUserCount.idDecremented(id)
            operatorList.remove(userid)

            assertEquals(activeList.exists(userid), user.isLoggedIn())
            if (user.isLoggedIn())  {
                activeList.remove(userid)
                allChannelsByActiveCount.idDecremented(id)
            }

            if (getUserCount() == 0) {
                removeChannel(this)
            }

        }

        fun hasUser(user : User) : Boolean = userList.exists(user.id())

    }

}

