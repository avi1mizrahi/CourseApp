package il.ac.technion.cs.softwaredesign.dataTypes

// TODO discuss how to implement this
import il.ac.technion.cs.softwaredesign.DB
//

class Token(token: String) {
    private var token: String = token

    public fun setUser(user: User)  {
        DB.write_string("tokens", token, value=user.getName())
    }

    public fun getUser() : User? {
        val name = DB.read_string("tokens", token) ?: return null
        return User(name)
    }

}