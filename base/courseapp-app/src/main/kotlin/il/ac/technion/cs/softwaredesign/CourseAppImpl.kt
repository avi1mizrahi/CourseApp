package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.dataTypeProxies.ChannelManager
import il.ac.technion.cs.softwaredesign.dataTypeProxies.MessageManager
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
import java.util.concurrent.CompletionException


fun <T> CompletableFuture<T>.joinException(): T {
    try {
        return this.join()
    } catch (e: CompletionException) {
        throw e.cause!!
    }
}

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
        lateinit var managers: Managers
    }

    override fun setup(): CompletableFuture<Unit> {
        storage = storageFactory.open("main".toByteArray()).join()
        managers = Managers(KeyValueStoreImpl(AsyncStorageAdapter(CourseAppImplInitializer.storage)))


        return completedOf(Unit)
    }
}

class Managers @Inject constructor(db: KeyValueStore) {

    val users = UserManager(db.scope("users"))
    val tokens = TokenManager(db.scope("tokens"))
    val channels = ChannelManager(db.scope("channels"))
    val messages = MessageManager(db.scope("messages"))


    val messageListenerManager = messages.MessageListenerManager()

}

private fun <T> completedOf(t: T) : CompletableFuture<T> {
    return CompletableFuture.completedFuture(t)
}


class CourseAppImpl @Inject constructor(private val managers: Managers) :
        CourseApp {

    override fun addListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        return getUserByTokenOrThrow(token).thenApply { u ->
            managers.messageListenerManager.addcallback(u, callback)
        }
    }

    override fun removeListener(token: String,
                                callback: ListenerCallback): CompletableFuture<Unit> {
        return getUserByTokenOrThrow(token).thenApply { u->
            managers.messageListenerManager.removeCallback(u, callback)
        }
    }

    override fun channelSend(token: String,
                             channel: String,
                             message: Message): CompletableFuture<Unit> {
        val u = getUserByTokenOrThrow(token).joinException()
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()
        if (!u.isInChannel(c)) throw UserNotAuthorizedException()

        val source = channel + "@" + u.getName()
        (message as MessageManager.MessageImpl).setSource(source)


        return managers.messageListenerManager.sendToChannel(c, managers.users, source, message)
                .thenApply { managers.messages.statistics_addToTotalChannelMessagesCount()  }
                .thenApply { c.addToMessagesCount() }
    }

    override fun broadcast(token: String, message: Message): CompletableFuture<Unit> {
        val u = getUserByTokenOrThrow(token).joinException()
        if (!u.isAdmin()) throw UserNotAuthorizedException()

        // Make the source string and write it
        val source = "BROADCAST"
        (message as MessageManager.MessageImpl).setSource(source)
        managers.messages.addBroadcastToList(message)

        return managers.messageListenerManager.deliverBroadcastToAllListeners(message, managers.users)
    }

    override fun privateSend(token: String,
                             user: String,
                             message: Message): CompletableFuture<Unit> {
        val sender = getUserByTokenOrThrow(token).joinException()
        val receiver = managers.users.getUserByName(user) ?: throw NoSuchEntityException()

        // Make the source string and write it
        val source = "@" + sender.getName()
        (message as MessageManager.MessageImpl).setSource(source)

        managers.messageListenerManager.deliverToUserOrEnqueuePending(receiver, source, message)

        return completedOf(Unit)
    }

    override fun fetchMessage(token: String, id: Long): CompletableFuture<Pair<String, Message>> {

        lateinit var u : User
        lateinit var message : Message
        lateinit var source : String
        lateinit var c : ChannelManager.Channel


        val getUser = CompletableFuture.runAsync { u = getUserByTokenOrThrow(token).joinException() }
        val getMessageAndChannel = CompletableFuture.runAsync {  message = managers.messages.readMessageFromDB(id) ?: throw NoSuchEntityException() }
                .thenRun{
                    source = (message as MessageManager.MessageImpl).getSource()
                    val channelName = source.split("@")[0]
                    c = managers.channels.getChannelByName(channelName) ?: throw NoSuchEntityException()
                }

        return CompletableFuture.allOf(getUser, getMessageAndChannel)
                .thenApply {
                    if (!c.hasUser(u)) throw UserNotAuthorizedException()

                    Pair(source,message)
                }
    }

    private fun getUserByTokenOrThrow(t: String): CompletableFuture<User> {
        return CompletableFuture.supplyAsync {getUserByToken(t) ?: throw InvalidTokenException()}
    }

    private fun getUserByToken(t: String): User? {
        val token = managers.tokens.getTokenByString(t) ?: return null
        return managers.users.getUserByID(token.getUserid()!!)
    }

    override fun login(username: String, password: String): CompletableFuture<String> {
        return CompletableFuture.supplyAsync {managers.users.getUserByName(username)}
                .thenApply {it ->

                    var u = it
                    if (u == null) {
                        u = managers.users.createUser(username, password)

                        u.setLastReadBroadcast(managers.messages.getLastBroadcastID()) // TODO a bit ugly to put it here

                    } else {
                        if (u.getPassword() != password)
                            throw NoSuchEntityException()

                        if (u.isLoggedIn())
                            throw UserAlreadyLoggedInException()

                        u.forEachChannel { chid -> managers.channels.getChannelById(chid).addActive(u) }
                    }

                    val t = managers.tokens.generateNewTokenForUser(u)
                    u.logInAndAssignToken(t)

                    t.getString()
                }
    }

    override fun logout(token: String): CompletableFuture<Unit> {
        val t = managers.tokens.getTokenByString(token) ?: throw InvalidTokenException()

        val u = managers.users.getUserByID(t.getUserid()!!) // User has to exist, we just checked

        // User must have a token and it must be this token
        //assert(u.getCurrentToken()!! == t.getString())

        t.delete()
        u.logout()

        // Cannot be done async, modifies shared heaps
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
        return getUserByTokenOrThrow(token).thenApply {
            if (!it.isAdmin())
                throw UserNotAuthorizedException()
        }
                .thenApply {managers.users.getUserByName(username) ?: throw NoSuchEntityException()}
                .thenApply {it.setAdmin() }

    }

    override fun channelJoin(token: String, channel: String): CompletableFuture<Unit> {
        val u = getUserByTokenOrThrow(token).joinException()

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
        return getUserByTokenOrThrow(token)
                .thenCombine(CompletableFuture.supplyAsync {
            managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()
        }) { u, c ->
            if (!u.isInChannel(c)) throw NoSuchEntityException()
            Pair(u, c)
        }.thenCompose { (u, c) ->
            CompletableFuture.allOf(
                    CompletableFuture.runAsync { c.removeUser(u) } ,
                    CompletableFuture.runAsync { u.removeFromChannelList(c) }
            )
        }.thenApply { Unit }
    }

    override fun channelMakeOperator(token: String, channel: String, username: String): CompletableFuture<Unit> {
        val actingUser = getUserByTokenOrThrow(token).joinException()
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
        val op = getUserByTokenOrThrow(token).joinException()
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()

        if (!c.isOp(op)) throw UserNotAuthorizedException()

        val targetUser = managers.users.getUserByName(username) ?: throw NoSuchEntityException()
        if (!c.hasUser(targetUser)) throw NoSuchEntityException()

        c.removeUser(targetUser)
        targetUser.removeFromChannelList(c)
        return completedOf(Unit)
    }

    override fun isUserInChannel(token: String, channel: String, username: String): CompletableFuture<Boolean?> {
        val user = getUserByTokenOrThrow(token).joinException()
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()

        if (!c.hasUser(user) && !user.isAdmin()) throw UserNotAuthorizedException()

        val targetUser = managers.users.getUserByName(username) ?: return completedOf(null)
        return completedOf(c.hasUser(targetUser))
    }

    override fun numberOfActiveUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        val user = getUserByTokenOrThrow(token).joinException()
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()
        if (!c.hasUser(user) && !user.isAdmin()) throw UserNotAuthorizedException()

        return completedOf(c.getActiveCount().toLong())
    }

    override fun numberOfTotalUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        val user = getUserByTokenOrThrow(token).joinException()
        val c = managers.channels.getChannelByName(channel) ?: throw NoSuchEntityException()
        if (!c.hasUser(user) && !user.isAdmin()) throw UserNotAuthorizedException()

        return completedOf(c.getUserCount().toLong())
    }


}


