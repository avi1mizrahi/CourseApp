package il.ac.technion.cs.softwaredesign.dataTypeProxies

import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.Set
import il.ac.technion.cs.softwaredesign.exceptions.NameFormatException
import il.ac.technion.cs.softwaredesign.dataTypeProxies.UserManager.User
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.concurrent.CompletableFuture


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


class ChannelManager(DB: KeyValueStore) {

    private val allChannels = Array(ScopedKeyValueStore(DB, listOf("allChannels")))
    private val nameToId = DB.getIntMapReference(listOf("nameToId"))

    private val statistics_allChannelsByUserCount = Heap(ScopedKeyValueStore(DB, listOf("ChannelsByUserCount")),
            {id -> getChannelById(id).getUserCount()},
            {id -> -id})

    private val statistics_allChannelsByActiveCount = Heap(ScopedKeyValueStore(DB, listOf("ChannelsByActiveUserCount")),
            {id -> getChannelById(id).getActiveCount()},
            {id -> -id})

    private val statistics_allChannelsByMessageCount = Heap(ScopedKeyValueStore(DB, listOf("ChannelsByMessageCount")),
            {id -> getChannelById(id).getMessageCount()},
            {id -> -id})




    fun statistics_getTop10ChannelsByUserCount() : List<String> {
        return statistics_allChannelsByUserCount.getTop10().map { id -> getChannelById(id).getName() }
    }

    fun statistics_getTop10ChannelsByActiveUserCount() : List<String> {
        return statistics_allChannelsByActiveCount.getTop10().map { id -> getChannelById(id).getName() }
    }
    fun statistics_getTop10ChannelsByMessageCount() : List<String> {
        return statistics_allChannelsByMessageCount.getTop10().map { id -> getChannelById(id).getName() }
    }


    fun createNewChannel(name : String) : Channel {

        val (channelDB, id) = allChannels.newSlot()
        val channel = Channel(channelDB, id)

        channel.initialize(name)

        nameToId.write(name, id)
        statistics_allChannelsByUserCount.addMinimum(id)
        statistics_allChannelsByActiveCount.add(id)
        statistics_allChannelsByMessageCount.addMinimum(id)

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
        //assert(c.getUserCount() == 0)


        CompletableFuture.allOf(
                CompletableFuture.runAsync {nameToId.delete(c.getName())},
                CompletableFuture.runAsync {statistics_allChannelsByUserCount.remove(c.getID())},
                CompletableFuture.runAsync {statistics_allChannelsByActiveCount.remove(c.getID())},
                CompletableFuture.runAsync {statistics_allChannelsByMessageCount.remove(c.getID())}

        ).join()
    }

    inner class Channel(DB: KeyValueStore, private val id: Int) {

        private val userList = Set(DB.scope("users"))
        private val activeList = Set(DB.scope("activeUsers"))
        private val operatorList = Set(DB.scope("operators"))
        private val name = DB.getStringReference("name")

        private val messageCount = DB.getIntReference("messageCount")
        fun getMessageCount() = messageCount.read() ?: 0
        fun addToMessagesCount() {
            messageCount.write(getMessageCount() + 1)
            statistics_allChannelsByMessageCount.idIncremented(id)
        }


        fun initialize(s : String) {
            name.write(s)
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
            operatorList.add(user.id())
        }

        fun isOp(user : User) : Boolean = operatorList.exists(user.id())


        fun addActive(user : User) {
            activeList.add(user.id())
            statistics_allChannelsByActiveCount.idIncremented(id)
        }

        fun removeActive(user : User) {
            activeList.remove(user.id())
            statistics_allChannelsByActiveCount.idDecremented(id)
        }

        private fun isActive(user : User) : Boolean = activeList.exists(user.id())

        fun addUser(user : User) {
            val userid = user.id()
            CompletableFuture.allOf(
                    CompletableFuture.runAsync {
                        userList.add(userid)
                        statistics_allChannelsByUserCount.idIncremented(id)
                    },
                    CompletableFuture.runAsync {
                        if (user.isLoggedIn()) {
                            addActive(user)
                        }
                    }
            ).join()
        }

        fun removeUser(user : User) {
            val userid = user.id()

            CompletableFuture.allOf(
                    CompletableFuture.runAsync {
                        userList.remove(userid)
                        statistics_allChannelsByUserCount.idDecremented(id)
                    },
                    CompletableFuture.runAsync {
                        if (operatorList.exists(userid))
                            operatorList.remove(userid)
                    },
                    CompletableFuture.runAsync {
                        activeList.remove(userid)
                        statistics_allChannelsByActiveCount.idDecremented(id)
                    }

            ).join()

            if (getUserCount() == 0) {
                removeChannel(this)
            }

        }

        fun forEachUser( func: (Int) -> Unit) {
            userList.forEach(func)
        }

        fun hasUser(user : User) : Boolean = userList.exists(user.id())

    }

}

