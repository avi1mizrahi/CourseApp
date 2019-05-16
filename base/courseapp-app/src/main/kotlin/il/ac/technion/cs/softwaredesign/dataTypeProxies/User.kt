package il.ac.technion.cs.softwaredesign.dataTypeProxies

import il.ac.technion.cs.softwaredesign.KeyValueStore


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

        var ret = User(DB, id)

        ret.setName(name)
        ret.setPassword(password)


        // TODO more user init stuff

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
        return User(DB, id)
    }


    private fun addUserID(name: String, id: Int) {
        nameToIdMap.write(name, id)
    }
}


class User(private val DB: KeyValueStore, private val id: Int) {

    val name = DB.getStringReference(listOf(USERS_IDENTIFIER, id.toString(), NAME_IDENTIFIER))
    val password = DB.getStringReference(listOf(USERS_IDENTIFIER, id.toString(), PASSWORD_IDENTIFIER))
    var token = DB.getStringReference(listOf(USERS_IDENTIFIER, id.toString(), TOKEN_IDENTIFIER))

    // TODO make these two only visible to userManager.
    internal fun setName(n : String) {
        name.write(n)
    }
    internal fun setPassword(pass: String) {
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

    fun getCurrentToken() : Token? {
        val t = token.read() ?: return null

        return Token(DB, t)
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