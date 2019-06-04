package il.ac.technion.cs.softwaredesign.dataTypeProxies

import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.Array
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture

private const val MESSAGES_IDENTIFIER = "messages"
private const val ALLMESSAGES_IDENTIFIER = "allmessages"



class MessageManager(private val DB: KeyValueStore) : MessageFactory {

    private val messages = Array(ScopedKeyValueStore(DB, listOf(ALLMESSAGES_IDENTIFIER)))

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
        private val mediaProxy = DB.getIntReference("media")
//        private val typeProxy = DB.getIntReference("type")
        private val createdProxy = DB.getIntReference("created")
        private val receivedProxy = DB.getIntReference("received")
        private val contentProxy = DB.getStringReference("content")

        override val id: Long
        override val media: MediaType
        override val contents: ByteArray
        override val created : LocalDateTime
        override var received : LocalDateTime?


        // New message
        constructor(DB: ScopedKeyValueStore, id: Long,
                    media: MediaType, contents: ByteArray) {
            this.id = id
            this.media = media
            this.contents = contents
            this.created = LocalDateTime.now()!!
            this.received = null
            this.write()
        }

        // Read message
        constructor(DB: ScopedKeyValueStore, id: Long) {
            this.id = id
            this.media = MediaType.values()[mediaProxy.read()!!]
            this.contents = contentProxy.read()!!.toByteArray() // TODO read raw bytearray instead of encoding it
            this.created = LocalDateTime.ofEpochSecond(createdProxy.read()!!.toLong(), 0, ZoneOffset.UTC)

            this.received = receivedProxy.read()?.let {
                LocalDateTime.ofEpochSecond(it.toLong(), 0, ZoneOffset.UTC)
            }

        }

        private fun write() {
            assert(mediaProxy.read() == null)
            contentProxy.write(contents.toString()) // TODO write raw bytearray instead of encoding it
            mediaProxy.write(media.ordinal)
            createdProxy.write(created.toEpochSecond(ZoneOffset.UTC).toInt()) // Good until 2038.
            //receivedProxy.write() // This is not needed as long as we do not delete messages.
        }



        fun setReadNow() {
            receivedProxy.write(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC).toInt())
        }



        // Broadcast/private/channel. not sure if we need
//        fun setType(type: MessageType) {
//            typeProxy.write(type.ordinal)
//        }
//        fun getType() : MessageType {
//            return MessageType.values()[typeProxy.read()!!]
//        }


    }

}

