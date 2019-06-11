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


class MessageManager @Inject constructor(private val DB: KeyValueStore) : MessageFactory {


    private val messages = Array(DB.scope("allmessages"))

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

    inner class MessageListenerManager {

        // Private and broadcast messages
        private val totalPendingPrivateMessages = DB.getIntReference("totalPendingMessages")

        // Map of UserID -> his callbacks
        private val messageListeners = HashMap<Int, ArrayList<ListenerCallback>>()

        fun getTotalPrivatePending() = (totalPendingPrivateMessages.read() ?: 0).toLong()


        private fun addToPendingMessagesCounter(source: String) {
            if (!source.startsWith("#")) {
                val count = totalPendingPrivateMessages.read()
                totalPendingPrivateMessages.write(count?.let { it + 1 }?: 1)
            }
        }

        private fun removeFromPendingMessagesCounter(source: String) {
            if (!source.startsWith("#")) {
                val count = totalPendingPrivateMessages.read()!!
                totalPendingPrivateMessages.write(count - 1)
            }
        }


        fun sendToUserOrEnqueuePending(receiver : UserManager.User, source: String, message : Message) {
            val messageHasBeenRead = send(source, message as MessageImpl, messageListeners[receiver.id()])
            if (!messageHasBeenRead) {
                receiver.addPendingMessageID(message.id.toInt())
                addToPendingMessagesCounter(source)
            }
        }

        // returns if message has been read (if there are callbacks)
        private fun send(source: String, message : MessageImpl, callbacks : List<ListenerCallback>?) : Boolean {
            // if null or empty, return false
            if(callbacks?.isEmpty() != false) return false

            callbacks.forEach{callback -> callback(source, message)}
            message.setReadNow()
            return true
        }



        fun addcallback(u : UserManager.User, callback : ListenerCallback) {
            val id = u.id()
            messageListeners[id] ?: messageListeners.put(id, ArrayList())
            val list = messageListeners[id]!!

            // only callback in list
            if (list.isEmpty()) {
                u.forEachPendingMessage {
                    val message = readMessageFromDB(it.toLong()) as MessageManager.MessageImpl
                    val source = message.getSource()
                    val messageHasBeenRead = send(source, message, listOf(callback))
                    assert(messageHasBeenRead)

                    removeFromPendingMessagesCounter(source)
                }
                u.clearPendingMessages()
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

