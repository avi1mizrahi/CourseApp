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

class Managers @Inject constructor(db: KeyValueStore) {
    val users = UserManager(db)
    val tokens = TokenManager(db)
    val channels = ChannelManager(db)
}

class CourseAppImpl @Inject constructor(private val managers: Managers) :
        CourseApp {
    private fun getUserByTokenOrThrow(t: String): User {
        return getUserByToken(t) ?: throw InvalidTokenException()
    }

    private fun getUserByToken(t: String): User? {
        val token = managers.tokens.getTokenByString(t) ?: return null
        return managers.users.getUserByID(token.getUserid()!!)
    }

    override fun login(username: String, password: String): String {
        var u = managers.users.getUserByName(username)

        // User does not exist
        if (u == null) {
            u = managers.users.createUser(username, password)
        } else {
            if (u.getPassword() != password)
                throw NoSuchEntityException()

            if (u.isLoggedIn())
                throw UserAlreadyLoggedInException()

            u.getChannelList().forEach { managers.channels.getChannelById(it).addActive(u) }
        }


        val t = managers.tokens.generateNewTokenForUser(u)
        u.logInAndAssignToken(t)
        return t.getString()
    }

    override fun logout(token: String) {
        val t = managers.tokens.getTokenByString(token) ?: throw InvalidTokenException()

        val u = managers.users.getUserByID(t.getUserid()!!) // User has to exist, we just checked

        // User must have a token and it must be this token
        assert(u.getCurrentToken()!! == t.getString())

        t.delete()
        u.logout()

        val channels = u.getChannelList()
        channels.forEach { managers.channels.getChannelById(it).removeActive(u) }
    }

    override fun isUserLoggedIn(token: String, username: String): Boolean? {
        // Confirm that token belongs to any user
        managers.tokens.getTokenByString(token) ?: throw InvalidTokenException()
        val u = managers.users.getUserByName(username) ?: return null
        return u.isLoggedIn()
    }

    override fun makeAdministrator(token: String, username: String) {
        val oldAdmin = getUserByTokenOrThrow(token)
        if (!oldAdmin.getisAdmin()) throw UserNotAuthorizedException()

        val newAdmin = managers.users.getUserByName(username) ?: throw NoSuchEntityException()

        newAdmin.setisAdmin(true)
    }

    override fun channelJoin(token: String, channel: String) {
        val u = getUserByTokenOrThrow(token)

        managers.channels.throwIfBadChannelName(channel)
        var c = managers.channels.getChannelByName(channel)
        if (c == null) { // new channel
            if (!u.getisAdmin()) throw UserNotAuthorizedException()

            c = managers.channels.createNewChannel(channel)
            c.addOp(u)
        }

        if (u.isInChannel(c)) return

        c.addUser(u)
        u.addToChannelList(c)
    }

    override fun channelPart(token: String, channel: String) {
        val u = getUserByTokenOrThrow(token)
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()

        if (!u.isInChannel(c)) return

        c.removeUser(u)
        u.removeFromChannelList(c)
    }

    override fun channelMakeOperator(token: String, channel: String, username: String) {
        val actingUser = getUserByTokenOrThrow(token)
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()

        if (!c.isOp(actingUser) && !actingUser.getisAdmin()) throw UserNotAuthorizedException()
        if (!c.isOp(actingUser) && actingUser.getisAdmin() && actingUser.getName() != username) throw UserNotAuthorizedException()
        if (!c.hasUser(actingUser)) throw UserNotAuthorizedException()

        val targetUser = managers.users.getUserByName(username) ?: throw NoSuchEntityException()
        if (!c.hasUser(targetUser)) throw NoSuchEntityException()

        c.addOp(targetUser)
    }

    override fun channelKick(token: String, channel: String, username: String) {
        val op = getUserByTokenOrThrow(token)
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()
        val targetUser = managers.users.getUserByName(username) ?: throw NoSuchEntityException()
        if (!c.hasUser(targetUser)) throw NoSuchEntityException()

        if (!c.isOp(op)) throw UserNotAuthorizedException()

        c.removeUser(targetUser)
        targetUser.removeFromChannelList(c)
    }

    override fun isUserInChannel(token: String, channel: String, username: String): Boolean? {
        val user = getUserByTokenOrThrow(token)
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()

        if (!c.hasUser(user) && !user.getisAdmin()) throw UserNotAuthorizedException()

        val targetUser = managers.users.getUserByName(username) ?: return null
        return c.hasUser(targetUser)
    }

    override fun numberOfActiveUsersInChannel(token: String, channel: String): Long {
        val user = getUserByTokenOrThrow(token)
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()
        if (!c.hasUser(user) && !user.getisAdmin()) throw UserNotAuthorizedException()

        return c.getActiveCount().toLong()
    }

    override fun numberOfTotalUsersInChannel(token: String, channel: String): Long {
        val user = getUserByTokenOrThrow(token)
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()
        if (!c.hasUser(user) && !user.getisAdmin()) throw UserNotAuthorizedException()

        return c.getUserCount().toLong()
    }
}


class CourseAppStatisticsImpl @Inject constructor(private val managers: Managers): CourseAppStatistics {
    override fun totalUsers() = managers.users.getUserCount().toLong()

    override fun loggedInUsers() = managers.users.getActiveCount().toLong()

    override fun top10ChannelsByUsers() = managers.channels.getTop10ChannelsByUserCount()

    override fun top10ActiveChannelsByUsers() = managers.channels.getTop10ChannelsByActiveUserCount()

    override fun top10UsersByChannels() = managers.users.getTop10UsersByChannel()
}