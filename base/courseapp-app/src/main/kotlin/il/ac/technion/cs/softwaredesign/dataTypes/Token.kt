package il.ac.technion.cs.softwaredesign.dataTypes

import il.ac.technion.cs.softwaredesign.DBAccess


class Token(DB: DBAccess, token: String) {
    private var token: String = token
    private var DB: DBAccess = DB


    public fun getString() : String{
        return token
    }

    public fun exists() : Boolean {
        return DB.readString("tokens", token) != null
    }

    public fun remove() {
        DB.writeString("tokens", token, value=null)
    }

    public fun setUser(user: User)  {
        DB.writeString("tokens", token, value=user.getName())
    }

    public fun getUser() : User? {
        val name = DB.readString("tokens", token) ?: return null
        return User(DB, name)
    }

}