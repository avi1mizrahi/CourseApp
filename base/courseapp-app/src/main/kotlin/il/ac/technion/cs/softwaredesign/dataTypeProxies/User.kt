package il.ac.technion.cs.softwaredesign.dataTypeProxies

import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.Set
import il.ac.technion.cs.softwaredesign.dataTypeProxies.TokenManager.Token


private const val USERSTATS_IDENTIFIER = "usersstats"
private const val USERSTATSCOUNT_IDENTIFIER = "count"

private const val USERS_IDENTIFIER = "users"
private const val PASSWORD_IDENTIFIER = "password"
private const val NAME_IDENTIFIER = "name"
private const val TOKEN_IDENTIFIER = "token"
private const val NAMETOID_IDENTIFIER = "nametoid"




//users/$id/token
//users/$id/password
//users/$id/name -> name
//nametoid/$name/ -> int32

class UserManager(private val DB: KeyValueStore)
{
    var count = DB.getIntReference(listOf(USERSTATS_IDENTIFIER, USERSTATSCOUNT_IDENTIFIER))
    var nameToIdMap = DB.getIntMapReference(listOf(NAMETOID_IDENTIFIER))

    init {
        // initialize user count
        if (count.read() == null)
            count.write(0)

    }

    fun createUser(name: String, password: String) : User {
        var id = getUserCount()
        incrementUserCount()

        var ret = getUserByID(id)

        ret.setName(name)
        ret.setPassword(password)
        if (id == 0) ret.setisAdmin(true)


        addUserID(name, id)
        return ret
    }

    fun getUserCount(): Int {
        return count.read()!!
    }

    fun incrementUserCount() {
        count.write(getUserCount() + 1)
    }

    fun getUserByName(name: String): User? {
        val id = nameToIdMap.read(name) ?: return null
        return getUserByID(id)
    }

    fun getUserByID(id : Int) : User {
        return User(ScopedKeyValueStore(DB, listOf(USERS_IDENTIFIER, id.toString())), id)
    }

    private fun addUserID(name: String, id: Int) {
        nameToIdMap.write(name, id)
    }


    class User(private val DB: ScopedKeyValueStore, private val id: Int) {
        private val name = DB.getStringReference(NAME_IDENTIFIER)
        private val password = DB.getStringReference(PASSWORD_IDENTIFIER)
        private var token = DB.getStringReference(TOKEN_IDENTIFIER)
        private val isAdmin = DB.getStringReference("isAdmin")

        private var channelList = Set(ScopedKeyValueStore(DB, listOf("channels")))


        fun addToChannelList(channel: ChannelManager.Channel) {
            assert(!isInChannel(channel))
            channelList.add(channel.getID())
        }
        fun removeFromChannelList(channel: ChannelManager.Channel) {
            assert(isInChannel(channel))
            channelList.remove(channel.getID())
        }

        fun isInChannel(channel: ChannelManager.Channel) : Boolean {
            return channelList.exists(channel.getID())
        }

        fun getChannelList() : List<Int> {
            return channelList.getAll()
        }


        fun getisAdmin() : Boolean {
            return isAdmin.read() != null
        }
        fun setisAdmin(b: Boolean) {
            if (b)
                isAdmin.write("")
            else
                isAdmin.delete()
        }

        fun setName(n : String) {
            name.write(n)
        }
        fun setPassword(pass: String) {
            password.write(pass)
        }

        fun getName() : String {
            return name.read()!!
        }

        fun getID() : Int {
            return this.id
        }

        fun exists() : Boolean {
            return password.read() != null
        }

        fun isLoggedIn() : Boolean {
            return token.read() != null
        }

        fun getCurrentToken() : String? {
            return token.read()
        }

        fun setToken(t: Token) {
            token.write(t.getString())
        }

        fun removeToken() {
            token.delete()
        }

        fun getPassword() : String? {
            return password.read()
        }



    }
}

