package il.ac.technion.cs.softwaredesign.dataTypeProxies

import il.ac.technion.cs.softwaredesign.KeyValueStore
import il.ac.technion.cs.softwaredesign.getIntReference
import il.ac.technion.cs.softwaredesign.dataTypeProxies.UserManager.User
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

    fun getTokenByString(str : String) : Token? {
        if (exists(str))
            return Token(DB, str)
        return null
    }

    fun exists(str : String) : Boolean {
        return Token(DB, str).exists()
    }
    inner class Token(private val DB: KeyValueStore, private val token: String) {

        private val userID = DB.getIntReference(listOf(TOKENS_IDENTIFIER, token))

        fun getString() : String{
            return token
        }

        fun remove() {
            userID.delete()
        }

        fun setUser(user: User)  {
            userID.write(user.getID())
        }

        fun getUserid() : Int? {
            return userID.read() ?: return null

        }

        fun exists() : Boolean {
            return getUserid() != null
        }

    }
}

