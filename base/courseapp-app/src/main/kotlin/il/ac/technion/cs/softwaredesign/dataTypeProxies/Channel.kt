package il.ac.technion.cs.softwaredesign.dataTypeProxies

import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.Set
import il.ac.technion.cs.softwaredesign.exceptions.NameFormatException
import il.ac.technion.cs.softwaredesign.dataTypeProxies.UserManager.User
import org.junit.jupiter.api.Assertions.assertEquals

private const val CHANNELSTATS_IDENTIFIER = "usersstats"
private const val COUNT_IDENTIFIER = "count"
private const val INDEXCOUNTER_IDENTIFIER = "indexCounter"
private const val ALLCHANNELS_IDENTIFIER = "allchannels"



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

    var indexCounter = DB.getIntReference(listOf(CHANNELSTATS_IDENTIFIER, INDEXCOUNTER_IDENTIFIER))
    //var count = DB.getIntReference(listOf(CHANNELSTATS_IDENTIFIER, COUNT_IDENTIFIER))

    // Channel name -> index
    var allChannels = DB.getIntMapReference(listOf(CHANNELSTATS_IDENTIFIER, ALLCHANNELS_IDENTIFIER))

    init {
        if (indexCounter.read() == null)
            indexCounter.write(0)
    }


    fun createNewChannel(name : String) : Channel {
        assert(getChannelByName(name) == null)

        val id = indexCounter.read()!!
        indexCounter.write(id + 1)


        allChannels.write(name, id)

        val channel = Channel(ScopedKeyValueStore(DB, listOf(id.toString())), id)
        channel.setName(name)
        return channel
    }

    fun throwIfBadChannelName(name : String) {
        if (isBadChannelName(name)) throw NameFormatException()
    }

    fun getChannelByName(name : String) : Channel? {
        val channelID = allChannels.read(name) ?: return null
        return Channel(ScopedKeyValueStore(DB, listOf(channelID.toString())), channelID)
    }


    fun getChannelById(channelID : Int) : Channel {
        return Channel(ScopedKeyValueStore(DB, listOf(channelID.toString())), channelID)
    }


    fun channelExists(name : String) : Boolean {
        return allChannels.read(name) != null
    }

    fun removeChannel(c : Channel) {
        assert(c.getUserCount() == 0)

        allChannels.delete(c.getName())
    }

    inner class Channel(private val DB: ScopedKeyValueStore, private val id: Int) {

        private val userList = Set(ScopedKeyValueStore(DB, listOf("users")))
        private val activeList = Set(ScopedKeyValueStore(DB, listOf("activeUsers")))
        private val operatorList = Set(ScopedKeyValueStore(DB, listOf("operators")))
        private val name = DB.getStringReference("name")

        fun getID() = id

        internal fun setName(s : String) = name.write(s)

        fun getName() = name.read()!!

        fun getUserCount() : Int {
            return userList.count()
        }
        fun getActiveCount() : Int {
            return activeList.count()
        }

        fun addOp(user : User) {
            assert(!isOp(user))
            operatorList.add(user.getID())
        }

        fun removeOp(user : User) {
            assert(isOp(user))
            operatorList.remove(user.getID())
        }

        fun isOp(user : User) : Boolean = operatorList.exists(user.getID())


        fun addActive(user : User) {
            assert(!isActive(user))
            activeList.add(user.getID())
        }

        fun removeActive(user : User) {
            assert(isActive(user))
            activeList.remove(user.getID())
        }

        fun isActive(user : User) : Boolean = activeList.exists(user.getID())

        fun addUser(user : User) {
            assert(!hasUser(user))
            val id = user.getID()
            userList.add(id)

            if (user.isLoggedIn())
                activeList.add(id)

        }

        fun removeUser(user : User) {
            assert(hasUser(user))
            val id = user.getID()
            userList.remove(id)


            assertEquals(activeList.exists(id), user.isLoggedIn())
            if (user.isLoggedIn()) activeList.remove(id)

            if (getUserCount() == 0)
                allChannels.delete(this.getName())

        }

        fun hasUser(user : User) : Boolean = userList.exists(user.getID())

    }

}

