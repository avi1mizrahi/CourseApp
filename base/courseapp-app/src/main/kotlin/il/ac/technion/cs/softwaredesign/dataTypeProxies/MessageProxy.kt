package il.ac.technion.cs.softwaredesign.dataTypeProxies

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.Array
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture


class MessageManager @Inject constructor(DB: KeyValueStore) : MessageFactory {

    private val messages = Array(DB.scope("allmessages"))

//    enum class MessageType {
//        BROADCAST,
//        CHANNEL,
//        PRIVATE,
//    }


    override fun create(media: MediaType, contents: ByteArray) : CompletableFuture<Message> {
        val (messageDB, index) = this.messages.newSlot()
        return CompletableFuture.completedFuture(MessageImpl(messageDB, index.toLong(), media, contents))
    }


    fun readMessageFromDB(index : Long) : Message? {
        val messageDB = this.messages[index.toInt()] ?: return null
        return MessageImpl(messageDB, index)
    }



    inner class MessageImpl : Message {
        private val messageDB : ScopedKeyValueStore

        override val id: Long
        override val media: MediaType
        override val contents: ByteArray
        override val created : LocalDateTime
        override var received : LocalDateTime?


        lateinit var messagesource : String

        // New message
        constructor(messageDB: ScopedKeyValueStore, id: Long,
                    media: MediaType, contents: ByteArray) {
            this.messageDB = messageDB
            initPoxies()

            this.id = id
            this.media = media
            this.contents = contents
            this.created = LocalDateTime.now()!!
            this.received = null
            this.write()
        }

        // Read message
        constructor(messageDB: ScopedKeyValueStore, id: Long) {
            this.messageDB = messageDB
            initPoxies()

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
        private fun initPoxies() {
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

