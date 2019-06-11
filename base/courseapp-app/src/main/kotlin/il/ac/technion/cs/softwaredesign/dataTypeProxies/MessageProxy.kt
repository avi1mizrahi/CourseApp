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


private fun isSourceBroadcast(source : String) = source == "BROADCAST"
private fun isSourceChannel(source : String) = source.startsWith("#")
private fun isSourcePrivate(source : String) = !isSourceChannel(source) && !isSourceBroadcast(source)


class MessageManager @Inject constructor(private val DB: KeyValueStore) : MessageFactory {



    private val messages = Array(DB.scope("allmessages"))

    // ordered list of index -> messageID
    private val broadcasts = ArrayInt(DB.scope("broadcasts"))

    // Channel messages counter
    private val totalChannelMessages = DB.getIntReference("totalChannelMessages")

    override fun create(media: MediaType, contents: ByteArray) : CompletableFuture<Message> {
        val (messageDB, index) = this.messages.newSlot()
        return CompletableFuture.completedFuture(MessageImpl(messageDB, index.toLong(), media, contents))
    }


    fun getTotalChannelMessages() = (totalChannelMessages.read() ?: 0).toLong()
    fun addToTotalChannelMessagesCount() = totalChannelMessages.write(getTotalChannelMessages().toInt() + 1)

    fun readMessageFromDB(index : Long) : Message? {

        // TODO there are 2 instances of MessageManager. Their caches are not synchronized.
        this.messages.forceCacheRefresh()
        //


        val messageDB = this.messages[index.toInt()] ?: return null
        return MessageImpl(messageDB, index)
    }


    fun addBroadcastToList(message: Message) {
        broadcasts.push(message.id.toInt())
    }

    inner class MessageListenerManager {

        // TODO this could be cached.
        // Private and broadcast messages
        private val statistics_totalPendingPrivateMessages = DB.getIntReference("totalPendingMessages")

        // Map of UserID -> his callbacks
        private val messageListeners = HashMap<Int, ArrayList<ListenerCallback>>()

        fun getTotalPrivatePending() = (statistics_totalPendingPrivateMessages.read() ?: 0).toLong()


        private fun statistics_addToPendingPrivateAndBroadcastMessages(i : Int) {
            val count = statistics_totalPendingPrivateMessages.read()
            statistics_totalPendingPrivateMessages.write(count?.let { it + i }?: i)
        }

        private fun statistics_removeFromPendingPrivateAndBroadcastMessages(i : Int) {
            val count = statistics_totalPendingPrivateMessages.read()!!
            statistics_totalPendingPrivateMessages.write(count - i)
        }


        fun deliverBroadcastToAllListeners(message : Message, userManager: UserManager) {
            // Statistics stuff
            val totalUnread = userManager.getUserCount() - messageListeners.size
            statistics_addToPendingPrivateAndBroadcastMessages(totalUnread)
            //


            messageListeners.forEach { id, callbacks ->
                val u = userManager.getUserByID(id)
                u.setLastReadBroadcast(broadcasts.count() - 1)

                deliver("BROADCAST", message, callbacks)
            }
        }

        fun deliverToUserOrEnqueuePending(receiver : UserManager.User, source: String, message : Message) {

            var callbacks = messageListeners[receiver.id()]
            if (callbacks != null && !callbacks.isEmpty()) {
                deliver(source, message, callbacks)


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



        private fun deliver(source: String, message : Message, callbacks : List<ListenerCallback>)  {
            callbacks.forEach{callback -> callback(source, message)}
        }


        fun addcallback(u : UserManager.User, callback : ListenerCallback) {
            val id = u.id()
            messageListeners[id] ?: messageListeners.put(id, ArrayList())
            val list = messageListeners[id]!!

            // only callback in list
            if (list.isEmpty()) {

                fun doForEachMessage(id : Int) {
                    val message = readMessageFromDB(id.toLong()) as MessageManager.MessageImpl
                    val source = message.getSource()
                    deliver(source, message, listOf(callback))

                    if (isSourcePrivate(source) || isSourceBroadcast(source)) {
                        statistics_removeFromPendingPrivateAndBroadcastMessages(1)
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

        fun removeCallback(u : UserManager.User, callback : ListenerCallback) {
            val id = u.id()
            if (messageListeners[id]?.remove(callback) != true) throw NoSuchEntityException()
        }
    }

    inner class MessageImpl : Message {
        private val messageDB : KeyValueStore

        override val id: Long
        override val media: MediaType
        override val contents: ByteArray
        override val created : LocalDateTime
        override var received : LocalDateTime?


        lateinit var messagesource : String

        // New message
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


        fun setReadNow() {
            receivedProxy.write(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC).toInt())
        }


        fun setSource(s : String) = sourceProxy.write(s)

        // getSource on an unsent message should be considered a bug
        fun getSource() : String = sourceProxy.read()!!

    }

}

