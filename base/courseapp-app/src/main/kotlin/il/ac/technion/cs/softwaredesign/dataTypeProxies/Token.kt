package il.ac.technion.cs.softwaredesign.dataTypeProxies

import il.ac.technion.cs.softwaredesign.KeyValueStore
import il.ac.technion.cs.softwaredesign.ScopedKeyValueStore
import il.ac.technion.cs.softwaredesign.getIntReference
import il.ac.technion.cs.softwaredesign.dataTypeProxies.UserManager.User
import kotlin.random.Random


class TokenManager(private val DB: KeyValueStore) {
    private fun generateToken() : String {
        var out = ""
        repeat(32) {
            out += Random.nextInt('a'.toInt(), 'z'.toInt() + 1).toChar()
        }
        return out
    }

    fun generateNewTokenForUser(u: User) : Token {
        val t = Token(DB, generateToken())
        t.setUser(u)
        return t
    }

    fun getTokenByString(str : String) : Token? {
        if (exists(str))
            return Token(DB, str)
        return null
    }

    private fun exists(str : String) : Boolean {
        return Token(DB, str).exists()
    }
    inner class Token(DB: KeyValueStore, private val token: String) {

        private val userID = DB.getIntReference(token)

        fun getString() : String{
            return token
        }

        fun delete() {
            userID.delete()
        }

        fun setUser(user: User)  {
            userID.write(user.id())
        }

        fun getUserid() : Int? {
            return userID.read() ?: return null

        }

        fun exists() : Boolean {
            return getUserid() != null
        }

    }
}

