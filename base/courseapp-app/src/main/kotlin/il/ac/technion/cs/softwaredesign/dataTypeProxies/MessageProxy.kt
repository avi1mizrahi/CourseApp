package il.ac.technion.cs.softwaredesign.dataTypeProxies

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.Array
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap


private fun isSourceBroadcast(source : String) = source == "BROADCAST"
private fun isSourceChannel(source : String) = source.startsWith("#")
private fun isSourcePrivate(source : String) = !isSourceChannel(source) && !isSourceBroadcast(source)


/**
 * A manager handling message related logic.
 */
class MessageManager @Inject constructor(private val DB: KeyValueStore) : MessageFactory {


    private val messages = Array(DB.scope("allmessages"))

    // ordered list of index -> messageID
    private val broadcasts = ArrayInt(DB.scope("broadcasts"))

    // Channel messages counter
    private val statistics_totalChannelMessages = DB.getIntReference("totalChannelMessages")


    override fun create(media: MediaType, contents: ByteArray) : CompletableFuture<Message> {
        val (messageDB, index) = this.messages.newSlot()
        return CompletableFuture.completedFuture(MessageImpl(messageDB, index.toLong(), media, contents))
    }

    /**
     * Statistics: Return total channel message count
     */
    fun statistics_getTotalChannelMessages() = (statistics_totalChannelMessages.read() ?: 0).toLong()

    /**
     * Statistics: Increment the total message count
     */
    fun statistics_addToTotalChannelMessagesCount() = statistics_totalChannelMessages.write(statistics_getTotalChannelMessages().toInt() + 1)


    /**
     * Read a message from the DB and return an object encapsulating the Message inteface
     * @param index ID of the message to read
     * @return Object with the Message interface or null if ID does not exist
     */
    fun readMessageFromDB(index : Long) : Message? {

//
//        this.messages.forceCacheRefresh()
//

        val messageDB = this.messages[index.toInt()] ?: return null
        return MessageImpl(messageDB, index)
    }

    /**
     * Get an index representing the last broadcast made.
     * This is NOT the message ID.
     */
    fun getLastBroadcastID() = broadcasts.count() - 1

    /**
     * Push a broadcast message to the broadcast list
     */
    fun addBroadcastToList(message: Message) {
        broadcasts.push(message.id.toInt())
    }


    /**
     * A manager handling message listenrs
     */
    inner class MessageListenerManager {

        // TODO this could be cached.
        // Private and broadcast messages
        private val statistics_totalPendingPrivateMessages = DB.getIntReference("totalPendingMessages")

        // Map of UserID -> his callbacks
        private val messageListeners = ConcurrentHashMap<Int, ArrayList<ListenerCallback>>()

        /**
         * Statistics: Get total pending private messages.
         */
        fun statistics_getTotalPrivatePending() = (statistics_totalPendingPrivateMessages.read() ?: 0).toLong()


        // Synchronized allows only one thread at a time access run this function
        @Synchronized
        private fun statistics_addToPendingPrivateAndBroadcastMessages(i : Int) {
            val count = statistics_totalPendingPrivateMessages.read()
            statistics_totalPendingPrivateMessages.write(count?.let { it + i }?: i)
        }


        /**
         * Deliver a broadcast to all listening users
         *
         * @param message to deliver. Must be a broadcast
         * @param userManager a reference to the UserManager.
         * @return A future that completes when the callbacks all complete.
         */
        fun deliverBroadcastToAllListeners(message : Message, userManager: UserManager) : CompletableFuture<Unit> {
            // Statistics stuff
            val totalUnread = userManager.getUserCount() - messageListeners.size
            statistics_addToPendingPrivateAndBroadcastMessages(totalUnread)
            //

            val futures = ArrayList<CompletableFuture<*>>()
            messageListeners.forEach { id, callbacks ->

                (message as MessageImpl).setReadNow() // maximum one read() call

                val u = userManager.getUserByID(id)
                u.setLastReadBroadcast(broadcasts.count() - 1)

                futures.addAll(deliver("BROADCAST", message, callbacks))
            }
            return CompletableFuture.allOf(*futures.toTypedArray()).thenApply { Unit }
        }

        /**
         * Deliver a message to all users of a channel
         *
         *
         * @param channel The channel to send to
         * @param userManager reference to the user manager
         * @param source Source of the message
         * @param message The message
         * @return A future that completes when the callbacks all complete.
         */
        fun sendToChannel(channel : ChannelManager.Channel, userManager: UserManager, source: String, message: Message) : CompletableFuture<Unit> {
            val futures = ArrayList<CompletableFuture<*>>()
            channel.forEachUser{userid ->

                val future = CompletableFuture.runAsync {
                    val receiver = userManager.getUserByID(userid)
                    deliverToUserOrEnqueuePending(receiver, source, message)
                }
                futures.add(future)
            }
            return CompletableFuture.allOf(*(futures.toTypedArray())).thenApply { Unit }
        }


        /**
         * Deliever a message or put it on the user's pending list if its not a broadcast
         *
         *
         *
         * @param reciever the recieving user.
         * @param source Source of the message
         * @param message The message
         */
        fun deliverToUserOrEnqueuePending(receiver : UserManager.User, source: String, message : Message) {

            var callbacks = messageListeners[receiver.id()]
            if (callbacks != null && !callbacks.isEmpty()) {
                (message as MessageImpl).setReadNow()
                CompletableFuture.allOf(*deliver(source, message, callbacks).toTypedArray()).join()


                if (isSourceBroadcast(source))
                    receiver.setLastReadBroadcast(broadcasts.count() - 1)
            }
            else {
                // We enqueue channel and private messages only
                if (isSourcePrivate(source) || isSourceChannel(source)) {
                    receiver.addPendingMessageID(message.id.toInt())
                }

                if (isSourcePrivate(source) || isSourceBroadcast(source)) {
                    statistics_addToPendingPrivateAndBroadcastMessages(1)
                }
            }
        }


        private fun deliver(source: String, message : Message, callbacks : List<ListenerCallback>) : List<CompletableFuture<Unit>>  {
            return callbacks.map{callback -> callback(source, message)}
        }


        /**
         * Adds a message callback to a given user. All pending messages including broadcasts will be sent to the listener
         *
         * @param u the User
         * @param callback the callback to add
         */
        fun addcallback(u : UserManager.User, callback : ListenerCallback) {
            val id = u.id()
            messageListeners[id] ?: messageListeners.put(id, ArrayList())
            val list = messageListeners[id]!!

            // only callback in list
            if (list.isEmpty()) {

                fun doForEachMessage(id : Int) {
                    val message = readMessageFromDB(id.toLong()) as MessageManager.MessageImpl
                    val source = message.getSource()
                    CompletableFuture.allOf(*deliver(source, message, listOf(callback)).toTypedArray()).join()

                    if (isSourcePrivate(source) || isSourceBroadcast(source)) {
                        statistics_addToPendingPrivateAndBroadcastMessages(-1)
                    }
                }

                // Send Broadcasts
                val lastReadBroadcast = u.getLastReadBroadcast()
                broadcasts.forEachFrom({it -> doForEachMessage(it)}, lastReadBroadcast + 1)
                // Send private and channel messages
                u.forEachPendingMessage {it -> doForEachMessage(it)}


                // Clear channel/private messages queue
                u.clearPendingMessages()
                // Set Broadcast to last message read
                u.setLastReadBroadcast(broadcasts.count() - 1)

            }
            list.add(callback)
        }

        /**
         * Remove a callback
         *
         * @param u the User
         * @param callback the callback to add
         */
        fun removeCallback(u : UserManager.User, callback : ListenerCallback) {
            val id = u.id()
            if (messageListeners[id]?.remove(callback) != true) throw NoSuchEntityException()
        }
    }

