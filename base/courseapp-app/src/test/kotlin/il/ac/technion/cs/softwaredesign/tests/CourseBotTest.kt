package il.ac.technion.cs.softwaredesign.tests

import com.authzee.kotlinguice4.KotlinModule
import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.dataTypeProxies.*
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserAlreadyLoggedInException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import java.time.Duration.ofSeconds
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture


fun completedOf(): CompletableFuture<Unit> = CompletableFuture.completedFuture(Unit)
inline fun <reified T> completedOf(t: T): CompletableFuture<T> = CompletableFuture.completedFuture(t)
inline fun <reified T> failedOf(t: Throwable): CompletableFuture<T> = CompletableFuture.failedFuture(t)

class CourseBotTest {
    // We Inject a mocked KeyValueStore and not rely on a KeyValueStore that relies on another DB layer
    private val injector: Injector
    private val app: CourseApp = mockk()
    private val statistics: CourseAppStatistics = mockk()
    private val messageFactory: MessageFactory = mockk()
    private var bots: CourseBots

    // for bots
    class MockStorage : SecureStorage {
        private val encoding = Charsets.UTF_8

        private val keyvalDB = HashMap<String, ByteArray>()

        override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
            val bytes = keyvalDB[key.toString(encoding)]
            if (bytes != null)
                Thread.sleep(bytes.size.toLong())
            return CompletableFuture.completedFuture(bytes)
        }

