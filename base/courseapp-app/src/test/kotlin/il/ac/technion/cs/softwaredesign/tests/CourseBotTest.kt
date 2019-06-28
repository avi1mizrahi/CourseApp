package il.ac.technion.cs.softwaredesign.tests

import com.authzee.kotlinguice4.KotlinModule
import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.google.inject.Injector
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration.ofSeconds
import java.util.concurrent.CompletableFuture


inline fun <reified T> completedOf(t: T) = CompletableFuture.completedFuture(t)
inline fun <reified T> failedOf(t: Throwable) = CompletableFuture.failedFuture<T>(t)

class CourseBotTest {
    // We Inject a mocked KeyValueStore and not rely on a KeyValueStore that relies on another DB layer
    private val injector: Injector
    private val app: CourseApp = mockk()
    private val statistics: CourseAppStatistics = mockk()
    private val messageFactory: MessageFactory = mockk(relaxed = true)
    private val bots: CourseBots

    init {
        class CourseAppModuleMock : KotlinModule() {
            override fun configure() {
                val keystoreinst = VolatileKeyValueStore()

                bind<KeyValueStore>().toInstance(keystoreinst)
                bind<CourseApp>().toInstance(app)
                bind<CourseAppStatistics>().toInstance(statistics)
                bind<MessageFactory>().toInstance(messageFactory)

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
        bots = injector.getInstance()

        bots.start()
    }

    @Nested
    inner class Channels {
        @Test
        fun `throws when can't join`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf(Unit)
            every { app.channelJoin(any(), any()) } returns failedOf(UserNotAuthorizedException())

            val bot = bots.bot().join()

            assertThrows<UserNotAuthorizedException> { bot.join("#WTF").joinException() }
        }

        @Test
        fun `throws when can't part`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf(Unit)
            every { app.channelPart(any(), any()) } returns failedOf(NoSuchEntityException())

            val bot = bots.bot().join()

            assertThrows<NoSuchEntityException> { bot.part("#WTF").joinException() }
        }

        @Test
        fun `join and part`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf(Unit)
            every { app.channelJoin(any(), any()) } returns completedOf(Unit)
            every { app.channelPart(any(), any()) } returns completedOf(Unit)

            val bot = bots.bot().join()
            bot.join("#c").join()

            assertDoesNotThrow { bot.part("#c").joinException() }
        }

        @Test
        fun `list channels`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf(Unit)
            every { app.channelJoin(any(), any()) } returns completedOf(Unit)

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
    }

    @Nested
    inner class Counter {
        @Test
        fun `beginCount throws IllegalArgumentException on bad input`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf(Unit)

            val bot = bots.bot().join()

            assertThrows<IllegalArgumentException> {
                bot.beginCount("#hh", null, null).joinException()
            }
        }

        @Test
        fun `count throws IllegalArgumentException without prior begin`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf(Unit)

            val bot = bots.bot().join()

            assertThrows<IllegalArgumentException> {
                bot.count("#hh", null, null).joinException()
            }
        }
    }

    @Nested
    inner class Calculator {

    }

    @Nested
    inner class Tipping {

    }

    @Nested
    inner class Stocker {

    }

    @Nested
    inner class Survey {

    }

    @Nested
    inner class General {
        //TODO: from bots.bots: @return List of bot names **in order of bot creation.**
        @Test
        fun `default name`() {
            every { app.login("Anna0", any()) } returns completedOf("1")
            every { app.login("Anna1", any()) } returns completedOf("2")
            every { app.login("Anna2", any()) } returns completedOf("3")

            every { app.addListener("1", any()) } returns completedOf(Unit)
            every { app.addListener("2", any()) } returns completedOf(Unit)
            every { app.addListener("3", any()) } returns completedOf(Unit)

            every { app.channelJoin("1", any()) } returns completedOf(Unit)
            every { app.channelJoin("2", any()) } returns completedOf(Unit)
            every { app.channelJoin("3", any()) } returns completedOf(Unit)

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

            every { app.addListener("1", any()) } returns completedOf(Unit)
            every { app.channelJoin("1", any()) } returns completedOf(Unit)

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

            every { app.addListener(any(), any()) } returns completedOf(Unit)
            every { app.channelJoin(any(), any()) } returns completedOf(Unit)

            bots.bot().thenCompose { it.join("#c1") }.join()
            bots.bot().thenCompose { it.join("#c2") }.join()
            bots.bot().thenCompose { it.join("#c1") }.join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bots.bots().join()
            }, equalTo(listOf("Anna0", "Anna1", "Anna2")))
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

                val managers = Managers(keystoreinst)
                bind<Managers>().toInstance(managers)
                bind<MessageFactory>().toInstance(managers.messages)

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

    @Disabled // TODO
    @Test
    fun `The bot accurately tracks keywords`() {
        app.login("gal", "hunter2")
            .thenCompose { adminToken ->
                app.channelJoin(adminToken, "#channel")
                    .thenCompose {
                        bots.bot()
                            .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                            .thenCompose { bot -> bot.beginCount(".*ello.*[wW]orl.*") }
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
            // TODO different regex? we have to store messages?
            bots.bot("Anna0").thenCompose { bot -> bot.count(null, ".*hell.*worl.*") }
                .join() // missing null for channel
        }, equalTo(1L))
    }

    @Disabled // TODO
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

        assertThat(runWithTimeout(ofSeconds(10)) {
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