class CourseAppStatisticsImpl @Inject constructor(private val managers: Managers): CourseAppStatistics {
    override fun pendingMessages(): CompletableFuture<Long> {
        return CompletableFuture.completedFuture(managers.messageListenerManager.statistics_getTotalPrivatePending())
    }

    override fun channelMessages(): CompletableFuture<Long> {
        return CompletableFuture.completedFuture(managers.messages.statistics_getTotalChannelMessages())
    }

    override fun top10ChannelsByMessages(): CompletableFuture<List<String>> {
        return CompletableFuture.completedFuture(managers.channels.statistics_getTop10ChannelsByMessageCount())

    }

    override fun totalUsers(): CompletableFuture<Long> = completedOf(managers.users.statistics_getUserCount().toLong())

    override fun loggedInUsers(): CompletableFuture<Long> = completedOf(managers.users.statistics_getActiveCount().toLong())

    override fun top10ChannelsByUsers(): CompletableFuture<List<String>> = completedOf(managers.channels.statistics_getTop10ChannelsByUserCount())

    override fun top10ActiveChannelsByUsers(): CompletableFuture<List<String>> = completedOf(managers.channels.statistics_getTop10ChannelsByActiveUserCount())

    override fun top10UsersByChannels(): CompletableFuture<List<String>> = completedOf(managers.users.statistics_getTop10UsersByChannel())
}