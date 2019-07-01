package il.ac.technion.cs.softwaredesign.dataTypeProxies

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.Set
import il.ac.technion.cs.softwaredesign.exceptions.NameFormatException
import il.ac.technion.cs.softwaredesign.dataTypeProxies.UserManager.User
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.concurrent.CompletableFuture


// Valid names for channels start with `#`, then have any number of English alphanumeric characters, underscores
// (`_`) and hashes (`#`).
private fun isBadChannelName(name : String) : Boolean {
    val alphanumeric = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '_' + '#'

    if (!name.startsWith("#"))
        return true

    if (name.drop(1).any { char -> !alphanumeric.contains(char)})
        return true
    return false
}


/**
 * A manager handling channel logic.
 */

class ChannelManager @Inject constructor(private val _db: KeyValueStore) {
    private val DB = _db.scope("channels")


    private val allChannels = Array(ScopedKeyValueStore(DB, listOf("allChannels")))
    private val nameToId = DB.getIntMapReference(listOf("nameToId"))

    val statistics = Statistics()
    inner class Statistics{
        val allChannelsByUserCount = Heap(ScopedKeyValueStore(DB, listOf("ChannelsByUserCount")),
                {id -> getChannelById(id).getUserCount()},
                {id -> -id})

        val allChannelsByActiveCount = Heap(ScopedKeyValueStore(DB, listOf("ChannelsByActiveUserCount")),
                {id -> getChannelById(id).getActiveCount()},
                {id -> -id})

        val allChannelsByMessageCount = Heap(ScopedKeyValueStore(DB, listOf("ChannelsByMessageCount")),
                {id -> getChannelById(id).getMessageCount()},
                {id -> -id})

        /**
         * Statistics: Returns top 10 channels by user count
         */
        fun getTop10ChannelsByUserCount() : List<String> {
            return allChannelsByUserCount.getTop10().map { id -> getChannelById(id).getName() }
        }

        /**
         * Statistics: Returns top 10 channels by active user count
         */
        fun getTop10ChannelsByActiveUserCount() : List<String> {
            return allChannelsByActiveCount.getTop10().map { id -> getChannelById(id).getName() }
        }

        /**
         * Statistics: Returns top 10 channels by message count
         */
        fun getTop10ChannelsByMessageCount() : List<String> {
            return allChannelsByMessageCount.getTop10().map { id -> getChannelById(id).getName() }
        }
    }








    /**
     * Create a new channel
     * Assumptions:
     * -The given name is unique and is legal
     *
     * @param name Channel's name
     */
    fun createNewChannel(name : String) : Channel {

        val (channelDB, id) = allChannels.newSlot()
        val channel = Channel(channelDB, id)

        channel.initialize(name)

        nameToId.write(name, id)
        statistics.allChannelsByUserCount.addMinimum(id)
        statistics.allChannelsByActiveCount.add(id)
        statistics.allChannelsByMessageCount.addMinimum(id)

        return channel
    }

    /**
     * Checks a string and throws if its an illegal channel name
     */
    fun throwIfBadChannelName(name : String) {
        if (isBadChannelName(name)) throw NameFormatException()
    }

    /**
     * Returns a channel by a name
     * @param name Name of the channel
     * @returns Channel of that name or null if not found
     */
    fun getChannelByName(name : String) : Channel? {
        val channelID = nameToId.read(name) ?: return null
        return Channel(allChannels[channelID]!!, channelID)
    }

    /**
     * Returns a channel by its ID
     * @param id ID of the channel
     * @returns Channel of that id
     */
    fun getChannelById(channelID : Int) : Channel {
        return Channel(allChannels[channelID]!!, channelID)
    }

//
//    fun channelExists(name : String) : Boolean {
//        return allChannels.read(name) != null
//    }

