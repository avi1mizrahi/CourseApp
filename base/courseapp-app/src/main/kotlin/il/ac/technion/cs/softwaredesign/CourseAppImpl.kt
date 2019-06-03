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
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.util.concurrent.CompletableFuture


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
            .join()// TODO: remove join
    }
}

class Managers @Inject constructor(db: KeyValueStore) {
    val users = UserManager(db)
    val tokens = TokenManager(db)
    val channels = ChannelManager(db)
}

private fun <T> completedOf(t: T) : CompletableFuture<T> {
    // TODO("remove this method")
    return CompletableFuture.completedFuture(t)
}

class CourseAppImpl @Inject constructor(private val managers: Managers) :
        CourseApp {
    override fun addListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeListener(token: String,
                                callback: ListenerCallback): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun channelSend(token: String,
                             channel: String,
                             message: Message): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun broadcast(token: String, message: Message): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun privateSend(token: String,
                             user: String,
                             message: Message): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun fetchMessage(token: String, id: Long): CompletableFuture<Pair<String, Message>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun getUserByTokenOrThrow(t: String): User {
        return getUserByToken(t) ?: throw InvalidTokenException()
    }

    private fun getUserByToken(t: String): User? {
        val token = managers.tokens.getTokenByString(t) ?: return null
        return managers.users.getUserByID(token.getUserid()!!)
    }

    override fun login(username: String, password: String): CompletableFuture<String> {
        var u = managers.users.getUserByName(username)

        // User does not exist
        if (u == null) {
            u = managers.users.createUser(username, password)
        } else {
            if (u.getPassword() != password)
                throw NoSuchEntityException()

            if (u.isLoggedIn())
                throw UserAlreadyLoggedInException()

            u.forEachChannel { managers.channels.getChannelById(it).addActive(u) }
        }


        val t = managers.tokens.generateNewTokenForUser(u)
        u.logInAndAssignToken(t)
        return completedOf(t.getString())
    }

    override fun logout(token: String): CompletableFuture<Unit> {
        val t = managers.tokens.getTokenByString(token) ?: throw InvalidTokenException()

        val u = managers.users.getUserByID(t.getUserid()!!) // User has to exist, we just checked

        // User must have a token and it must be this token
        assert(u.getCurrentToken()!! == t.getString())

        t.delete()
        u.logout()

        u.forEachChannel { managers.channels.getChannelById(it).removeActive(u) }

        return completedOf(Unit)
    }

    override fun isUserLoggedIn(token: String, username: String): CompletableFuture<Boolean?> {
        // Confirm that token belongs to any user
        managers.tokens.getTokenByString(token) ?: throw InvalidTokenException()
        val u = managers.users.getUserByName(username) ?: return completedOf(null)
        return completedOf(u.isLoggedIn())
    }

    override fun makeAdministrator(token: String, username: String): CompletableFuture<Unit> {
        val oldAdmin = getUserByTokenOrThrow(token)
        if (!oldAdmin.isAdmin()) throw UserNotAuthorizedException()

        val newAdmin = managers.users.getUserByName(username) ?: throw NoSuchEntityException()

        newAdmin.setAdmin()

        return completedOf(Unit)
    }

    override fun channelJoin(token: String, channel: String): CompletableFuture<Unit> {
        val u = getUserByTokenOrThrow(token)

        managers.channels.throwIfBadChannelName(channel)
        var c = managers.channels.getChannelByName(channel)
        if (c == null) { // new channel
            if (!u.isAdmin()) throw UserNotAuthorizedException()

            c = managers.channels.createNewChannel(channel)
            c.addOp(u)
        }

        if (!u.isInChannel(c)) {
            c.addUser(u)
            u.addToChannelList(c)
        }

        return completedOf(Unit)
    }

    override fun channelPart(token: String, channel: String): CompletableFuture<Unit> {
        val u = getUserByTokenOrThrow(token)
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()

        if (u.isInChannel(c)) {
            c.removeUser(u)
            u.removeFromChannelList(c)
        }
        return completedOf(Unit)
    }

    override fun channelMakeOperator(token: String, channel: String, username: String): CompletableFuture<Unit> {
        val actingUser = getUserByTokenOrThrow(token)
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()

        if (!c.isOp(actingUser) && !actingUser.isAdmin()) throw UserNotAuthorizedException()
        if (!c.isOp(actingUser) && actingUser.isAdmin() && actingUser.getName() != username) throw UserNotAuthorizedException()
        if (!c.hasUser(actingUser)) throw UserNotAuthorizedException()

        val targetUser = managers.users.getUserByName(username) ?: throw NoSuchEntityException()
        if (!c.hasUser(targetUser)) throw NoSuchEntityException()

        c.addOp(targetUser)
        return completedOf(Unit)
    }

    override fun channelKick(token: String, channel: String, username: String): CompletableFuture<Unit> {
        val op = getUserByTokenOrThrow(token)
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()
        val targetUser = managers.users.getUserByName(username) ?: throw NoSuchEntityException()
        if (!c.hasUser(targetUser)) throw NoSuchEntityException()

        if (!c.isOp(op)) throw UserNotAuthorizedException()

        c.removeUser(targetUser)
        targetUser.removeFromChannelList(c)
        return completedOf(Unit)
    }

    override fun isUserInChannel(token: String, channel: String, username: String): CompletableFuture<Boolean?> {
        val user = getUserByTokenOrThrow(token)
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()

        if (!c.hasUser(user) && !user.isAdmin()) throw UserNotAuthorizedException()

        val targetUser = managers.users.getUserByName(username) ?: return completedOf(null)
        return completedOf(c.hasUser(targetUser))
    }

    override fun numberOfActiveUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        val user = getUserByTokenOrThrow(token)
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()
        if (!c.hasUser(user) && !user.isAdmin()) throw UserNotAuthorizedException()

        return completedOf(c.getActiveCount().toLong())
    }

    override fun numberOfTotalUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        val user = getUserByTokenOrThrow(token)
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()
        if (!c.hasUser(user) && !user.isAdmin()) throw UserNotAuthorizedException()

        return completedOf(c.getUserCount().toLong())
    }
}


class CourseAppStatisticsImpl @Inject constructor(private val managers: Managers): CourseAppStatistics {
    override fun pendingMessages(): CompletableFuture<Long> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun channelMessages(): CompletableFuture<Long> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun top10ChannelsByMessages(): CompletableFuture<List<String>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun totalUsers(): CompletableFuture<Long> = completedOf(managers.users.getUserCount().toLong())

    override fun loggedInUsers(): CompletableFuture<Long> = completedOf(managers.users.getActiveCount().toLong())

    override fun top10ChannelsByUsers(): CompletableFuture<List<String>> = completedOf(managers.channels.getTop10ChannelsByUserCount())

    override fun top10ActiveChannelsByUsers(): CompletableFuture<List<String>> = completedOf(managers.channels.getTop10ChannelsByActiveUserCount())

    override fun top10UsersByChannels(): CompletableFuture<List<String>> = completedOf(managers.users.getTop10UsersByChannel())
}