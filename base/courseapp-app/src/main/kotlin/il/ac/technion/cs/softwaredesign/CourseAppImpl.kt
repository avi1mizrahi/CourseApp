package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.dataTypeProxies.*
import il.ac.technion.cs.softwaredesign.exceptions.*

import il.ac.technion.cs.softwaredesign.dataTypeProxies.UserManager.User
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory


/**
 * This is the class implementing CourseApp, a course discussion group system.
 *
 * You may assume that [CourseAppInitializer.setup] was called before this class was instantiated.
 *
 * Currently specified:
 * + User authentication.
 */

class CourseAppImplInitializer @Inject constructor(val storageFactory: SecureStorageFactory) : CourseAppInitializer {
    companion object {
        var storage : SecureStorage? = null
    }
    override fun setup() {
        storage = storageFactory.open("main".toByteArray())
    }

}

abstract class CourseAppComponent (var DB: KeyValueStore) {
    protected var userManager = UserManager(DB)
    protected var tokenManager = TokenManager(DB)
    protected var channelManager  = ChannelManager(DB)
}

class CourseAppImpl @Inject constructor(val _DB: KeyValueStore): CourseAppComponent(_DB), CourseApp  {


    private fun getUserByTokenOrThrow(t : String) : User {
        return getUserByToken(t) ?: throw InvalidTokenException()
    }

    private fun getUserByToken(t : String) : User? {
        val token = tokenManager.getTokenByString(t) ?: return null
        return userManager.getUserByID(token.getUserid()!!)
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
        var u = userManager.getUserByName(username)

        // User does not exist
        if (u == null) {
            u = userManager.createUser(username, password)
        }
        else {
            if (u.getPassword() != password)
                throw NoSuchEntityException()

            if (u.isLoggedIn())
                throw UserAlreadyLoggedInException()


            val channels = u.getChannelList()
            channels.forEach { channelID -> channelManager.getChannelById(channelID).addActive(u)}

        }


        val t = tokenManager.generateNewTokenForUser(u)
        u.logInAndAssignToken(t)
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
        val t = tokenManager.getTokenByString(token) ?: throw InvalidTokenException()

        val u = userManager.getUserByID(t.getUserid()!!) // User has to exist, we just checked

        // User must have a token and it must be this token
        assert(u.getCurrentToken()!! == t.getString())

        t.delete()
        u.logout()

        val channels = u.getChannelList()
        channels.forEach { channelID -> channelManager.getChannelById(channelID).removeActive(u)}
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
        tokenManager.getTokenByString(token) ?: throw InvalidTokenException()
        val u = userManager.getUserByName(username) ?: return null
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
    override fun makeAdministrator(token: String, username: String) {
        val oldAdmin = getUserByTokenOrThrow(token)
        if (!oldAdmin.getisAdmin()) throw UserNotAuthorizedException()

        val newAdmin = userManager.getUserByName(username) ?: throw NoSuchEntityException()

        newAdmin.setisAdmin(true)
    }

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
    override fun channelJoin(token: String, channel: String) {
        val u = getUserByTokenOrThrow(token)

        channelManager.throwIfBadChannelName(channel)
        var c = channelManager.getChannelByName(channel)
        if (c == null) { // new channel
            if (!u.getisAdmin()) throw UserNotAuthorizedException()

            c = channelManager.createNewChannel(channel)
        }

        if (u.isInChannel(c)) return

        c.addUser(u)
        u.addToChannelList(c)
    }

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
    override fun channelPart(token: String, channel: String) {
        val u = getUserByTokenOrThrow(token)
        val c = channelManager.getChannelByName(channel) ?: throw NoSuchEntityException()

        if (!u.isInChannel(c)) return

        c.removeUser(u)
        u.removeFromChannelList(c)
    }

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
    override fun channelMakeOperator(token: String, channel: String, username: String) {
        val admin = getUserByTokenOrThrow(token)
        val c = channelManager.getChannelByName(channel) ?: throw NoSuchEntityException()
        val targetUser = userManager.getUserByName(username) ?: throw NoSuchEntityException()
        if (!c.hasUser(targetUser)) throw NoSuchEntityException()

        // TODO function description conflicts with given staff test
        // I think they want channel creator to be operator.
        if (!c.isOp(admin) && !admin.getisAdmin()) throw UserNotAuthorizedException()
        if (!c.isOp(admin) && admin.getisAdmin() && admin.getID() != targetUser.getID()) throw UserNotAuthorizedException()
        if (!c.hasUser(admin)) throw UserNotAuthorizedException()

        c.addOp(targetUser)
    }

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
    override fun channelKick(token: String, channel: String, username: String) {
        val op = getUserByTokenOrThrow(token)
        val c = channelManager.getChannelByName(channel) ?: throw NoSuchEntityException()
        val targetUser = userManager.getUserByName(username) ?: throw NoSuchEntityException()
        if (!c.hasUser(targetUser)) throw NoSuchEntityException()


        // TODO conflict between requirements. Test say admin can kick. Description doesn't mention admin
        if (!c.isOp(op) && !op.getisAdmin()) throw UserNotAuthorizedException()

        c.removeUser(targetUser)
        targetUser.removeFromChannelList(c)
    }

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
    override fun isUserInChannel(token: String, channel: String, username: String): Boolean? {
        val user = getUserByTokenOrThrow(token)
        val c = channelManager.getChannelByName(channel) ?: throw NoSuchEntityException()

        if (!c.hasUser(user) && !user.getisAdmin()) throw UserNotAuthorizedException()

        val targetUser = userManager.getUserByName(username) ?: return null
        return c.hasUser(targetUser)
    }

    /**
     * Gets the number of logged-in users in a given [channel].
     *
     * Administrators can query any channel, while regular users can only query channels that they are members of.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [channel] does not exist.
     * @throws UserNotAuthorizedException If [token] identifies a user who is not an administrator and is not a member
     * of [channel].
     * @returns Number of logged-in users in [channel].
     */
    override fun numberOfActiveUsersInChannel(token: String, channel: String): Long {
        val user = getUserByTokenOrThrow(token)
        val c = channelManager.getChannelByName(channel) ?: throw NoSuchEntityException()
        if (!c.hasUser(user) && !user.getisAdmin()) throw UserNotAuthorizedException()

        return c.getActiveCount().toLong()
    }

    /**
     * Gets the number of users in a given [channel].
     *
     * Administrators can query any channel, while regular users can only query channels that they are members of.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [channel] does not exist.
     * @throws UserNotAuthorizedException If [token] identifies a user who is not an administrator and is not a member
     * of [channel].
     * @return Number of users, both logged-in and logged-out, in [channel].
     */
    override fun numberOfTotalUsersInChannel(token: String, channel: String): Long {
        val user = getUserByTokenOrThrow(token)
        val c = channelManager.getChannelByName(channel) ?: throw NoSuchEntityException()
        if (!c.hasUser(user) && !user.getisAdmin()) throw UserNotAuthorizedException()

        return c.getUserCount().toLong()
    }


}


class CourseAppStatisticsImpl @Inject constructor(val _DB: KeyValueStore): CourseAppComponent(_DB), CourseAppStatistics {
    /**
     * Count the total number of users, both logged-in and logged-out, in the system.
     *
     * @return The total number of users.
     */
    override fun totalUsers(): Long = userManager.getUserCount().toLong()