    /**
     * Remove a channel
     * Assumptions:
     * -Channel must be empty!
     *
     * @param c Channel to remove
     *
     */
    fun removeChannel(c : Channel) {
        //assert(c.getUserCount() == 0)


        CompletableFuture.allOf(
                CompletableFuture.runAsync {nameToId.delete(c.getName())},
                CompletableFuture.runAsync {statistics.allChannelsByUserCount.remove(c.getID())},
                CompletableFuture.runAsync {statistics.allChannelsByActiveCount.remove(c.getID())},
                CompletableFuture.runAsync {statistics.allChannelsByMessageCount.remove(c.getID())}

        ).join()
    }


    /**
     *  An object representing a channel on the DB
     */
    inner class Channel(DB: KeyValueStore, private val id: Int) {

        private val userList = Set(DB.scope("users"))
        private val activeList = Set(DB.scope("activeUsers"))
        private val operatorList = Set(DB.scope("operators"))
        private val name = DB.getStringReference("name")

        private val messageCount = DB.getIntReference("messageCount")

        /**
         * Return the amount of messages in the channel
         */
        fun getMessageCount() = messageCount.read() ?: 0

        /**
         * Increment the message counter
         */
        fun addToMessagesCount() {
            messageCount.write(getMessageCount() + 1)
            statistics.allChannelsByMessageCount.idIncremented(id)
        }

        /**
         * Initialize the constant fields of the channel. Must be called by the managers's createNewChannel
         */
        fun initialize(s : String) {
            name.write(s)
        }

        /**
         * Returns the id of the channel
         */
        fun getID() = id

        /**
         * Returns the name of the channel
         */
        fun getName() = name.read()!!

        /**
         * Returns the amount of users in the channel
         * These values are cached and wrong reads/writes may happen if 2 instances representing the same channel use this
         */
        fun getUserCount() : Int {
            return userList.count()
        }

        /**
         * Returns the amount of active users in the channel
         * These values are cached and wrong reads/writes may happen if 2 instances representing the same channel use this
         */
        fun getActiveCount() : Int {
            return activeList.count()
        }

        /**
         * Add a user to the channel's operator list
         * @param user User to add
         */
        fun addOp(user : User) {
            operatorList.add(user.id())
        }

        /**
         * Check if a user is an operator on this channel
         * @param user User to check
         */
        fun isOp(user : User) : Boolean = operatorList.exists(user.id())


        /**
         * Add a user to this channels Active user list
         * @param user User to add
         */
        fun addActive(user : User) {
            activeList.add(user.id())
            statistics.allChannelsByActiveCount.idIncremented(id)
        }

        /**
         * Remove a user from this channels Active user list
         * @param user User to remove
         */
        fun removeActive(user : User) {
            activeList.remove(user.id())
            statistics.allChannelsByActiveCount.idDecremented(id)
        }


        private fun isActive(user : User) : Boolean = activeList.exists(user.id())

        /**
         * Add a user to this channel
         * @param user User to add
         */
        fun addUser(user : User) {
            val userid = user.id()
            CompletableFuture.allOf(
                    CompletableFuture.runAsync {
                        userList.add(userid)
                        statistics.allChannelsByUserCount.idIncremented(id)
                    },
                    CompletableFuture.runAsync {
                        if (user.isLoggedIn())
                            addActive(user)
                    }
            ).join()
        }

        /**
         * Remove a user from this channel
         * @param user User to remove
         */
        fun removeUser(user : User) {
            val userid = user.id()

            CompletableFuture.allOf(
                    CompletableFuture.runAsync {
                        userList.remove(userid)
                        statistics.allChannelsByUserCount.idDecremented(id)
                    },
                    CompletableFuture.runAsync {
                        if (operatorList.exists(userid))
                            operatorList.remove(userid)
                    },
                    CompletableFuture.runAsync {
                        activeList.remove(userid)
                        statistics.allChannelsByActiveCount.idDecremented(id)
                    }

            ).join()

            if (getUserCount() == 0) {
                removeChannel(this)
            }

        }

        /**
         * Run an action on the ID of each user in this channel
         */
        fun forEachUser( func: (Int) -> Unit) {
            userList.forEach(func)
        }

        /**
         * returns true if the channel has given user
         * @param user User to check.
         */
        fun hasUser(user : User) : Boolean = userList.exists(user.id())

    }

}