    /**
     * A class inherting the Message interface with pointers to DB keys.

     */
    inner class MessageImpl : Message {
        private val messageDB : KeyValueStore

        override val id: Long
        override val media: MediaType
        override val contents: ByteArray
        override val created : LocalDateTime
        override var received : LocalDateTime?


        lateinit var messagesource : String

        // New message
        /**
         * This constructor is called when a new message is created.
         * This is intended to only be called by MessageManager.create()
         */
        constructor(messageDB: KeyValueStore, id: Long,
                    media: MediaType, contents: ByteArray) {
            this.messageDB = messageDB
            initProxies()

            this.id = id
            this.media = media
            this.contents = contents
            this.created = LocalDateTime.now()!!
            this.received = null
            this.write()
        }

        // Read message
        /**
         * This constructor reads an existing message from the DB with a given index.
         * This should only be called by the manager's ReadMessageFromDB
         */
        constructor(messageDB: KeyValueStore, id: Long) {
            this.messageDB = messageDB
            initProxies()

            this.id = id
            this.media = MediaType.values()[mediaProxy.read()!!]
            this.contents = contentProxy.read()!!
            this.created = LocalDateTime.ofEpochSecond(createdProxy.read()!!.toLong(), 0, ZoneOffset.UTC)

            this.received = receivedProxy.read()?.let {
                LocalDateTime.ofEpochSecond(it.toLong(), 0, ZoneOffset.UTC)
            }

            this.messagesource = getSource()
        }

        private lateinit var mediaProxy: KeyValueStore.Object<Int>
        private lateinit var createdProxy:KeyValueStore.Object<Int>
        private lateinit var receivedProxy:KeyValueStore.Object<Int>
        private lateinit var contentProxy:KeyValueStore.Object<ByteArray>
        private lateinit var sourceProxy: KeyValueStore.Object<String>
        private fun initProxies() {
            mediaProxy = messageDB.getIntReference("media")
            createdProxy = messageDB.getIntReference("created")
            receivedProxy = messageDB.getIntReference("received")
            contentProxy = messageDB.getByteArrayReference("content")
            sourceProxy = messageDB.getStringReference("source")
        }


        private fun write() {
            assert(mediaProxy.read() == null)
            contentProxy.write(contents)
            mediaProxy.write(media.ordinal)
            createdProxy.write(created.toEpochSecond(ZoneOffset.UTC).toInt()) // Good until 2038.
            //receivedProxy.write() // This is not needed as long as we do not delete messages.
        }


        // Read time must be set to first reader. this bool prevents extra reads.
        private var cachedMessageAlreadyRead = false

        /**
         * Set the message read time to now if its not already set.
         * Uses an internal cache to prevent needless set calls.
         */
        fun setReadNow() {
            if (!cachedMessageAlreadyRead && receivedProxy.read()==null)
            {
                val time = LocalDateTime.now()
                received = time
                receivedProxy.write(time.toEpochSecond(ZoneOffset.UTC).toInt())
                cachedMessageAlreadyRead = true
            }

        }

        /**
         * Set the source of the message and writes it to the DB
         */
        fun setSource(s : String) = sourceProxy.write(s)

        /**
         * Gets the source of the message on the DB or throws if a source isn't set.
         * getSource on an unsent message should be considered a bug
         */

        fun getSource() : String = sourceProxy.read()!!

    }

}

