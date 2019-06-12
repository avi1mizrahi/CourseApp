package il.ac.technion.cs.softwaredesign.dataTypeProxies

import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.Set
import il.ac.technion.cs.softwaredesign.dataTypeProxies.TokenManager.Token


/**
 *
 *     A manager that handles users
 *     @param DB a scoped DB pointing to the unique folder the usermanager was initialized on
 *     @constructor creates new UserManager.
 *
 */
class UserManager(DB: KeyValueStore) {
    private val nameToIdMap = DB.getIntMapReference(listOf("nametoid"))

    private var activeCountCache = -1
    private val activeCount = DB.getIntReference(listOf("activecount"))
    private val statistics_allUsersByChannelCount =
            Heap(ScopedKeyValueStore(DB, listOf("usersbychannels")),
                 { id -> getUserByID(id).getChannelCount() },
                 { id -> -id })


    private val allUsers = Array(ScopedKeyValueStore(DB, listOf("allusers")))

    fun statistics_getTop10UsersByChannel(): List<String> =
            statistics_allUsersByChannelCount.getTop10().map { id -> getUserByID(id).getName() }


    /**
     * Creates a new user with the given name and password.
     * Assumptions:
     *  -The user name isn't taken
     *
     *
     * @param name User's name
     * @param password his password
     */
    fun createUser(name: String, password: String): User {
        val (userDB, id) = allUsers.newSlot()

        val ret = User(userDB, id)
        ret.initialize(name, password)
        if (id == 0) ret.setAdmin()

        addUserID(name, id)
        statistics_allUsersByChannelCount.addMinimum(id)
        return ret
    }

    /**
     * Gets the user count of the system. The allUsers object uses an inner cache, and if another instance of the manager
     * changes it it must be forced to refresh its cache with DataStructure.forceCacheRefresh()
     */
    fun getUserCount(): Int = allUsers.count() // has inner cache

    private fun setActiveCount(i : Int) {
        activeCount.write(i)
        activeCountCache = i
    }


    /**
     * Gets the amount of users logged in at the moment. Does not refresh cache.
     */
    fun getActiveCount(): Int  {
        if (activeCountCache == -1)
            activeCountCache = activeCount.read() ?: 0

        return activeCountCache

    }

    fun getUserByName(name: String): User? {
        val id = nameToIdMap.read(name) ?: return null
        return getUserByID(id)
    }

    fun getUserByID(id: Int): User = User(allUsers[id]!!, id)

//    fun forEachUser( func: (User) -> Unit) {
//        allUsers.forEach { db, index ->
//            func(User(db, index))
//        }
//    }

    private fun addUserID(name: String, id: Int) = nameToIdMap.write(name, id)


    /**
     * Refresh the cache and get the user count.
     */
    fun statistics_getUserCount() : Int {
        allUsers.forceCacheRefresh()
        return getUserCount()
    }

    /**
     * Refresh the cache and get the cache the active user count
     */
    fun statistics_getActiveCount() : Int {
        activeCountCache = -1
        return getActiveCount()
    }


    /**
     * a class representing a proxy to a user's info on the DB.
     * Should be constructed only by CreateUser()
     */
    inner class User(DB: KeyValueStore, private val id: Int) {

        private val name = DB.getStringReference("name")
        private val password = DB.getStringReference("password")
        private var token = DB.getStringReference("token")
        private val isAdmin = DB.getStringReference("isAdmin")
        private var channelList = Set(DB.scope("channels"))

        private val pendingMessages = ArrayInt(DB.scope("pendingMessages"))
        private val lastReadBroadcast = DB.getIntReference("lastReadBroadcast")


        fun initialize(n: String, pass: String) {
            name.write(n)
            password.write(pass)
        }


        /**
         * Get a number representing the last broadcast read by the user. The number is NOT the message id and is used by
         * the message manager to choose which broadcasts to send.
         */
        fun getLastReadBroadcast() = lastReadBroadcast.read() ?: -1
        fun setLastReadBroadcast(count : Int) = lastReadBroadcast.write(count)

        /**
         * Get the amount of channels the user is in.
         */
        fun getChannelCount(): Int = channelList.count()

        /**
         * Add a channel to the user's channel list.
         */
        fun addToChannelList(channel: ChannelManager.Channel) {
            channelList.add(channel.getID())

            statistics_allUsersByChannelCount.idIncremented(id)
        }

        /**
         * Remove a channel from the user's channel list.
         */
        fun removeFromChannelList(channel: ChannelManager.Channel) {
            channelList.remove(channel.getID())

            statistics_allUsersByChannelCount.idDecremented(id)
        }

        /**
         * Return if the user is in the given channel
         */
        fun isInChannel(channel: ChannelManager.Channel): Boolean =
                channelList.exists(channel.getID())

        /**
         * Run an action on each channel the user is in.
         */
        fun forEachChannel(action: (Int) -> Unit) = channelList.forEach(action)

        /**
         * Return if the user is an admin
         */
        fun isAdmin(): Boolean = isAdmin.read() != null

        /**
         * Set user to admin.
         */
        fun setAdmin() = isAdmin.write("")

        /**
         * Get the user's name
         */
        fun getName(): String = name.read()!!

        /**
         * Get the user's unique ID
         */
        fun id(): Int = id


        /**
         * Return true if the user is logged in (has an active token)
         */
        fun isLoggedIn(): Boolean = token.read() != null

        //fun getCurrentToken(): String? = token.read()

        private fun setToken(t: Token) = token.write(t.getString())

        private fun removeToken() = token.delete()

        /**
         * Get the user's password
         */
        fun getPassword(): String? = password.read()

        /**
         * Log the user in and update the active user count
         * @param token the new token
         */
        fun logInAndAssignToken(token: Token) {
            setToken(token)

            setActiveCount(getActiveCount() + 1)
        }

        /**
         * Log the user out and update the active user count
         */
        fun logout() {
            removeToken()

            setActiveCount(getActiveCount() - 1)
        }


        /**
         * Clear the user's pendind messages list. These include private and channel messages only.
         */
        fun clearPendingMessages() = pendingMessages.clear()
        /**
         * Add a new message ID to the user's pending messages list. These include private and channel messages only.
         */
        fun addPendingMessageID(i : Int) = pendingMessages.push(i)
        /**
         * Run an action on each of the user's pending messages. These include private and channel messages only.
         */
        fun forEachPendingMessage(action : (Int) -> Unit) = pendingMessages.forEach(action)

    }
}

