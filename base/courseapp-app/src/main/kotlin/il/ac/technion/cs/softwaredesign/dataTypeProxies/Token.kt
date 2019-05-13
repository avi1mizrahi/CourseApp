package il.ac.technion.cs.softwaredesign.dataTypeProxies

import il.ac.technion.cs.softwaredesign.KeyValueStore
import kotlin.random.Random


private const val TOKENS_IDENTIFIER = "tokens"

class TokenManager(private val DB: KeyValueStore) {
    private fun generateToken() : String
    {
        var out = ""
        repeat(32)
        {
            out += Character.toString(Random.nextInt('a'.toInt(), 'z'.toInt() + 1))
        }
        return out
    }

    fun generateNewToken(u: User) : Token {
        val t = Token(DB, generateToken())
        t.setUser(u)
        u.setToken(t)
        return t
    }

    fun removeTokenLink(t: Token){
        val u = t.getUser()!! // User has to exist, we just checked

        // User must have a token and it must be this token
        assert(u.getCurrentToken()!!.getString() == t.getString())

        t.remove()
        u.removeToken()
    }

    fun getTokenByString(str : String) : Token? {
        if (exists(str))
            return Token(DB, str)
        return null
    }

    fun exists(str : String) : Boolean {
        return DB.read_int32(listOf(TOKENS_IDENTIFIER, str)) != null
    }

}

class Token(private val DB: KeyValueStore, private val token: String) {
    fun getString() : String{
        return token
    }

    fun remove() {
        DB.delete_int32(listOf(TOKENS_IDENTIFIER, token))
    }

    fun setUser(user: User)  {
        DB.write_int32(listOf(TOKENS_IDENTIFIER, token), user.getID())
    }

    fun getUser() : User? {
        val id = DB.read_int32(listOf(TOKENS_IDENTIFIER, token)) ?: return null
        return User(DB, id)
    }

}