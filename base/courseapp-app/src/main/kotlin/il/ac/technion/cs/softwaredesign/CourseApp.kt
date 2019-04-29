package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.dataTypes.Token
import il.ac.technion.cs.softwaredesign.dataTypes.User
import java.lang.IllegalArgumentException
import kotlin.random.Random


/**
 * This is the class implementing CourseApp, a course discussion group system.
 *
 * You may assume that [CourseAppInitializer.setup] was called before this class was instantiated.
 *
 * Currently specified:
 * + User authentication.
 */
class CourseApp (DB: DBAccess = DBAccess()) {

    private var DB: DBAccess = DB

    private fun tokenFactory(str: String) = Token(DB, str)
    private fun userFactory(str: String) = User(DB, str)


    private fun generateToken() : Token
    {
        var out = ""
        repeat(32)
        {
            out += Character.toString(Random.nextInt('a'.toInt(), 'z'.toInt() + 1))
        }
        return tokenFactory(out)
    }



    /**
     * Log in a user identified by [username] and [password], returning an authentication token that can be used in
     * future calls. If this username did not previously log in to the system, it will be automatically registered with
     * the provided password. Otherwise, the password will be checked against the previously provided password.
     *
     * Note: Allowing enumeration of valid usernames is not a good property for a system to have, from a security
     * standpoint. But this is the way this system will work.
     *
     * This is a *create* command.
     *
     * @throws IllegalArgumentException If the password does not match the username, or the user is already logged in.
     * @return An authentication token to be used in other calls.
     */
    fun login(username: String, password: String) : String
    {
        var u = userFactory(username)

        // See if the user exists and create it if it doesn't
        if (!u.exists())
        {
            u.setPassword(password)
        }
        // Else, verify the plaintext password and that the user isn't logged in
        else
        {
            if (u.getPassword() != password)
                throw IllegalArgumentException()

            if (u.isLoggedIn())
                throw IllegalArgumentException()
        }


        var t = generateToken()

        // Set Token to point to user and User to point to token.
        u.setCurrentToken(t)
        t.setUser(u)
        //

        return t.getString()

    }

    /**
     * Log out the user with this authentication [token]. The [token] will be invalidated and can not be used for future
     * calls.
     *
     * This is a *delete* command.
     *
     * @throws IllegalArgumentException If the auth [token] is invalid.
     */
    fun logout(token: String): Unit
    {
        var t = tokenFactory(token)

        if (!t.exists()) {
            throw IllegalArgumentException()
        }


        var u = t.getUser()!! // User has to exist, we just checked

        // User must have a token and it must be this token
        assert(u.getCurrentToken()!!.getString() == token)


        t.remove()
        u.removeCurrentToken()
    }


    /**
     * Indicate the status of [username] in the application.
     *
     * A valid authentication [token] (for *any* user) is required to perform this operation.
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException If the auth [token] is invalid.
     * @return True if [username] exists and is logged in, false if it exists and is not logged in, and null if it does
     * not exist.
     */
    fun isUserLoggedIn(token: String, username: String): Boolean?
    {
        // Confirm that token belongs to any user
        var t = tokenFactory(token)
        if (!t.exists()) {
            throw IllegalArgumentException()
        }

        var u = userFactory(username)
        if (!u.exists())
            return null

        return u.isLoggedIn()
    }
}