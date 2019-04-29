package il.ac.technion.cs.softwaredesign.dataTypes

import il.ac.technion.cs.softwaredesign.DBAccess


class Token(private var DB: DBAccess, private var token: String) {
    public fun getString() : String{
        return token
    }

    public fun exists() : Boolean {
        return DB.read("tokens", token) != null
    }

    public fun remove() {
        DB.write("tokens", token, value=null)
    }

    public fun setUser(user: User)  {
        DB.write("tokens", token, value=user.getName())
    }

    public fun getUser() : User? {
        val name = DB.read("tokens", token) ?: return null
        return User(DB, name)
    }

}