    /**
     * Count the number of logged-in users in the system.
     *
     * @return The number of logged-in users.
     */
    override fun loggedInUsers(): Long = userManager.getActiveCount().toLong()

    /**
     * Return a sorted list of the top 10 channels in the system by user count. The list will be sorted in descending
     * order, so the channel with the highest membership will be first, followed by the second, and so on.
     *
     * If two channels have the same number of users, they will be sorted in ascending lexicographical order.
     *
     * If there are less than 10 channels in the system, a shorter list will be returned.
     *
     * @return A sorted list of channels by user count.
     */
    override fun top10ChannelsByUsers(): List<String> = channelManager.getTop10ChannelsByUserCount()


    /**
     * Return a sorted list of the top 10 channels in the system by logged-in user count. The list will be sorted in
     * descending order, so the channel with the highest active membership will be first, followed by the second, and so
     * on.
     *
     * If two channels have the same number of logged-in users, they will be sorted in ascending lexicographical order.
     *
     * If there are less than 10 channels in the system, a shorter list will be returned.
     *
     * @return A sorted list of channels by logged-in user count.
     */
    override fun top10ActiveChannelsByUsers(): List<String> = channelManager.getTop10ChannelsByActiveUserCount()

    /**
     * Return a sorted list of the top 10 users in the system by channel membership count. The list will be sorted in
     * descending order, so the user who is a member of the most channels will be first, followed by the second, and so
     * on.
     *
     * If two users are members of the same number of channels, they will be sorted in ascending lexicographical order.
     *
     * If there are less than 10 users in the system, a shorter list will be returned.
     *
     * @return A sorted list of users by channel count.
     *
     */
    override fun top10UsersByChannels(): List<String> = userManager.getTop10UsersByChannel()
}