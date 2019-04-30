package il.ac.technion.cs.softwaredesign.dataTypes


import il.ac.technion.cs.softwaredesign.KeyValueStore



class User(private var DB: KeyValueStore, private var name: String) {

    fun getName() : String {
        return this.name
    }

    // TODO Can we eliminate the duplicate ("users", name) part?
    fun exists() : Boolean {
        return DB.read("users", name, "password") != null
    }

    fun isLoggedIn() : Boolean {
        return DB.read("users", name, "token") != null
    }

    fun getCurrentToken() : Token? {
        val token = DB.read("users", name, "token") ?: return null

        return Token(DB, token)
    }

    fun setCurrentToken(token: Token) {
        DB.write("users", name, "token", value=token.getString())
    }

    fun removeCurrentToken() {
        DB.delete("users", name, "token")
    }

    fun getPassword() : String? {
        return DB.read("users", name, "password")
    }

    fun setPassword(pass: String) {
        DB.write("users", name, "password", value=pass)
    }

}