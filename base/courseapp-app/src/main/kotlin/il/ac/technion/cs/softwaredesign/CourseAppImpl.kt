package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.dataTypeProxies.ChannelManager
import il.ac.technion.cs.softwaredesign.dataTypeProxies.TokenManager
import il.ac.technion.cs.softwaredesign.dataTypeProxies.UserManager
import il.ac.technion.cs.softwaredesign.dataTypeProxies.UserManager.User
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserAlreadyLoggedInException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
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

class CourseAppImplInitializer @Inject constructor(private val storageFactory: SecureStorageFactory) :
        CourseAppInitializer {
    companion object {
        lateinit var storage: SecureStorage
    }

    override fun setup() {
        storage = storageFactory.open("main".toByteArray())
    }

}

abstract class CourseAppComponent(DB: KeyValueStore) {
    protected var userManager = UserManager(DB)
    protected var tokenManager = TokenManager(DB)
    protected var channelManager = ChannelManager(DB)
}

class CourseAppImpl @Inject constructor(_DB: KeyValueStore) : CourseAppComponent(_DB),
                                                              CourseApp {


    private fun getUserByTokenOrThrow(t: String): User {
        return getUserByToken(t) ?: throw InvalidTokenException()
    }

    private fun getUserByToken(t: String): User? {
        val token = tokenManager.getTokenByString(t) ?: return null
        return userManager.getUserByID(token.getUserid()!!)
    }

    override fun login(username: String, password: String): String {
        var u = userManager.getUserByName(username)

        // User does not exist
        if (u == null) {
            u = userManager.createUser(username, password)
        } else {
            if (u.getPassword() != password)
                throw NoSuchEntityException()

            if (u.isLoggedIn())
                throw UserAlreadyLoggedInException()


            val channels = u.getChannelList()
            channels.forEach { channelID -> channelManager.getChannelById(channelID).addActive(u) }

        }


        val t = tokenManager.generateNewTokenForUser(u)
        u.logInAndAssignToken(t)
        return t.getString()
    }

    override fun logout(token: String) {
        val t = tokenManager.getTokenByString(token) ?: throw InvalidTokenException()

        val u = userManager.getUserByID(t.getUserid()!!) // User has to exist, we just checked

        // User must have a token and it must be this token
        assert(u.getCurrentToken()!! == t.getString())

        t.delete()
        u.logout()

        val channels = u.getChannelList()
        channels.forEach { channelID -> channelManager.getChannelById(channelID).removeActive(u) }
    }

    override fun isUserLoggedIn(token: String, username: String): Boolean? {
        // Confirm that token belongs to any user
        tokenManager.getTokenByString(token) ?: throw InvalidTokenException()
        val u = userManager.getUserByName(username) ?: return null
        return u.isLoggedIn()
    }

    override fun makeAdministrator(token: String, username: String) {
        val oldAdmin = getUserByTokenOrThrow(token)
        if (!oldAdmin.getisAdmin()) throw UserNotAuthorizedException()

        val newAdmin = userManager.getUserByName(username) ?: throw NoSuchEntityException()

        newAdmin.setisAdmin(true)
    }

    override fun channelJoin(token: String, channel: String) {
        val u = getUserByTokenOrThrow(token)

        channelManager.throwIfBadChannelName(channel)
        var c = channelManager.getChannelByName(channel)
        if (c == null) { // new channel
            if (!u.getisAdmin()) throw UserNotAuthorizedException()

            c = channelManager.createNewChannel(channel)
            c.addOp(u)
        }

        if (u.isInChannel(c)) return

        c.addUser(u)
        u.addToChannelList(c)
    }

    override fun channelPart(token: String, channel: String) {
        val u = getUserByTokenOrThrow(token)
        val c = channelManager.getChannelByName(channel) ?: throw NoSuchEntityException()

        if (!u.isInChannel(c)) return

        c.removeUser(u)
        u.removeFromChannelList(c)
    }

    override fun channelMakeOperator(token: String, channel: String, username: String) {
        val actingUser = getUserByTokenOrThrow(token)
        val c = channelManager.getChannelByName(channel) ?: throw NoSuchEntityException()

        if (!c.isOp(actingUser) && !actingUser.getisAdmin()) throw UserNotAuthorizedException()
        if (!c.isOp(actingUser) && actingUser.getisAdmin() && actingUser.getName() != username) throw UserNotAuthorizedException()
        if (!c.hasUser(actingUser)) throw UserNotAuthorizedException()

        val targetUser = userManager.getUserByName(username) ?: throw NoSuchEntityException()
        if (!c.hasUser(targetUser)) throw NoSuchEntityException()

        c.addOp(targetUser)
    }

    override fun channelKick(token: String, channel: String, username: String) {
        val op = getUserByTokenOrThrow(token)
        val c = channelManager.getChannelByName(channel) ?: throw NoSuchEntityException()
        val targetUser = userManager.getUserByName(username) ?: throw NoSuchEntityException()
        if (!c.hasUser(targetUser)) throw NoSuchEntityException()

        if (!c.isOp(op)) throw UserNotAuthorizedException()

        c.removeUser(targetUser)
        targetUser.removeFromChannelList(c)
    }

    override fun isUserInChannel(token: String, channel: String, username: String): Boolean? {
        val user = getUserByTokenOrThrow(token)
        val c = channelManager.getChannelByName(channel) ?: throw NoSuchEntityException()

        if (!c.hasUser(user) && !user.getisAdmin()) throw UserNotAuthorizedException()

        val targetUser = userManager.getUserByName(username) ?: return null
        return c.hasUser(targetUser)
    }

    override fun numberOfActiveUsersInChannel(token: String, channel: String): Long {
        val user = getUserByTokenOrThrow(token)
        val c = channelManager.getChannelByName(channel) ?: throw NoSuchEntityException()
        if (!c.hasUser(user) && !user.getisAdmin()) throw UserNotAuthorizedException()

        return c.getActiveCount().toLong()
    }

    override fun numberOfTotalUsersInChannel(token: String, channel: String): Long {
        val user = getUserByTokenOrThrow(token)
        val c = channelManager.getChannelByName(channel) ?: throw NoSuchEntityException()
        if (!c.hasUser(user) && !user.getisAdmin()) throw UserNotAuthorizedException()

        return c.getUserCount().toLong()
    }


}


class CourseAppStatisticsImpl @Inject constructor(val _DB: KeyValueStore) : CourseAppComponent(_DB),
                                                                            CourseAppStatistics {
    override fun totalUsers(): Long = userManager.getUserCount().toLong()

    override fun loggedInUsers(): Long = userManager.getActiveCount().toLong()

    override fun top10ChannelsByUsers(): List<String> = channelManager.getTop10ChannelsByUserCount()

    override fun top10ActiveChannelsByUsers(): List<String> =
            channelManager.getTop10ChannelsByActiveUserCount()

    override fun top10UsersByChannels(): List<String> = userManager.getTop10UsersByChannel()
}