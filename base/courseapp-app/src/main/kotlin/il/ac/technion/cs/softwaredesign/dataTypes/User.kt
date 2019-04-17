package il.ac.technion.cs.softwaredesign.dataTypes


// TODO discuss how to implement this
import il.ac.technion.cs.softwaredesign.DB
///


class User(name: String) {
    private var name: String = name

    public fun getName() : String {
        return this.name
    }

    // TODO Can we eliminate the duplicate ("users", name) part?
    public fun exists() : Boolean {
        return DB.read_string("users", name, "password") != null
    }
    public fun getCurrentToken() : String? {
        return DB.read_string("users", name, "token")
    }
    public fun setCurrentToken(token: String) {
        DB.write_string("users", name, "token", value=token)
    }
    public fun removeCurrentToken() {
        DB.delete_string("users", name, "token")
    }

    public fun getPassword() : String? {
        return DB.read_string("users", name, "password")
    }
    public fun setPassword(pass: String) {
        DB.write_string("users", name, "password", value=pass)
    }

}