        override fun write(key: ByteArray, value: ByteArray): CompletableFuture<Unit> {
            keyvalDB[key.toString(encoding)] = value
            return CompletableFuture.completedFuture(Unit)
        }
    }
    class SecureStorageFactoryMock @Inject constructor(val retvalue : SecureStorage) : SecureStorageFactory {
        override fun open(name : ByteArray) : CompletableFuture<SecureStorage> {
            return CompletableFuture.completedFuture(retvalue) // note: name unused
        }
    }
    init {

        class CourseAppModuleMock : KotlinModule() {
            override fun configure() {
                val keystoreinst = VolatileKeyValueStore()

                bind<KeyValueStore>().toInstance(keystoreinst)
                bind<CourseApp>().toInstance(app)
                bind<CourseAppStatistics>().toInstance(statistics)
                bind<MessageFactory>().toInstance(messageFactory)

                bind<SecureStorage>().toInstance(MockStorage())
                bind<SecureStorageFactory>().to<SecureStorageFactoryMock>()
                bind<CourseBots>().to<CourseBotManager>()
            }
        }

        injector = Guice.createInjector(CourseAppModuleMock())
        bots = injector.getInstance()
        bots.start().join()
    }



    private fun newBots() : CourseBots {
        val bots : CourseBots = injector.getInstance()
        bots.start().join()
        return bots
    }



    @Test
    fun `bots exist after restarting`() {
        every { app.login(any(), any()) } returns completedOf("1")
        every { app.addListener(any(), any()) } returns completedOf()

        bots.bot("bot1").join()
        bots.bot("bot2").join()

        val newbots = newBots()

        assertThat(runWithTimeout(ofSeconds(10)) {
            newbots.bots().join()
        }, equalTo(listOf("bot1", "bot2")))
    }

    @Test
    fun `bots reuse old tokens if already logged in`() {
        every { app.login(any(), any()) } returns completedOf("token12345")
        every { app.addListener(any(), any()) } returns completedOf()
        every { app.channelJoin(any(), any()) } returns completedOf()

        bots.bot("bot1").join()

        every { app.login(any(), any()) } throws UserAlreadyLoggedInException()


        val bot = newBots().bot("bot1").join()
        bot.join("#test")

        verify(exactly = 1) {
            app.channelJoin("token12345","#test")
        }
    }


    @Nested
    inner class Channels {
        @BeforeEach
        internal fun setUp() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf()
        }

        @Test
        fun `throws when can't join`() {
            every { app.channelJoin(any(), any()) } returns failedOf(UserNotAuthorizedException())

            val bot = bots.bot().join()

            assertThrows<UserNotAuthorizedException> { bot.join("#WTF").joinException() }
        }

        @Test
        fun `throws when can't part`() {
            every { app.channelPart(any(), any()) } returns failedOf(NoSuchEntityException())

            val bot = bots.bot().join()

            assertThrows<NoSuchEntityException> { bot.part("#WTF").joinException() }
        }

        @Test
        fun `join and part`() {
            every { app.channelJoin(any(), any()) } returns completedOf()
            every { app.channelPart(any(), any()) } returns completedOf()

            val bot = bots.bot().join()
            bot.join("#c").join()

            assertDoesNotThrow { bot.part("#c").joinException() }
        }

        @Test
        fun `join only the asked channels`() {
            every { app.channelJoin("1", "#c1"  ) } returns completedOf()
            every { app.channelJoin("1", "#c7"  ) } returns completedOf()
            every { app.channelJoin("1", "#koko") } returns completedOf()

            bots.bot().thenCompose { bot ->
                bot.join("#c1")
                    .thenCompose { bot.join("#c7") }
                    .thenCompose { bot.join("#koko") }
            }.join()

            verify(exactly = 1) {
                app.channelJoin("1","#c1"  )
                app.channelJoin("1","#c7"  )
                app.channelJoin("1","#koko")
            }

            confirmVerified()
        }

        @Test
        fun `list channels`() {
            every { app.channelJoin(any(), any()) } returns completedOf()

            val theBot = bots.bot().thenCompose { bot ->
                bot.join("#c1")
                    .thenCompose { bot.join("#c2") }
                    .thenCompose { bot.join("#c22") }
                    .thenCompose { bot.join("#c14") }
                    .thenApply { bot }
            }.join()

            // another bot
            bots.bot().thenCompose { bot ->
                bot.join("#c1")
                    .thenCompose { bot.join("#c22222") }
                    .thenCompose { bot.join("#c2111112") }
                    .thenCompose { bot.join("#c14") }
                    .thenApply { bot }
            }.join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                theBot.channels().join()
            }, equalTo(listOf("#c1", "#c2", "#c22", "#c14")))
        }

        @Test
        fun `list channels after restart`() {
            every { app.channelJoin(any(), any()) } returns completedOf()

            val theBot = bots.bot("bot1").thenCompose { bot ->
                bot.join("#c1")
                        .thenCompose { bot.join("#c2") }
                        .thenCompose { bot.join("#c22") }
                        .thenCompose { bot.join("#c14") }
                        .thenApply { bot }
            }.join()


            theBot.channels().join()

            val samebot = newBots().bot("bot1").join()
            assertThat(runWithTimeout(ofSeconds(10)) {
                samebot.channels().join()
            }, equalTo(listOf("#c1", "#c2", "#c22", "#c14")))
        }
    }


    @Nested
    inner class Counter {
        private val listener = slot<ListenerCallback>()
        private val listeners = mutableListOf<ListenerCallback>()

        @BeforeEach
        internal fun setUp() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener("1", capture(listener)) } answers {
                listeners.add(listener.captured)
                completedOf()
            }
        }

        @Test
        fun `beginCount throws IllegalArgumentException on bad input`() {
            val bot = bots.bot().join()

            // TODO: should it throw?
            assertThrows<IllegalArgumentException> {
                bot.beginCount("#hh", null, null).joinException()
            }

            every { app.channelJoin(any(), any()) } returns completedOf()
            bot.join("#hh").join()

            assertThrows<IllegalArgumentException> {
                bot.beginCount(null, null, null).joinException()
            }
        }

        @Test
        fun `count throws IllegalArgumentException without prior begin`() {
            val bot = bots.bot().join()

            assertThrows<IllegalArgumentException> {
                bot.count("#hh", "null", null).joinException()
            }

            every { app.channelJoin(any(), any()) } returns completedOf()
            bot.join("#hh").join()
            bot.beginCount("#hh", "null", null).joinException()

            assertThrows<IllegalArgumentException> {
                bot.count("#hdh", "null", null).joinException()
            }
            assertThrows<IllegalArgumentException> {
                bot.count("#hh", "null", MediaType.TEXT).joinException()
            }
            assertThrows<IllegalArgumentException> {
                bot.count("#hh", null, MediaType.TEXT).joinException()
            }
        }

        @Test
        fun `count with exact specs`() {
            every { app.channelJoin("1", "#ch") } returns completedOf()

            val bot = bots.bot().join()
            bot.join("#ch")
                .thenCompose { bot.beginCount("#ch", "מחט", MediaType.TEXT) }
                .join()

            val msg = mockk<Message>(relaxed = true)

            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "there is מחט here".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            every { msg.id } returns 35
            listeners.forEach { it("#ch@someone", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.count("#ch", "מחט", MediaType.TEXT).join()
            }, equalTo(2L))
        }

        @Test
        fun `count works after restart`() {
            every { app.channelJoin("1", "#ch") } returns completedOf()
            every { app.addListener("1", capture(listener)) } answers {
                completedOf()
            }

            var bot = bots.bot("bot1").join()
            bot.join("#ch")
                    .thenCompose { bot.beginCount("#ch", "take.*me", MediaType.TEXT) }
                    .join()

            // Send message to old bot that he will count
            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 33
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "klj k take !!!!! me !!!".toByteArray()
            listener.invoke("#ch@someone", msg).join()

            // this should overwrite the listener
            bot = newBots().bot("bot1").join()

            // should be counted
            every { msg.id } returns 34
            every { msg.contents } returns "klj k take !!!2!! me !!!".toByteArray()
            listener.invoke("#ch@someone", msg).join()

            // should not be counted
            every { msg.id } returns 35
            every { msg.contents } returns "klj k e !!!!! me take !!!".toByteArray()
            listener.invoke("#ch@someone", msg).join()

            // one before new bot and one after new bot
            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.count("#ch", "take.*me", MediaType.TEXT).join()
            }, equalTo(2L))

        }

        @Test
        fun `restart counter`() {
            every { app.channelJoin(any(), any()) } returns completedOf()

            val bot = bots.bot().join()
            bot.join("#ch")
                .thenCompose { bot.beginCount("#ch", "מחט", MediaType.TEXT) }
                .join()

            val msg = mockk<Message>(relaxed = true)

            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "there is מחט here".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            bot.beginCount("#ch", "מחט", MediaType.TEXT).join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.count("#ch", "מחט", MediaType.TEXT).join()
            }, equalTo(0L))
        }

        @Test
        fun `counter counts after reset`() {
            every { app.channelJoin(any(), any()) } returns completedOf()

            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "there is מחט here".toByteArray()

            val bot = bots.bot().join()
            bot.join("#ch")
                    // start counting
                .thenCompose { bot.beginCount("#ch", "מחט", MediaType.TEXT) }
                    // send 3 messages
                .thenAccept { listeners.forEach { it("#ch@someone", msg).join() } }
                .thenAccept { every { msg.id } returns 111 }
                .thenAccept { listeners.forEach { it("#ch@someone", msg).join() } }
                .thenAccept { every { msg.id } returns 423 }
                .thenAccept { listeners.forEach { it("#ch@someone", msg).join() } }
                    // reset counter
                .thenCompose { bot.beginCount("#ch", "מחט", MediaType.TEXT) }
                    // send 2 messages
                .thenAccept { every { msg.id } returns 343 }
                .thenAccept { listeners.forEach { it("#ch@someoneElse", msg).join() } }
                .thenAccept { every { msg.id } returns 322 }
                .thenAccept { listeners.forEach { it("#ch@someoneWho", msg).join() } }
                .join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.count("#ch", "מחט", MediaType.TEXT).join()
            }, equalTo(2L))
        }

        @Test
        fun `count with regex`() {
            every { app.channelJoin("1", "#ch") } returns completedOf()

            val bot = bots.bot().join()
            bot.join("#ch")
                .thenCompose { bot.beginCount("#ch", "take.*me", MediaType.TEXT) }
                .join()

            val msg = mockk<Message>(relaxed = true)

            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "klj k take !!!!! me !!!".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            every { msg.id } returns 35
            every { msg.contents } returns "klj k e !!!!! me take !!!".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.count("#ch", "take.*me", MediaType.TEXT).join()
            }, equalTo(1L))
        }

        @Test
        fun `count with regex all channels`() {
            every { app.channelJoin("1", any()) } returns completedOf()

            val bot = bots.bot()
                .thenCompose {bot ->
                    bot.join("#ch1")
                        .thenCompose { bot.join("#ch2") }
                        .thenCompose { bot.beginCount(null, "take.*me", MediaType.TEXT) }
                        .thenApply { bot }
                }.join()

            val msg = mockk<Message>(relaxed = true)

            // count this - #1
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "klj k take !!!!! me !!!".toByteArray()
            listeners.forEach { it("#ch1@jjj", msg).join() }

            // count this - #2
            every { msg.id } returns 35
            listeners.forEach { it("#ch2@iii", msg).join() }

            // DON'T count this
            every { msg.id } returns 36
            every { msg.contents } returns "klj k e !!!!! me take !!!".toByteArray()
            listeners.forEach { it("#ch1@kkk", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.count(null, "take.*me", MediaType.TEXT).join()
            }, equalTo(2L))
        }
    }

    @Nested
    inner class Calculator {
        private val listener = slot<ListenerCallback>()
        private val listeners = mutableListOf<ListenerCallback>()

        @BeforeEach
        internal fun setUp() {
            every { app.login(any(), any()) } returns completedOf("yalla")
            every { app.channelJoin(any(), any()) } returns completedOf()
            every { app.addListener(any(), capture(listener)) } answers {
                listeners.add(listener.captured)
                completedOf()
            }
        }

        @Test
        fun `calculates correctly`() {
            val bot = bots.bot().join()
            bot.join("#ch").thenCompose { bot.setCalculationTrigger("pleaseCalc2me") }.join()

            val msg = mockk<Message>(relaxed = true)
            every { messageFactory.create(MediaType.TEXT, "7".toByteArray()) } returns completedOf(msg)
            every { app.channelSend("yalla", "#ch", msg) } returns completedOf()

            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "pleaseCalc2me 3+4".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            every { msg.id } returns 35
            every { msg.contents } returns "3+4".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            verify(exactly = 1) {
                app.channelSend("yalla", "#ch", any())
            }
            confirmVerified()
        }

        @Test
        fun `don't die`() {
            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "3+4 3+4 but this is not a valid expression!!!".toByteArray()

            bots.bot().thenCompose { bot ->
                bot.join("#ch").thenCompose { bot.setCalculationTrigger("3+4") }
            }.join()

            assertDoesNotThrow {
                listeners.forEach { it("#ch@someoneElse", msg).join() }
            }
        }

        @Test
        fun `calculates correctly with complex expression`() {
            val bot = bots.bot().join()
            bot.join("#ch").thenCompose { bot.setCalculationTrigger("pleaseCalc2me") }.join()

            val msg = mockk<Message>(relaxed = true)
            every { messageFactory.create(MediaType.TEXT, "7".toByteArray()) } returns completedOf(msg)
            every { app.channelSend("yalla", "#ch", msg) } returns completedOf()

            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "pleaseCalc2me 3 + (2 * 2 + (9/3) * 0) * (1 + 12 / 12 - 1) + (-2 + 2) - (-14/-2 + -7)".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            verify(exactly = 1) {
                app.channelSend("yalla", "#ch", any())
            }
            confirmVerified()
        }

        @Test
        fun `calculates correctly with tricky trigger`() {
            val bot = bots.bot().join()
            bot.join("#ch").thenCompose { bot.setCalculationTrigger("#ch") }.join()

            val msg = mockk<Message>(relaxed = true)
            every { messageFactory.create(MediaType.TEXT, "7".toByteArray()) } returns completedOf(msg)
            every { app.channelSend("yalla", "#ch", msg) } returns completedOf()

            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "#ch 3+4".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            verify(exactly = 1) {
                app.channelSend("yalla", "#ch", any())
            }
            confirmVerified()
        }

        @Test
        fun `works after restart`() {
            val bot = bots.bot("kaka").join()
            bot.join("#ch").thenCompose { bot.setCalculationTrigger("pleaseCalc2me") }.join()

            listeners.clear()
            // this should overwrite the listener
            newBots().bot("kaka").join()

            val msg = mockk<Message>(relaxed = true)
            every { messageFactory.create(MediaType.TEXT, "7".toByteArray()) } returns completedOf(msg)
            every { app.channelSend("yalla", "#ch", msg) } returns completedOf()

            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "pleaseCalc2me 3+4".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            verify(exactly = 1) {
                app.channelSend("yalla", "#ch", any())
            }
            confirmVerified()
        }

        @Test
        fun `can be turned off`() {
            val bot = bots.bot().join()
            bot.join("#ch").thenCompose { bot.setCalculationTrigger("pleaseCalc2me") }.join()

            val msg = mockk<Message>(relaxed = true)
            every { messageFactory.create(MediaType.TEXT, "7".toByteArray()) } returns completedOf(msg)
            every { app.channelSend("yalla", "#ch", msg) } returns completedOf()

            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "pleaseCalc2me 3+4".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            // turn off
            bot.setCalculationTrigger(null).join()
            // don't count this
            every { msg.id } returns 2424
            listeners.forEach { it("#ch@someone", msg).join() }

            verify(exactly = 1) {
                app.channelSend("yalla", "#ch", any())
            }
            confirmVerified()
        }

        @Test
        fun `works after turning on again`() {
            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "3+4 3+4".toByteArray()
            every { messageFactory.create(MediaType.TEXT, "7".toByteArray()) } returns completedOf(msg)
            every { app.channelSend("yalla", "#ch", msg) } returns completedOf()

            bots.bot().thenCompose { bot ->
                bot.join("#ch")
                    // start
                    .thenCompose { bot.setCalculationTrigger("3+4") }
                    // turn off-on
                    .thenCompose { bot.setCalculationTrigger(null) }
                    .thenCompose { bot.setCalculationTrigger("3+4") }
                    // calc
                    .thenAccept { every { msg.id } returns 343 }
                    .thenAccept { listeners.forEach { it("#ch@someoneElse", msg).join() } }
            }.join()

            verify(exactly = 1) {
                app.channelSend("yalla", "#ch", any())
            }
            confirmVerified()
        }

        @Test
        fun `works in all channels`() {
            bots.bot().thenCompose { bot ->
                bot.join("#ch1")
                    .thenCompose { bot.join("#ch2") }
                    .thenCompose { bot.setCalculationTrigger("3+4") }
                    .thenApply { bot }
            }.join()

            val msg = mockk<Message>(relaxed = true)
            every { msg.media } returns MediaType.TEXT
            every { messageFactory.create(MediaType.TEXT, "7".toByteArray()) } returns completedOf(msg)
            every { app.channelSend(any(), any(), msg) } returns completedOf()

            // calc this - #1
            every { msg.id } returns 34
            every { msg.contents } returns "3+4 3+4".toByteArray()
            listeners.forEach { it("#ch1@jjj", msg).join() }

            // calc this - #2
            every { msg.id } returns 35
            listeners.forEach { it("#ch2@iii", msg).join() }

            // DON'T calc this
            every { msg.id } returns 36
            every { msg.contents } returns "3+ 4 3+4".toByteArray()
            listeners.forEach { it("#ch1@kkk", msg).join() }

            verify(exactly = 1) {
                app.channelSend("yalla", "#ch1", any())
                app.channelSend("yalla", "#ch2", any())
            }
            confirmVerified()
        }
    }

    @Nested
    inner class Tipping {
        private val listener = slot<ListenerCallback>()
        private val listeners = mutableListOf<ListenerCallback>()
        private lateinit var bot: CourseBot

        private val theToken = "** !ya_habibi_yalla! **"

        @BeforeEach
        internal fun setUp() {
            every { app.login(any(), any()) } returns completedOf(theToken)
            every { app.channelJoin(theToken, any()) } returns completedOf()
            every { app.addListener(theToken, capture(listener)) } answers {
                listeners.add(listener.captured)
                completedOf()
            }

            bot = bots.bot().join()
        }

        @Test
        fun `get tip, become rich`() {
            bot.join("#ch").thenCompose { bot.setTipTrigger("gimmeTha$") }.join()

            every { app.isUserInChannel(theToken, "#ch", "richest") } returns completedOf(true)

            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "gimmeTha$ 1 richest".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch").join()
            }, equalTo("richest"))
        }

        @Test
        fun `paying to someone the most makes him richest`() {
            bot.join("#ch").thenCompose { bot.setTipTrigger("gimmeTha$") }.join()

            every { app.isUserInChannel(theToken, any(), any()) } returns completedOf(true)

            val msg = mockk<Message>(relaxed = true)
            every { msg.media } returns MediaType.TEXT

            every { msg.id } returns 34
            every { msg.contents } returns "gimmeTha$ 999 richest".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            every { msg.id } returns 35
            every { msg.contents } returns "gimmeTha$ 1 not richest".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch").join()
            }, equalTo("richest"))
        }

        @Test
        fun `richestUser returns null on tie`() {
            bot.join("#ch").thenCompose { bot.setTipTrigger("gimmeTha$") }.join()

            every { app.isUserInChannel(theToken, any(), any()) } returns completedOf(true)

            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "gimmeTha$ 150 richest1".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            every { msg.id } returns 3324
            every { msg.contents } returns "gimmeTha$ 150 richest2".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch").join()
            }, absent())
        }

        @Test
        fun `at the beggining, communism`() {
            bot.join("#ch").join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch").join()
            }, absent())
        }

        @Test
        fun `at the beggining, communism part 2`() {
            bot.join("#ch").thenCompose { bot.setTipTrigger("gimmeTha$") }.join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch").join()
            }, absent())
        }

        @Test
        fun `richestUser throws NoSuchEntityException if bot not in channel`() {
            assertThrows<NoSuchEntityException> {
                bot.richestUser("#ch").joinException()
            }

            bot.setTipTrigger("gimmeTha$").join()

            assertThrows<NoSuchEntityException> {
                bot.richestUser("#ch").joinException()
            }
        }

        @Test
        fun `no user, no money`() {
            bot.join("#ch").thenCompose { bot.setTipTrigger("gimmeTha$") }.join()

            every { app.isUserInChannel(theToken, "#ch", "stranger") } returns completedOf(false)
            every { app.isUserInChannel(theToken, "#ch", "who is this") } returns completedOf(null)
            every { app.isUserInChannel(theToken, "#ch", "richest") } returns completedOf(true)

            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "gimmeTha$ 100 stranger".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            every { msg.id } returns 3431
            every { msg.contents } returns "gimmeTha$ 1 richest".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            every { msg.id } returns 22222
            every { msg.contents } returns "gimmeTha$ 100 who is this".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch").join()
            }, equalTo("richest"))
        }

        @Test
        fun `don't die`() {
            bot.join("#ch").thenCompose { bot.setTipTrigger("gimmeTha$") }.join()

            every { app.isUserInChannel(theToken, "#ch", "richest") } returns completedOf(true)

            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "gimmeTha$ poorest 100".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            every { msg.id } returns 3431
            every { msg.contents } returns "gimmeTha$ c1 poorest".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            every { msg.id } returns 3433
            every { msg.contents } returns "gimmeTha$ 1 richest".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            every { msg.id } returns 22222
            every { msg.contents } returns "gimmeTha$ 10c0 poorest".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch").join()
            }, equalTo("richest"))
        }

        @Test
        fun `setTipTrigger returns previous trigger phrase`() {
            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.setTipTrigger("gimmeTha$").join()
            }, absent())
            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.setTipTrigger("something else").join()
            }, equalTo("gimmeTha$"))
            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.setTipTrigger(null).join()
            }, equalTo("something else"))
            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.setTipTrigger("bye").join()
            }, absent())
        }

        @Test
        fun `works after restart`() {
            bot.join("#ch").thenCompose { bot.setTipTrigger("gimmeTha$") }.join()

            every { app.isUserInChannel(theToken, "#ch", "richest") } returns completedOf(true)
            every { app.isUserInChannel(theToken, "#ch", "ככה ככה") } returns completedOf(true)

            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "gimmeTha$ 2 richest".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            listeners.clear()
            // this should overwrite the listener
            newBots().bot("kaka").join()

            every { msg.id } returns 342
            every { msg.contents } returns "gimmeTha$ 1 ככה ככה".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch").join()
            }, equalTo("richest"))
        }

        @Test
        fun `can be turned off`() {
            bot.join("#ch").thenCompose { bot.setTipTrigger("gimmeTha$") }.join()

            every { app.isUserInChannel(theToken, "#ch", any()) } returns completedOf(true)

            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "gimmeTha$ 2 richest".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            bot.setTipTrigger(null).join()

            // rich cannot give back, tipping is disabled
            every { msg.id } returns 342
            every { msg.contents } returns "gimmeTha$ 4 poorest".toByteArray()
            listeners.forEach { it("#ch@richest", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch").join()
            }, equalTo("richest"))
        }

        @Test
        fun `works after turning on again`() {
            bot.join("#ch").thenCompose { bot.setTipTrigger("gimmeTha$") }.join()

            every { app.isUserInChannel(theToken, "#ch", any()) } returns completedOf(true)

            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "gimmeTha$ 2 richest".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            bot.setTipTrigger(null).join()
            bot.setTipTrigger("triggered!!!!").join()

            every { msg.id } returns 342
            every { msg.contents } returns "triggered!!!! 3 new richest".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch").join()
            }, equalTo("new richest"))
        }

        @Test
        fun `can't send more then I have`() {
            bot.join("#ch").thenCompose { bot.setTipTrigger("gimmeTha$") }.join()

            every { app.isUserInChannel(theToken, "#ch", "richest") } returns completedOf(true)

            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "gimmeTha$ 1001 richest".toByteArray()
            listeners.forEach { it("#ch@poorest", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch").join()
            }, absent())
        }

        @Test
        fun `each channel has different ledger`() {
            every { app.isUserInChannel(theToken, "#ch1", "A") } returns completedOf(true)
            every { app.isUserInChannel(theToken, any(), "B") } returns completedOf(true)
            every { app.isUserInChannel(theToken, "#ch2", "C") } returns completedOf(true)

            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT

            bot.setTipTrigger("$")
                .thenCompose { bot.join("#ch1") }
                .thenCompose { bot.join("#ch2") }
                // Balances now:
                // ch1: A=1000, B=1000
                // ch2:         B=1000, C=1000
                .thenAccept {
                    every { msg.contents } returns "$ 1000 B".toByteArray()
                    listeners.forEach { it("#ch1@A", msg).join() }
                }
                // Balances now:
                // ch1: A=0, B=2000
                // ch2:      B=1000, C=1000
                .thenAccept {// this should do nothing
                    every { msg.id } returns 3253
                    every { msg.contents } returns "$ 2000 C".toByteArray()
                    listeners.forEach { it("#ch2@B", msg).join() }
                }.thenAccept {
                    every { msg.id } returns 3253
                    every { msg.contents } returns "$ 1 B".toByteArray()
                    listeners.forEach { it("#ch2@C", msg).join() }
                }
                // Balances now:
                // ch1: A=0, B=2000
                // ch2:      B=1001, C=999
                .join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch1").join()
            }, equalTo("B"))
            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch2").join()
            }, equalTo("B"))
        }

        @Test
        fun `transfers between few`() {
            every { app.isUserInChannel(theToken, any(), any()) } returns completedOf(true)

            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT

            bot.join("#ch")
                .thenCompose { bot.setTipTrigger("$") }
                // Balances now: A=1000, B=1000, C=1000, D=1000
                .thenAccept {
                    every { msg.contents } returns "$ 1 B".toByteArray()
                    listeners.forEach { it("#ch@A", msg).join() }
                }
                // Balances now: A=999, B=1001, C=1000, D=1000
                .thenAccept {// should fail
                    every { msg.contents } returns "$ 1001 B".toByteArray()
                    listeners.forEach { it("#ch@D", msg).join() }
                }
                // Balances now: A=999, B=1001, C=1000, D=1000
                .thenAccept {
                    every { msg.contents } returns "$ 2 C".toByteArray()
                    listeners.forEach { it("#ch@D", msg).join() }
                }
                // Balances now: A=999, B=1001, C=1002, D=998
                .join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.richestUser("#ch").join()
            }, equalTo("C"))
        }
    }

    @Nested
    inner class Stalker {
        private val listener = slot<ListenerCallback>()
        private val listeners = mutableListOf<ListenerCallback>()
        private lateinit var bot: CourseBot

        private val theToken = "עד מתי תעשה שיגמר"

        @BeforeEach
        internal fun setUp() {
            every { app.login(any(), any()) } returns completedOf(theToken)
            every { app.channelJoin(theToken, any()) } returns completedOf()
            every { app.addListener(theToken, capture(listener)) } answers {
                listeners.add(listener.captured)
                completedOf()
            }

            bot = bots.bot().join()
        }

        @Test
        fun `seenTime from unseen user`() {
            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "$ 1 B".toByteArray()

            bot.join("#ch")
                .thenAccept { listeners.forEach { it("#ch@A", msg).join() } }
                .join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.seenTime("who is this user").join()
            }, absent())
        }

        @Test
        fun `seenTime reports correctly`() {
            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "$ 1 B".toByteArray()
            val time = LocalDateTime.of(1991, 11, 11, 11, 11)
            every { msg.created } returns time

            bot.join("#ch")
                .thenAccept { listeners.forEach { it("#ch@A", msg).join() } }
                .join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.seenTime("A").join()
            }, equalTo(time))
        }

        @Test
        fun `seenTime reports correctly with multiple channels`() {
            bot.join("#ch4")
                .thenCompose { bot.join("#ch1") }
                .thenCompose { bot.join("#ch2") }
                .thenCompose { bot.join("#ch3") }
                .join()

            val msg = mockk<Message>(relaxed = true)
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "$ 1 B".toByteArray()
            every { msg.id } returns 34
            every { msg.created } returns LocalDateTime.of(1991, 11, 11, 11, 11)

            listeners.forEach { it("#ch1@A", msg).join() }
            every { msg.id } returns 35
            every { msg.created } returns LocalDateTime.of(1992, 11, 11, 11, 11)
            listeners.forEach { it("#ch2@A", msg).join() }
            every { msg.id } returns 36
            every { msg.created } returns LocalDateTime.of(1993, 11, 11, 11, 11)
            listeners.forEach { it("#ch1@A", msg).join() }
            every { msg.id } returns 37
            every { msg.created } returns LocalDateTime.of(1994, 11, 11, 11, 11)
            listeners.forEach { it("#ch3@A", msg).join() }
            every { msg.id } returns 38
            every { msg.created } returns LocalDateTime.of(1995, 11, 11, 11, 11)
            listeners.forEach { it("#ch2@F", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.seenTime("A").join()
            }, equalTo(LocalDateTime.of(1994, 11, 11, 11, 11)))
            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.seenTime("F").join()
            }, equalTo(LocalDateTime.of(1995, 11, 11, 11, 11)))
        }

        @Test
        fun `mostActiveUser throws NoSuchEntityException if not in any channel`() {
            assertThrows<NoSuchEntityException> { bot.mostActiveUser("#ch413!").joinException() }
        }

        @Test
        fun `mostActiveUser throws NoSuchEntityException if not in the channel`() {
            bot.join("#ch4").join()

            assertThrows<NoSuchEntityException> { bot.mostActiveUser("#ch413!").joinException() }
        }

        @Test
        fun `mostActiveUser reports correctly with multiple channels`() {
            bot.join("#ch4")
                .thenCompose { bot.join("#ch3") }
                .join()

            val msg = mockk<Message>(relaxed = true)
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "$ 1 B".toByteArray()
            every { msg.id } returns 34
            every { msg.created } returns LocalDateTime.of(1991, 11, 11, 11, 11)

            listeners.forEach { it("#ch4@F", msg).join() }
            every { msg.id } returns 35
            every { msg.created } returns LocalDateTime.of(1992, 11, 11, 11, 11)
            listeners.forEach { it("#ch3@A", msg).join() }
            every { msg.id } returns 36
            every { msg.created } returns LocalDateTime.of(1993, 11, 11, 11, 11)
            listeners.forEach { it("#ch4@A", msg).join() }
            every { msg.id } returns 37
            every { msg.created } returns LocalDateTime.of(1994, 11, 11, 11, 11)
            listeners.forEach { it("#ch3@A", msg).join() }
            every { msg.id } returns 38
            every { msg.created } returns LocalDateTime.of(1995, 11, 11, 11, 11)
            listeners.forEach { it("#ch4@F", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.mostActiveUser("#ch3").join()
            }, equalTo("A"))
            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.mostActiveUser("#ch4").join()
            }, equalTo("F"))
        }
    }

    @Nested
    inner class Survey {
        private val listener = slot<ListenerCallback>()
        private val listeners = mutableListOf<ListenerCallback>()
        private lateinit var bot: CourseBot

        private val theToken = "חלאס"

        @BeforeEach
        internal fun setUp() {
            every { app.login(any(), any()) } returns completedOf(theToken)
            every { app.channelJoin(theToken, any()) } returns completedOf()
            every { app.addListener(theToken, capture(listener)) } answers {
                listeners.add(listener.captured)
                completedOf()
            }

            bot = bots.bot().join()
        }

        @Test
        fun `runSurvey throws NoSuchEntityException if the bot is not in channel`() {
            assertThrows<NoSuchEntityException> {
                bot.runSurvey("#cah", "?", listOf("A", "B")).joinException()
            }
        }

        @Test
        fun `surveyResults throws NoSuchEntityException if unknown survey`() {
            assertThrows<NoSuchEntityException> {
                bot.surveyResults("asdff").joinException()
            }
        }

        @Test
        fun `runSurvey as surveys should be`() {
            val msg = mockk<Message>(relaxed = true)
            every { msg.media } returns MediaType.TEXT
            every { msg.id } returns 100
            every { msg.contents } returns "בעד?".toByteArray()

            every { messageFactory.create(MediaType.TEXT, "בעד?".toByteArray()) } returns completedOf(msg)
            every { app.channelSend(any(), any(), any()) } returns completedOf()

            val sid = bot.join("#cha")
                .thenCompose { bot.runSurvey("#cha", "בעד?", listOf("כן", "בעד", "סגר סגור")) }
                .thenApply { identifier ->
                    every { msg.id } returns 1001
                    listeners.forEach { it("#cha@A", msg).join() } // not survey related

                    every { msg.id } returns 1002
                    every { msg.contents } returns "סגר סגור".toByteArray()
                    listeners.forEach { it("#cha@A", msg).join() }

                    every { msg.id } returns 1003
                    every { msg.contents } returns "כן".toByteArray()
                    listeners.forEach { it("#cha@B", msg).join() }

                    every { msg.id } returns 1004
                    every { msg.contents } returns "סגר סגור".toByteArray()
                    listeners.forEach { it("#cha@C", msg).join() }

                    every { msg.id } returns 10033
                    every { msg.contents } returns "כן".toByteArray()
                    listeners.forEach { it("#cha@D", msg).join() }

                    identifier
                }.join()

            assertThat(bot.surveyResults(sid).join(), equalTo(listOf(2L, 0L, 2L)))

            verify(exactly = 1) { app.channelSend(theToken, "#cha", msg) }
            confirmVerified()
        }

        @Test
        fun `user can override answer`() {
            val msg = mockk<Message>(relaxed = true)
            every { msg.media } returns MediaType.TEXT
            every { msg.id } returns 100
            every { msg.contents } returns "בעד?".toByteArray()

            every { messageFactory.create(MediaType.TEXT, "בעד?".toByteArray()) } returns completedOf(msg)
            every { app.channelSend(any(), any(), any()) } returns completedOf()

            val sid = bot.join("#cha")
                .thenCompose { bot.runSurvey("#cha", "בעד?", listOf("כן", "בעד", "סגר סגור")) }
                .thenApply { identifier ->
                    every { msg.id } returns 1001
                    listeners.forEach { it("#cha@A", msg).join() } // not survey related

                    every { msg.id } returns 1002
                    every { msg.contents } returns "סגר סגור".toByteArray()
                    listeners.forEach { it("#cha@A", msg).join() }

                    every { msg.id } returns 1003
                    every { msg.contents } returns "כן".toByteArray()
                    listeners.forEach { it("#cha@A", msg).join() }

                    every { msg.id } returns 1004
                    every { msg.contents } returns "סגר סגור".toByteArray()
                    listeners.forEach { it("#cha@C", msg).join() }

                    every { msg.id } returns 1009
                    every { msg.contents } returns "סגר סגור".toByteArray()
                    listeners.forEach { it("#cha@C", msg).join() }

                    every { msg.id } returns 10033
                    every { msg.contents } returns "כן".toByteArray()
                    listeners.forEach { it("#cha@A", msg).join() }

                    identifier
                }.join()

            assertThat(bot.surveyResults(sid).join(), equalTo(listOf(1L, 0L, 1L)))

            verify(exactly = 1) { app.channelSend(theToken, "#cha", msg) }
            confirmVerified()
        }
    }

    @Nested
    inner class General {

        @Test
        fun `default name`() {
            every { app.login("Anna0", any()) } returns completedOf("1")
            every { app.login("Anna1", any()) } returns completedOf("2")
            every { app.login("Anna2", any()) } returns completedOf("3")

            every { app.addListener("1", any()) } returns completedOf()
            every { app.addListener("2", any()) } returns completedOf()
            every { app.addListener("3", any()) } returns completedOf()

            every { app.channelJoin("1", any()) } returns completedOf()
            every { app.channelJoin("2", any()) } returns completedOf()
            every { app.channelJoin("3", any()) } returns completedOf()

            bots.bot().thenCompose { it.join("#c1") }.join()
            bots.bot().thenCompose { it.join("#c1") }.join()
            bots.bot("Anna0").thenCompose { it.join("#c1") }.join()
            bots.bot().thenCompose { it.join("#c1") }.join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bots.bots("#c1").join()
            }, equalTo(listOf("Anna0", "Anna1", "Anna2")))

            verify(atLeast = 3) {
                app.login(any(), any())
                app.addListener(any(), any())
                app.channelJoin(any(), any())
            }

            confirmVerified()
        }

        @Test
        fun `custom name`() {
            every { app.login("beni sela", any()) } returns completedOf("1")

            every { app.addListener("1", any()) } returns completedOf()
            every { app.channelJoin("1", any()) } returns completedOf()

            bots.bot("beni sela").thenCompose { it.join("#c1") }.join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bots.bots("#c1").join()
            }, equalTo(listOf("beni sela")))

            verify(atLeast = 1) {
                app.login(any(), any())
                app.addListener(any(), any())
                app.channelJoin(any(), any())
            }

            confirmVerified()
        }

        @Test
        fun `list bots from all channels`() {
            every { app.login("Anna0", any()) } returns completedOf("1")
            every { app.login("Anna1", any()) } returns completedOf("2")
            every { app.login("Anna2", any()) } returns completedOf("3")
            every { app.login("Anna3", any()) } returns completedOf("4")

            every { app.addListener(any(), any()) } returns completedOf()
            every { app.channelJoin(any(), any()) } returns completedOf()

            bots.bot().thenCompose { it.join("#c1") }.join()
            bots.bot().thenCompose { it.join("#c2") }.join()
            bots.bot().thenCompose { it.join("#c1") }.join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bots.bots().join()
            }, equalTo(listOf("Anna0", "Anna1", "Anna2")))
        }

        @Disabled
        @Test
        fun `can be kicked out from channels, statistics should handle`() {
            every { app.login(any(), any()) } returns completedOf("7yhnm")
            every { app.addListener(any(), any()) } returns completedOf()
            every { app.channelJoin(any(), any()) } returns completedOf()

            val bot = bots.bot("botox").join()
            bot.join("cha-cha-cha").join()

            // shabang!
            every { app.isUserInChannel(any(), any(), any()) } returns completedOf(false)

            assertThrows<NoSuchEntityException> {
                bot.richestUser("cha-cha-cha").joinException()
            }
            assertThrows<NoSuchEntityException> {
                bot.mostActiveUser("cha-cha-cha").joinException()
            }
            assertThrows<NoSuchEntityException> {
                bot.runSurvey("cha-cha-cha",
                              "cha-cha-cha",
                              listOf("cha-cha-cha", "cha-pa-cha")).joinException()
            }
        }
    }
}

