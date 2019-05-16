package il.ac.technion.cs.softwaredesign.dataTypeProxies

import il.ac.technion.cs.softwaredesign.KeyValueStore
import il.ac.technion.cs.softwaredesign.getIntReference
import kotlin.random.Random


private const val TOKENS_IDENTIFIER = "tokens"

class TokenManager(private val DB: KeyValueStore) {
    private fun generateToken() : String {
        var out = ""
        repeat(32) {
            out += Random.nextInt('a'.toInt(), 'z'.toInt() + 1).toChar()
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
        return Token(DB, str).exists()
    }

}

class Token(private val DB: KeyValueStore, private val token: String) {

    val userID = DB.getIntReference(listOf(TOKENS_IDENTIFIER, token))

    fun getString() : String{
        return token
    }

    fun remove() {
        userID.delete()
    }

    fun setUser(user: User)  {
        userID.write(user.getID())
    }

    fun getUser() : User? {
        val id = userID.read() ?: return null
        return User(DB, id)
    }

    internal fun exists() : Boolean {
        return getUser() != null
    }

}