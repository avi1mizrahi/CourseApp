package il.ac.technion.cs.softwaredesign.dataTypeProxies

import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.Set
import il.ac.technion.cs.softwaredesign.dataTypeProxies.TokenManager.Token



//users/$id/token
//users/$id/password
//users/$id/name -> name
//nametoid/$name/ -> int32

class UserManager(DB: KeyValueStore) {
    private val nameToIdMap = DB.getIntMapReference(listOf("nametoid"))
    private val activeCount = DB.getIntReference(listOf("activecount"))
    private val allUsersByChannelCount =
            Heap(ScopedKeyValueStore(DB, listOf("usersbychannels")),
                 { id -> getUserByID(id).getChannelCount() },
                 { id -> -id })


    private val allUsers = Array(ScopedKeyValueStore(DB, listOf("allusers")))


    init {
        // initialize counters
        if (activeCount.read() == null) {
            activeCount.write(0)
        }
    }

    fun getTop10UsersByChannel(): List<String> =
            allUsersByChannelCount.getTop10().map { id -> getUserByID(id).getName() }

    fun createUser(name: String, password: String): User {
        val (userDB, id) = allUsers.newSlot()

        val ret = User(userDB, id)
        ret.initialize(name, password)
        if (id == 0) ret.setAdmin()

        addUserID(name, id)
        allUsersByChannelCount.add(id)
        return ret
    }

    fun getUserCount(): Int = allUsers.count()

    fun getActiveCount(): Int = activeCount.read()!!

    fun getUserByName(name: String): User? {
        val id = nameToIdMap.read(name) ?: return null
        return getUserByID(id)
    }

    fun getUserByID(id: Int): User = User(allUsers[id]!!, id)

    fun forEachUser( func: (User) -> Unit) {
        allUsers.forEach { db, index ->
            func(User(db, index))
        }
    }

    private fun addUserID(name: String, id: Int) = nameToIdMap.write(name, id)

    inner class User(DB: KeyValueStore, private val id: Int) {
        private val name = DB.getStringReference("name")
        private val password = DB.getStringReference("password")
        private var token = DB.getStringReference("token")
        private val isAdmin = DB.getStringReference("isAdmin")
        private var channelList = Set(DB.scope("channels"))
        private val pendingMessages = ArrayInt(DB.scope("pendingMessagges"))

        fun initialize(n: String, pass: String) {
            name.write(n)
            password.write(pass)
        }

        fun getChannelCount(): Int = channelList.count()

        fun addToChannelList(channel: ChannelManager.Channel) {
            channelList.add(channel.getID())

            allUsersByChannelCount.idIncremented(id)
        }

        fun removeFromChannelList(channel: ChannelManager.Channel) {
            channelList.remove(channel.getID())

            allUsersByChannelCount.idDecremented(id)
        }

        fun isInChannel(channel: ChannelManager.Channel): Boolean =
                channelList.exists(channel.getID())

        fun forEachChannel(action: (Int) -> Unit) = channelList.forEach(action)

        fun isAdmin(): Boolean = isAdmin.read() != null

        fun setAdmin() = isAdmin.write("")

        fun getName(): String = name.read()!!

        fun id(): Int = id

        fun isLoggedIn(): Boolean = token.read() != null

        fun getCurrentToken(): String? = token.read()

        private fun setToken(t: Token) = token.write(t.getString())

        private fun removeToken() = token.delete()

        fun getPassword(): String? = password.read()

        fun logInAndAssignToken(token: Token) {
            assert(!isLoggedIn())
            setToken(token)

            activeCount.write(activeCount.read()!! + 1)
        }

        // Assign the token to this user
        fun logout() {
            assert(isLoggedIn())
            removeToken()

            activeCount.write(activeCount.read()!! - 1)
        }

        fun getPendingMessagesCount() = pendingMessages.count()
        fun clearPendingMessages() = pendingMessages.clear()
        fun addPendingMessageID(i : Int) = pendingMessages.push(i)
        fun forEachPendingMessage(action : (Int) -> Unit) = pendingMessages.forEach(action)

    }
}