class CourseBotStaffTest {
    // We Inject a mocked KeyValueStore and not rely on a KeyValueStore that relies on another DB layer
    private val injector: Injector
    private val app: CourseApp // TODO: use mock instead of our full impl?
    private val statistics: CourseAppStatistics // TODO: same
    private val messageFactory: MessageFactory // TODO: same
    private val bots: CourseBots

    init {
        class CourseAppModuleMock : KotlinModule() {
            override fun configure() {
                val keystoreinst = VolatileKeyValueStore()


                bind<MessageFactory>().to<MessageManager>().`in`<Singleton>()


                bind<KeyValueStore>().toInstance(keystoreinst)
                bind<CourseApp>().to<CourseAppImpl>()
                bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()


                // Bots
                class MockStorage : SecureStorage {
                    private val encoding = Charsets.UTF_8

                    private val keyvalDB = HashMap<String, ByteArray>()

                    override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
                        val bytes = keyvalDB[key.toString(encoding)]
                        if (bytes != null)
                            Thread.sleep(bytes.size.toLong())
                        return CompletableFuture.completedFuture(bytes)
                    }

                    override fun write(key: ByteArray, value: ByteArray): CompletableFuture<Unit> {
                        keyvalDB[key.toString(encoding)] = value
                        return CompletableFuture.completedFuture(Unit)
                    }
                }

                class SecureStorageFactoryMock : SecureStorageFactory {
                    override fun open(name : ByteArray) : CompletableFuture<SecureStorage> {
                        return CompletableFuture.completedFuture(MockStorage())
                    }
                }
                bind<SecureStorageFactory>().to<SecureStorageFactoryMock>()
                bind<CourseBots>().to<CourseBotManager>()
            }
        }

