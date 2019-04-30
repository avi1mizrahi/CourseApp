package il.ac.technion.cs.softwaredesign.dataTypes

import il.ac.technion.cs.softwaredesign.KeyValueStore


class Token(private var DB: KeyValueStore, private var token: String) {
    fun getString() : String{
        return token
    }

    fun exists() : Boolean {
        return DB.read("tokens", token) != null
    }

    fun remove() {
        DB.delete("tokens", token)
    }

    fun setUser(user: User)  {
        DB.write("tokens", token, value=user.getName())
    }

    fun getUser() : User? {
        val name = DB.read("tokens", token) ?: return null
        return User(DB, name)
    }

}