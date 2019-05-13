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
    init {
        // initialize user count
        if (DB.read_int32(listOf(USERSTATS_IDENTIFIER, USERSTATSCOUNT_IDENTIFIER)) == null)
            DB.write_int32(listOf(USERSTATS_IDENTIFIER, USERSTATSCOUNT_IDENTIFIER), 0)


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
        return DB.read_int32(listOf(USERSTATS_IDENTIFIER, USERSTATSCOUNT_IDENTIFIER))!!
    }

    fun incrementUserCount() {
        DB.write_int32(listOf(USERSTATS_IDENTIFIER, USERSTATSCOUNT_IDENTIFIER), getUserCount() + 1)
    }

    fun getUserByName(name: String): User? {
        val id = DB.read_int32(listOf(NAMETOID_IDENTIFIER, name)) ?: return null
        return User(DB, id)
    }


    private fun addUserID(name: String, id: Int) {
        DB.write_int32(listOf(NAMETOID_IDENTIFIER, name), id)
    }
}


class User(private val DB: KeyValueStore, private val id: Int) {

    // TODO make these two only visible to userManager.
    internal fun setName(name : String) {
        DB.write(listOf(USERS_IDENTIFIER, id.toString(), NAME_IDENTIFIER), name)
    }
    internal fun setPassword(pass: String) {
        DB.write(listOf(USERS_IDENTIFIER, id.toString(), PASSWORD_IDENTIFIER), value=pass)
    }

    fun getName() : String {
        return DB.read(listOf(USERS_IDENTIFIER, id.toString(), NAME_IDENTIFIER))!!
    }

    fun getID() : Int {
        return this.id
    }

    fun exists() : Boolean {
        return DB.read(listOf(USERS_IDENTIFIER, id.toString(), PASSWORD_IDENTIFIER)) != null
    }

    fun isLoggedIn() : Boolean {
        return DB.read(listOf(USERS_IDENTIFIER, id.toString(), TOKEN_IDENTIFIER)) != null
    }

    fun getCurrentToken() : Token? {
        val token = DB.read(listOf(USERS_IDENTIFIER, id.toString(), TOKEN_IDENTIFIER)) ?: return null

        return Token(DB, token)
    }

    fun setToken(token: Token) {
        DB.write(listOf(USERS_IDENTIFIER, id.toString(), TOKEN_IDENTIFIER), value=token.getString())
    }

    fun removeToken() {
        DB.delete(listOf(USERS_IDENTIFIER, id.toString(), TOKEN_IDENTIFIER))
    }

    fun getPassword() : String? {
        return DB.read(listOf(USERS_IDENTIFIER, id.toString(), PASSWORD_IDENTIFIER))
    }



}