        injector = Guice.createInjector(CourseAppModuleMock())
        app = injector.getInstance()
        statistics = injector.getInstance()
        messageFactory = injector.getInstance()
        bots = injector.getInstance()

        bots.start()
    }

    @Test
    fun `Can create a bot and add make it join channels`() {
        val token = app.login("gal", "hunter2").join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            val bot = app.channelJoin(token, "#channel")
                .thenCompose { bots.bot() }
                .join()
            bot.join("#channel").join()
            bot.channels().join()
        }, equalTo(listOf("#channel")))
    }

    @Test
    fun `Can list bots in a channel`() {
        app.login("gal", "hunter2")
            .thenCompose { adminToken ->
                app.channelJoin(adminToken, "#channel")
                    .thenCompose { bots.bot().thenCompose { it.join("#channel") } }
            }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bots("#channel").join()
        }, equalTo(listOf("Anna0")))
    }

    @Test
    fun `A user in the channel can ask the bot to do calculation`() {
        val listener = mockk<ListenerCallback>(relaxed = true)

        every { listener(any(), any()) } returns CompletableFuture.completedFuture(Unit)

        app.login("gal", "hunter2")
            .thenCompose { adminToken ->
                app.channelJoin(adminToken, "#channel")
                    .thenCompose {
                        bots.bot().thenCompose { bot ->
                            bot.join("#channel")
                                .thenApply { bot.setCalculationTrigger("calculate") }
                        }
                    }
                    .thenCompose { app.login("matan", "s3kr3t") }
                    .thenCompose { token ->
                        app.channelJoin(token, "#channel")
                            .thenApply { token }
                    }
                    .thenCompose { token ->
                        app.addListener(token, listener)
                            .thenApply { token }
                    }
                    .thenCompose { token ->
                        app.channelSend(token,
                                        "#channel",
                                        messageFactory.create(MediaType.TEXT,
                                                              "calculate 20 * 2 + 2".toByteArray()).join())
                    }
            }.join()

        verify {
            listener.invoke("#channel@matan", any())
            listener.invoke("#channel@Anna0",
                            match { it.contents.toString(Charsets.UTF_8).toInt() == 42 })
        }
    }

    @Test
    fun `A user in the channel can tip another user`() {
        app.login("gal", "hunter2")
            .thenCompose { adminToken ->
                app.channelJoin(adminToken, "#channel")
                    .thenCompose {
                        bots.bot()
                            .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                            .thenCompose { bot -> bot.setTipTrigger("tip") }
                    }
                    .thenCompose { app.login("matan", "s3kr3t") }
                    .thenCompose { token ->
                        app.channelJoin(token, "#channel")
                            .thenApply { token }
                    }
                    .thenCompose { token ->
                        app.channelSend(token,
                                        "#channel",
                                        messageFactory.create(MediaType.TEXT,
                                                              "tip 10 gal".toByteArray()).join())
                    }
            }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0")
                .thenCompose { it.richestUser("#channel") }
                .join()
        }, present(equalTo("gal")))
    }

    @Test
    fun `The bot accurately tracks keywords`() {
        app.login("gal", "hunter2")
            .thenCompose { adminToken ->
                app.channelJoin(adminToken, "#channel")
                    .thenCompose {
                        bots.bot()
                            .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                            .thenCompose { bot -> bot.beginCount(null, ".*ello.*[wW]orl.*") }
                    }
                    .thenCompose { app.login("matan", "s3kr3t") }
                    .thenCompose { token ->
                        app.channelJoin(token, "#channel")
                            .thenApply { token }
                    }
                    .thenCompose { token ->
                        app.channelSend(token,
                                        "#channel",
                                        messageFactory.create(MediaType.TEXT,
                                                              "hello, world!".toByteArray()).join())
                    }
            }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0").thenCompose { bot -> bot.count(null, ".*ello.*[wW]orl.*") }
                .join() // missing null for channel
        }, equalTo(1L))
    }

    @Test
    fun `A user in the channel can ask the bot to do a survey`() {
        val adminToken = app.login("gal", "hunter2")
            .thenCompose { token -> app.channelJoin(token, "#channel").thenApply { token } }
            .join()
        val regularUserToken = app.login("matan", "s3kr3t")
            .thenCompose { token -> app.channelJoin(token, "#channel").thenApply { token } }
            .join()
        val bot = bots.bot()
            .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
            .join()

        assertThat(runWithTimeout(ofSeconds(100000)) {
            val survey =
                    bot.runSurvey("#channel", "What is your favorite flavour of ice-cream?",
                                  listOf("Cranberry",
                                         "Charcoal",
                                         "Chocolate-chip Mint")).join()
            app.channelSend(adminToken,
                            "#channel",
                            messageFactory.create(MediaType.TEXT,
                                                  "Chocolate-chip Mint".toByteArray()).join())
            app.channelSend(regularUserToken,
                            "#channel",
                            messageFactory.create(MediaType.TEXT,
                                                  "Chocolate-chip Mint".toByteArray()).join())
            app.channelSend(adminToken,
                            "#channel",
                            messageFactory.create(MediaType.TEXT,
                                                  "Chocolate-chip Mint".toByteArray()).join())
            bot.surveyResults(survey).join()
        }, containsElementsInOrder(0L, 0L, 2L))
    }
}
