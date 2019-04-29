package il.ac.technion.cs.softwaredesign.dataTypes

import il.ac.technion.cs.softwaredesign.DBAccess


class Token(private var DB: DBAccess, private var token: String) {
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