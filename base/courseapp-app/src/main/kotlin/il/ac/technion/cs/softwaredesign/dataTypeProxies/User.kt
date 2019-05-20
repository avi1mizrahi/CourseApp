package il.ac.technion.cs.softwaredesign.dataTypeProxies

import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.Set
import il.ac.technion.cs.softwaredesign.dataTypeProxies.TokenManager.Token


private const val USERSTATS_IDENTIFIER = "usersstats"

//users/$id/token
//users/$id/password
//users/$id/name -> name
//nametoid/$name/ -> int32

class UserManager(private val DB: KeyValueStore) {
    private val count = DB.getIntReference(listOf(USERSTATS_IDENTIFIER, "count"))
    private val nameToIdMap = DB.getIntMapReference(listOf("nametoid"))

    private val activeCount = DB.getIntReference(listOf(USERSTATS_IDENTIFIER, "activecount"))
    private val allUsersByChannelCount =
            Heap(ScopedKeyValueStore(DB, listOf(USERSTATS_IDENTIFIER, "usersbychannels")),
                 { id -> getUserByID(id).getChannelCount() },
                 { id -> -id })

    init {
        // initialize user count
        if (count.read() == null) {
            count.write(0)
            activeCount.write(0)
        }

    }

    fun getTop10UsersByChannel(): List<String> =
            allUsersByChannelCount.getTop10().map { id -> getUserByID(id).getName() }

    fun createUser(name: String, password: String): User {
        val id = getUserCount()
        incrementUserCount()

        val ret = getUserByID(id)
        ret.initialize(name, password)
        if (id == 0) ret.setAdmin()

        addUserID(name, id)
        return ret
    }

    fun getUserCount(): Int = count.read()!!

    fun getActiveCount(): Int = activeCount.read()!!

    private fun incrementUserCount() = count.write(getUserCount() + 1)

    fun getUserByName(name: String): User? {
        val id = nameToIdMap.read(name) ?: return null
        return getUserByID(id)
    }

    fun getUserByID(id: Int): User =
            User(ScopedKeyValueStore(DB, listOf("users", id.toString())), id)

    private fun addUserID(name: String, id: Int) = nameToIdMap.write(name, id)

    inner class User(DB: ScopedKeyValueStore, private val id: Int) {
        private val name = DB.getStringReference("name")
        private val password = DB.getStringReference("password")
        private var token = DB.getStringReference("token")
        private val isAdmin = DB.getStringReference("isAdmin")

        private var channelList = Set(ScopedKeyValueStore(DB, listOf("channels")))

        fun initialize(n: String, pass: String) {
            name.write(n)
            password.write(pass)
            channelList.initialize()
        }

        fun getChannelCount(): Int = channelList.count()

        fun addToChannelList(channel: ChannelManager.Channel) {
            assert(!isInChannel(channel))
            channelList.add(channel.getID())
        }

        fun removeFromChannelList(channel: ChannelManager.Channel) {
            assert(isInChannel(channel))
            channelList.remove(channel.getID())
        }

        fun isInChannel(channel: ChannelManager.Channel): Boolean =
                channelList.exists(channel.getID())

        fun getChannelList(): List<Int> = channelList.getAll()

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
    }
}

