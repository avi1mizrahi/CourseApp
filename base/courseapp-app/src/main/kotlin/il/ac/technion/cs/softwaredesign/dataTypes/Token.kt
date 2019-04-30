package il.ac.technion.cs.softwaredesign.dataTypes

import il.ac.technion.cs.softwaredesign.KeyValueStore


private const val TOKENS_IDENTIFIER = "tokens"

class Token(private var DB: KeyValueStore, private var token: String) {
    fun getString() : String{
        return token
    }

    fun exists() : Boolean {
        return DB.read(TOKENS_IDENTIFIER, token) != null
    }

    fun remove() {
        DB.delete(TOKENS_IDENTIFIER, token)
    }

    fun setUser(user: User)  {
        DB.write(TOKENS_IDENTIFIER, token, value=user.getName())
    }

    fun getUser() : User? {
        val name = DB.read(TOKENS_IDENTIFIER, token) ?: return null
        return User(DB, name)
    }

}