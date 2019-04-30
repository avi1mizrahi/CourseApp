package il.ac.technion.cs.softwaredesign.dataTypes


import il.ac.technion.cs.softwaredesign.KeyValueStore

private const val USERS_IDENTIFIER = "users"
private const val PASSWORD_IDENTIFIER = "password"
private const val TOKEN_IDENTIFIER = "token"

class User(private val DB: KeyValueStore, private var name: String) {

    fun getName() : String {
        return this.name
    }

    // TODO Can we eliminate the duplicate ("users", name) part?
    fun exists() : Boolean {
        return DB.read(USERS_IDENTIFIER, name, PASSWORD_IDENTIFIER) != null
    }

    fun isLoggedIn() : Boolean {
        return DB.read(USERS_IDENTIFIER, name, TOKEN_IDENTIFIER) != null
    }

    fun getCurrentToken() : Token? {
        val token = DB.read(USERS_IDENTIFIER, name, TOKEN_IDENTIFIER) ?: return null

        return Token(DB, token)
    }

    fun setCurrentToken(token: Token) {
        DB.write(USERS_IDENTIFIER, name, TOKEN_IDENTIFIER, value=token.getString())
    }

    fun removeCurrentToken() {
        DB.delete(USERS_IDENTIFIER, name, TOKEN_IDENTIFIER)
    }

    fun getPassword() : String? {
        return DB.read(USERS_IDENTIFIER, name, PASSWORD_IDENTIFIER)
    }

    fun setPassword(pass: String) {
        DB.write(USERS_IDENTIFIER, name, PASSWORD_IDENTIFIER, value=pass)
    }

}