package il.ac.technion.cs.softwaredesign.dataTypes


import il.ac.technion.cs.softwaredesign.DBAccess



class User(DB: DBAccess, name: String) {
    private var name: String = name
    private var DB: DBAccess = DB

    public fun getName() : String {
        return this.name
    }

    // TODO Can we eliminate the duplicate ("users", name) part?
    public fun exists() : Boolean {
        return DB.readString("users", name, "password") != null
    }

    public fun isLoggedIn() : Boolean {
        return DB.readString("users", name, "token") != null
    }
    public fun getCurrentToken() : Token? {
        val token = DB.readString("users", name, "token") ?: return null

        return Token(DB, token)
    }
    public fun setCurrentToken(token: Token) {
        DB.writeString("users", name, "token", value=token.getString())
    }
    public fun removeCurrentToken() {
        DB.deleteString("users", name, "token")
    }

    public fun getPassword() : String? {
        return DB.readString("users", name, "password")
    }
    public fun setPassword(pass: String) {
        DB.writeString("users", name, "password", value=pass)
    }

}