package il.ac.technion.cs.softwaredesign.dataTypeProxies

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.KeyValueStore
import il.ac.technion.cs.softwaredesign.ScopedKeyValueStore
import il.ac.technion.cs.softwaredesign.getIntReference
import il.ac.technion.cs.softwaredesign.dataTypeProxies.UserManager.User
import il.ac.technion.cs.softwaredesign.scope
import kotlin.random.Random


/**
 * A manager that handles creation and assigning of tokens.
 */
class TokenManager @Inject constructor(private val _db: KeyValueStore) {

    private val DB = _db.scope("tokens")

    private fun generateToken() : String {
        var out = ""
        repeat(16) {
            out += Random.nextInt('a'.toInt(), 'z'.toInt() + 1).toChar()
        }
        return out
    }

    /**
     * Generates a new random token and assigns that token to the given User.
     * The token must be assigned on the User now.
     * @param u The user to assign to this token
     */
    fun generateNewTokenForUser(u: User) : Token {
        val t = Token(DB, generateToken())
        t.setUser(u)
        return t
    }

    /**
     * Returns the token object associated with the given token or null if the token doesn't exist.
     * @param str The string of the token
     */
    fun getTokenByString(str : String) : Token? {
        if (exists(str))
            return Token(DB, str)
        return null
    }



    private fun exists(str : String) : Boolean {
        return Token(DB, str).exists()
    }

    /**
     * represents a token object on the DB
     */
    inner class Token(DB: KeyValueStore, private val token: String) {

        private val userID = DB.getIntReference(token)

        /**
         * Get the token's string
         */
        fun getString() : String{
            return token
        }

        /**
         * Deletes the token from the DB
         */
        fun delete() {
            userID.delete()
        }

        /**
         * Write a user to this token
         * @param user The user to write
         */
        fun setUser(user: User)  {
            userID.write(user.id())
        }

        /**
         * Get the userId of this token
         */
        fun getUserid() : Int? {
            return userID.read() ?: return null

        }

        /**
         * Return true if this token exists
         */
        fun exists() : Boolean {
            return getUserid() != null
        }

    }
}

