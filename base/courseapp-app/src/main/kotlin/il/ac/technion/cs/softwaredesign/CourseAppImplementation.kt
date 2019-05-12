package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.dataTypeProxies.Token
import il.ac.technion.cs.softwaredesign.dataTypeProxies.User
import il.ac.technion.cs.softwaredesign.exceptions.*
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.lang.IllegalArgumentException
import kotlin.random.Random



//class StaffStorage : Storage {
//        override fun read(key: ByteArray): ByteArray? {
//                return il.ac.technion.cs.softwaredesign.storage.read(key)
//        }
//
//        override fun write(key: ByteArray, value: ByteArray) {
//                il.ac.technion.cs.softwaredesign.storage.write(key, value)
//        }
//}

/**
 * This is the class implementing CourseApp, a course discussion group system.
 *
 * You may assume that [CourseAppInitializer.setup] was called before this class was instantiated.
 *
 * Currently specified:
 * + User authentication.
 */


class CourseAppImplementation (private var DB : KeyValueStore) : CourseApp {

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
         * If this is the first user to be registered, it will be made an administrator.
         *
         * This is a *create* command.
         *
         * @throws NoSuchEntityException If the password does not match the username.
         * @throws UserAlreadyLoggedInException If the user is already logged-in.
         * @return An authentication token to be used in other calls.
         */
        override fun login(username: String, password: String) : String
        {
                val u = userFactory(username)

                // See if the user exists and create it if it doesn't
                if (!u.exists())
                {
                        u.setPassword(password)
                }
                // Else, verify the plaintext password and that the user isn't logged in
                else
                {
                        if (u.getPassword() != password)
                                throw NoSuchEntityException()

                        if (u.isLoggedIn())
                                throw UserAlreadyLoggedInException()
                }


                val t = generateToken()

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
         * @throws InvalidTokenException If the auth [token] is invalid.
         */
        override fun logout(token: String) {
                val t = tokenFactory(token)

                if (!t.exists()) {
                        throw InvalidTokenException()
                }


                val u = t.getUser()!! // User has to exist, we just checked

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
         * @throws InvalidTokenException If the auth [token] is invalid.
         * @return True if [username] exists and is logged in, false if it exists and is not logged in, and null if it does
         * not exist.
         */
        override fun isUserLoggedIn(token: String, username: String): Boolean?
        {
                // Confirm that token belongs to any user
                val t = tokenFactory(token)
                if (!t.exists()) {
                        throw InvalidTokenException()
                }

                val u = userFactory(username)
                if (!u.exists())
                        return null

                return u.isLoggedIn()
        }


        /**
         * Make another user, identified by [username], an administrator. Only users who are administrators may perform this
         * operation.
         *
         * This is an *update* command.
         *
         * @throws InvalidTokenException If the auth [token] is invalid.
         * @throws UserNotAuthorizedException If the auth [token] does not belong to a user who is an administrator.
         * @throws NoSuchEntityException If [username] does not exist.
         */
        override fun makeAdministrator(token: String, username: String) = TODO()

        /**
         * The user identified by [token] will join [channel]. If the channel does not exist, it is created only if [token]
         * identifies a user who is an administrator.
         *
         * Valid names for channels start with `#`, then have any number of English alphanumeric characters, underscores
         * (`_`) and hashes (`#`).
         *
         * This is a *create* command.
         *
         * @throws InvalidTokenException If the auth [token] is invalid.
         * @throws NameFormatException If [channel] is not a valid name for a channel.
         * @throws UserNotAuthorizedException If [channel] does not exist and [token] belongs to a user who is not an
         * administrator.
         */
        override fun channelJoin(token: String, channel: String) = TODO()

        /**
         * The user identified by [token] will exit [channel].
         *
         * If the last user leaves a channel, the channel will be destroyed and its name will be available for re-use.
         *
         * This is a *delete* command.
         *
         * @throws InvalidTokenException If the auth [token] is invalid.
         * @throws NoSuchEntityException If [token] identifies a user who is not a member of [channel], or [channel] does
         * does exist.
         */
        override fun channelPart(token: String, channel: String) = TODO()

        /**
         * Make [username] an operator of this channel. Only existing operators of [channel] and administrators are allowed
         * to make other users operators.
         *
         * This is an *update* command.
         *
         * @throws InvalidTokenException If the auth [token] is invalid.
         * @throws NoSuchEntityException If [channel] does not exist.
         * @throws UserNotAuthorizedException If the user identified by [token] is at least one of the following:
         * 1. Not an operator of [channel] or an administrator,
         * 2. An administrator who is not an operator of [channel] and [username] does not match [token],
         * 3. Not a member of [channel].
         * @throws NoSuchEntityException If [username] does not exist, or if [username] is not a member of [channel].
         */
        override fun channelMakeOperator(token: String, channel: String, username: String) = TODO()

        /**
         * Remove the user [username] from [channel]. Only operators of [channel] may perform this operation.
         *
         * This is an *update* command.
         *
         * @throws InvalidTokenException If the auth [token] is invalid.
         * @throws NoSuchEntityException If [channel] does not exist.
         * @throws UserNotAuthorizedException If [token] is not an operator of this channel.
         * @throws NoSuchEntityException If [username] does not exist, or if [username] is not a member of [channel].
         */
        override fun channelKick(token: String, channel: String, username: String) = TODO()

        /**
         * Indicate [username]'s membership in [channel]. A user is still a member of a channel when logged off.
         *
         * This is a *read* command.
         *
         * @throws InvalidTokenException If the auth [token] is invalid.
         * @throws NoSuchEntityException If [channel] does not exist.
         * @throws UserNotAuthorizedException If [token] is not an administrator or member of this channel.
         * @return True if [username] exists and is a member of [channel], false if it exists and is not a member, and null
         * if it does not exist.
         */
        override fun isUserInChannel(token: String, channel: String, username: String): Boolean? = TODO()

        /**
         * Gets the number of logged-in users in a given [channel].
         *
         * Administrators can query any channel, while regular users can only query channels that they are members of.
         *
         * @throws InvalidTokenException If the auth [token] is invalid.
         * @throws NoSuchEntityException If [channel] does not exist.
         * @throws UserNotAuthorizedException If [token] identifies a user who is not an administrator or is not a member
         * of [channel].
         * @returns Number of logged-in users in [channel].
         */
        override fun numberOfActiveUsersInChannel(token: String, channel: String): Long = TODO()

        /**
         * Gets the number of users in a given [channel].
         *
         * Administrators can query any channel, while regular users can only query channels that they are members of.
         *
         * @throws InvalidTokenException If the auth [token] is invalid.
         * @throws NoSuchEntityException If [channel] does not exist.
         * @throws UserNotAuthorizedException If [token] identifies a user who is not an administrator or is not a member
         * of [channel].
         * @return Number of users, both logged-in and logged-out, in [channel].
         */
        override fun numberOfTotalUsersInChannel(token: String, channel: String): Long = TODO()
}

