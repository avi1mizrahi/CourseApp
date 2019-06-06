package il.ac.technion.cs.softwaredesign.tests

import com.authzee.kotlinguice4.KotlinModule
import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Provider
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.dataTypeProxies.MessageManager
import il.ac.technion.cs.softwaredesign.exceptions.*
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration.ofSeconds
import java.util.concurrent.CompletableFuture


class CourseAppTest {
    // We Inject a mocked KeyValueStore and not rely on a KeyValueStore that relies on another DB layer
    private val injector: Injector
    private val app: CourseApp
    private val statistics: CourseAppStatistics
    private val messageFactory: MessageFactory

    init {
        class CourseAppModuleMock : KotlinModule() {
            override fun configure() {
                val keystoreinst = VolatileKeyValueStore()
                bind<KeyValueStore>().toInstance(keystoreinst)
                bind<CourseApp>().to<CourseAppImpl>()
                bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()
                bind<MessageFactory>().toProvider(Provider<MessageFactory> {
                    MessageManager(keystoreinst.scope("messages"))
                })
            }
        }

        injector = Guice.createInjector(CourseAppModuleMock())
        app = injector.getInstance()
        statistics = injector.getInstance()
        messageFactory = injector.getInstance()
    }


    @Test
    fun `Empty test`() = Unit

    @Nested
    inner class Login {
        @Test
        fun `after login, a user is logged in`() {
            val token = app.login("gal", "hunter2")
                .thenCompose { app.login("imaman", "31337") }
                .thenCompose { app.login("matan", "s3kr1t") }
                .join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                app.isUserLoggedIn(token, "gal")
                    .join()
            },
                       present(isTrue))
        }

        @Test
        fun `an authentication token is invalidated after logout`() {
            val token = app
                .login("matan", "s3kr1t")
                .thenCompose { token -> app.logout(token).thenApply { token } }
                .join()

            assertThrows<InvalidTokenException> {
                runWithTimeout(ofSeconds(10)) {
                    app.isUserLoggedIn(token, "matan").joinException()
                }
            }
        }

        @Test
        fun `throw on invalid tokens`() {
            assertThrows<InvalidTokenException> {
                runWithTimeout(ofSeconds(10)) { app.isUserLoggedIn("a", "any").joinException() }
            }

            assertThrows<InvalidTokenException> {
                runWithTimeout(ofSeconds(10)) { app.logout("a").joinException() }
            }
        }

        @Test
        fun `login after logout`() {
            val token = app.login("name", "pass")
                .thenCompose { app.logout(it) }
                .thenCompose { app.login("name", "pass") }
                .join()

            // new token works
            assertThat(runWithTimeout(ofSeconds(10)) {
                app.isUserLoggedIn(token, "name").join()
            }, present(isTrue))
        }

        @Test
        fun `throws when already logged in`() {
            app.login("someone", "123").join()

            assertThrows<UserAlreadyLoggedInException> {
                runWithTimeout(ofSeconds(10)) {
                    app.login("someone", "123").joinException()
                }
            }
        }

        @Test
        fun `bad password throws nosuchEntity`() {
            app.login("name", "pass")
                .thenCompose { app.logout(it) }
                .join()

            assertThrows<NoSuchEntityException> {
                runWithTimeout(ofSeconds(10)) {
                    app.login("name", "badpass").joinException()
                }
            }
        }

        @Test
        fun `One user checking another`() {
            val token1 = app.login("name1", "pass").join()
            val token2 = app.login("name2", "pass").join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                app.isUserLoggedIn(token1, "name2").join()
            }, present(isTrue))
            assertThat(runWithTimeout(ofSeconds(10)) {
                app.isUserLoggedIn(token2, "name1").join()
            }, present(isTrue))
        }

        @Test
        fun `User is logged out after log out`() {
            val token1 = app.login("name1", "pass").join()
            val token2 = app.login("name2", "pass").join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                app.isUserLoggedIn(token2, "name1").join()
            }, present(isTrue))

            app.logout(token1).join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                app.isUserLoggedIn(token2, "name1").join()
            }, present(isFalse))
        }

        @Test
        fun `User not existing returns null when asked if logged in`() {
            val token1 = app.login("name1", "pass").join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                app.isUserLoggedIn(token1, "name2").join()
            }, absent())
        }
    }

    @Test
    fun `First user is admin and making others admin causes no exceptions`() {
        val tokenAdmin = app.login("name1", "pass")
            .thenCompose { admin ->
                app.login("name2", "pass")
                    .thenApply { admin }
            }.join()

        assertDoesNotThrow { app.makeAdministrator(tokenAdmin, "name2").join() }

    }

    @Test
    fun `Second user is not an admin`() {
        val second = app.login("name1", "pass")
            .thenCompose { app.login("name2", "pass") }
            .join()

        assertThrows<UserNotAuthorizedException> {
            app.makeAdministrator(second, "name1").joinException()
        }
    }

    @Nested
    inner class Channels {
        @Test
        fun `Test Channel name`() {
            val tokenAdmin = app.login("name1", "pass").join()

            assertThrows<NameFormatException> { app.channelJoin(tokenAdmin, "hello").joinException() }
            assertThrows<NameFormatException> { app.channelJoin(tokenAdmin, "1234").joinException() }
            assertThrows<NameFormatException> { app.channelJoin(tokenAdmin, "a1").joinException() }
            assertThrows<NameFormatException> { app.channelJoin(tokenAdmin, "עברית").joinException() }
            assertThrows<NameFormatException> { app.channelJoin(tokenAdmin, "#עברית").joinException() }
            assertThrows<NameFormatException> { app.channelJoin(tokenAdmin, "#hello[").joinException() }
            assertDoesNotThrow { app.channelJoin(tokenAdmin, "#hello").joinException() }
            assertDoesNotThrow { app.channelJoin(tokenAdmin, "#").joinException() }
        }

        @Test
        fun `Only admin can make channels`() {
            val tokenAdmin = app.login("name1", "pass").join()
            val tokenSecond = app.login("name2", "pass").join()

            app.channelJoin(tokenAdmin, "#ch1").join()
            assertThrows<UserNotAuthorizedException> {
                app.channelJoin(tokenSecond, "#ch2")
                    .joinException()
            }
        }

        @Test
        fun `Non admin can't join deleted channel`() {
            val tokenAdmin = app.login("name1", "pass").join()
            val tokenSecond = app.login("name2", "pass").join()

            app.channelJoin(tokenAdmin, "#ch1")
                .thenCompose { app.channelPart(tokenAdmin, "#ch1") }
                .join()
            assertThrows<UserNotAuthorizedException> {
                app.channelJoin(tokenSecond, "#ch1")
                    .joinException()
            }
        }


        @Test
        fun `Admins cant kick from channels they're not in`() {
            val admin = app.login("name1", "pass").join()
            val notAdmin = app.login("name2", "pass").join()

            app.makeAdministrator(admin, "name2")
                .thenCompose { app.channelJoin(notAdmin, "#ch1") }
                .join()

            assertThrows<UserNotAuthorizedException> {
                app.channelKick(admin, "#ch1", "name2").joinException()
            }
        }

        @Test
        fun `Operator can make operators and can kick admins`() {
            val tokenAdmin = app.login("name1", "pass").join()
            val tokenSecond = app.login("name2", "pass").join()

            app.channelJoin(tokenAdmin, "#ch1")
                .thenCompose { app.channelJoin(tokenSecond, "#ch1") }.join()

            assertThrows<UserNotAuthorizedException> {
                app.channelKick(tokenSecond, "#ch1", "name1").joinException()
            }

            assertEquals(2, app.numberOfTotalUsersInChannel(tokenSecond, "#ch1").join().toInt())
            app.channelMakeOperator(tokenAdmin, "#ch1", "name2")
                .thenCompose { app.channelKick(tokenSecond, "#ch1", "name1") }
                .join()
            assertEquals(1, app.numberOfTotalUsersInChannel(tokenSecond, "#ch1").join().toInt())
        }

        @Test
        fun `Nothing happens when joining channel twice`() {
            val tokenAdmin = app.login("name1", "pass")
                .thenCompose { admin ->
                    app.channelJoin(admin, "#ch1")
                        .thenCompose { app.channelJoin(admin, "#ch1") }
                        .thenApply { admin }
                }.join()

            assertEquals(1, app.numberOfTotalUsersInChannel(tokenAdmin, "#ch1").join().toInt())
        }

        @Test
        fun `Throws when leaving channel twice`() {
            val tokenAdmin = app.login("name1", "pass")
                .thenCompose { admin ->
                    app.channelJoin(admin, "#ch1")
                        .thenCompose { app.channelPart(admin, "#ch1") }
                        .thenApply { admin }
                }.join()

            assertThrows<NoSuchEntityException> {
                app.channelPart(tokenAdmin, "#ch1")
                    .joinException()
            }
            assertThrows<NoSuchEntityException> {
                app.numberOfTotalUsersInChannel(tokenAdmin, "#ch1").joinException()
            }
        }

        @Test
        fun `IsUserInChannel return desired results`() {
            val tokenAdmin = app.login("name1", "pass")
                .thenCompose { admin ->
                    app.login("name2", "pass")
                        .thenCompose { app.channelJoin(admin, "#ch1") }
                        .thenApply { admin }
                }.join()

            assertNull(app.isUserInChannel(tokenAdmin, "#ch1", "name3").join())
            assertFalse(app.isUserInChannel(tokenAdmin, "#ch1", "name2").join()!!)
            assertTrue(app.isUserInChannel(tokenAdmin, "#ch1", "name1").join()!!)
        }

        @Test
        fun `IsUserInChannel throws on bad input`() {
            assertThrows<InvalidTokenException> {
                app.isUserInChannel("aaa", "#ch1", "name1").joinException()
            }
            val tokenAdmin = app.login("name1", "pass").join()
            val tokenOther = app.login("name2", "pass").join()

            app.channelJoin(tokenAdmin, "#ch1").join()

            assertThrows<NoSuchEntityException> {
                app.isUserInChannel(tokenAdmin,
                                    "#ch2",
                                    "name1").joinException()
            }
            assertThrows<UserNotAuthorizedException> {
                app.isUserInChannel(tokenOther,
                                    "#ch1",
                                    "name1").joinException()
            }
        }

        @Test
        fun `Test channel active and nonactive user count`() {
            val tokenAdmin = app.login("name1", "pass").join()
            app.channelJoin(tokenAdmin, "#ch1").join()

            val tokens = ArrayList<String>()
            for (i in 101..130) {
                app.login("name$i", "pass")
                    .thenAccept {
                        app.channelJoin(it, "#ch1").join()
                        tokens.add(it)
                    }.join()
            }
            for (i in 2..30) {
                app.login("name$i", "pass")
                    .thenCompose { app.channelJoin(it, "#ch1") }
                    .join()
            }
            for (i in 201..230) {
                app.login("name$i", "pass").join()
            }

            assertEquals(60, app.numberOfTotalUsersInChannel(tokenAdmin, "#ch1").join().toInt())
            assertEquals(60, app.numberOfActiveUsersInChannel(tokenAdmin, "#ch1").join().toInt())


            tokens.forEach { app.logout(it).join() }

            assertEquals(60, app.numberOfTotalUsersInChannel(tokenAdmin, "#ch1").join().toInt())
            assertEquals(30, app.numberOfActiveUsersInChannel(tokenAdmin, "#ch1").join().toInt())
        }
    }

    @Nested
    inner class Messages {
        @Test
        fun `addListener throws on bad input`() {
            app.login("who", "ha").join()

            assertThrows<InvalidTokenException> {
                app.addListener("invalid token", mockk()).joinException()
            }
        }

        @Test
        fun `removeListener throws on bad input`() {
            val token = app.login("who", "ha").join()

            assertThrows<InvalidTokenException> {
                app.removeListener("invalid token", mockk()).joinException()
            }

            app.addListener(token, mockk(name = "A cute listener")).join()

            assertThrows<NoSuchEntityException> {
                app.removeListener(token, mockk(name = "who's that listener?!")).joinException()
            }
        }

        @Test
        fun `removeListener removes old listener`() {
            val token = app.login("who", "ha").join()

            val callback = mockk<ListenerCallback>(name = "A cute listener")
            app.addListener(token, callback).join()

            assertDoesNotThrow { app.removeListener(token, callback).joinException() }
        }

        @Test
        fun `channelSend throws on bad input`() {
            val token = app.login("who", "ha").join()

            assertThrows<InvalidTokenException> {
                app.channelSend("invalid token", "#what", mockk()).joinException()
            }

            assertThrows<NoSuchEntityException> {
                app.channelSend(token, "#what", mockk()).joinException()
            }

            app.login("bla", "bla")
                .thenCompose { bla -> app.makeAdministrator(token, "bla").thenApply { bla } }
                .thenCompose { app.channelJoin(it, "#what") }
                .join()

            assertThrows<UserNotAuthorizedException> {
                app.channelSend(token, "#what", mockk()).joinException()
            }
        }

        @Test
        fun `channelSend message received by all listeners`() {
            val listener = mockk<ListenerCallback>()

            every { listener(any(), any()) } returns CompletableFuture.completedFuture(Unit)

            val (tokens, message) =
                    app.login("who", "ha")
                        .thenCompose { admin ->
                            app.login("user2", "user2")
                                .thenApply { Pair(admin, it) }
                        }
                        .thenCompose { tokens ->
                            app.addListener(tokens.first, listener)
                                .thenCompose { app.addListener(tokens.second, listener) }
                                .thenCompose { app.channelJoin(tokens.first, "#channel") }
                                .thenCompose { app.channelJoin(tokens.second, "#channel") }
                                .thenCompose {
                                    messageFactory.create(MediaType.TEXT,
                                                          "1 2".toByteArray())
                                }.thenApply { Pair(tokens, it) }
                        }.join()

            app.channelSend(tokens.first, "#channel", message).join()

            verify(exactly = 2) {
                listener(match { it == "#channel@who" },
                         match { it.contents contentEquals "1 2".toByteArray() })
            }
            confirmVerified()
        }

        @Test
        fun `broadcast throws on bad input`() {
            val notAdmin = app.login("who", "ha")
                .thenCompose { app.login("someone else", "some password") }
                .join()

            assertThrows<InvalidTokenException> {
                app.broadcast("invalid token", mockk()).joinException()
            }

            assertThrows<UserNotAuthorizedException> {
                app.broadcast(notAdmin, mockk()).joinException()
            }
        }

        @Test
        fun `privateSend throws on bad input`() {
            val admin = app.login("who", "ha").join()

            assertThrows<InvalidTokenException> {
                app.privateSend("invalid token", "some one", mockk()).joinException()
            }

            assertThrows<NoSuchEntityException> {
                app.privateSend(admin, "some one", mockk()).joinException()
            }
        }

        @Test
        fun `add listener returns with pending private messages`() {
            val token = app.login("admin", "admin")
                .thenCompose { adminToken ->
                    app.login("gal", "hunter2").thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    messageFactory.create(MediaType.TEXT, "hello, world\n".toByteArray())
                        .thenCompose { app.privateSend(adminToken, "gal", it) }
                        .thenApply { nonAdminToken }
                }.join()

            val listener = mockk<ListenerCallback>()
            every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

            runWithTimeout(ofSeconds(10)) {
                app.addListener(token, listener).join()
            }

            verify {
                listener(match { it == "@admin" },
                         match { it.contents contentEquals "hello, world\n".toByteArray() })
            }
            confirmVerified(listener)
        }

        @Test
        fun `add listener returns with pending broadcast messages`() {
            val tokens = app.login("admin", "admin")
                .thenCompose { adminToken ->
                    app.login("gal", "hunter2")
                        .thenCompose { token1 ->
                            app.login("tal", "hunter5")
                                .thenApply { listOf(adminToken, token1, it) }
                        }
                }.thenCompose { tokens ->
                    messageFactory.create(MediaType.TEXT, "hello".toByteArray())
                        .thenCompose { app.broadcast(tokens[0], it) }
                        .thenCompose { messageFactory.create(MediaType.TEXT, "world".toByteArray()) }
                        .thenCompose { app.broadcast(tokens[0], it) }
                        .thenApply { tokens }
                }.join()

            val listener = mockk<ListenerCallback>()
            every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

            runWithTimeout(ofSeconds(10)) {
                app.addListener(tokens[1], listener).join()
            }
            runWithTimeout(ofSeconds(10)) {
                app.addListener(tokens[2], listener).join()
            }

            verify(exactly = 2) {
                listener(match { it == "BROADCAST" },
                         match { it.contents contentEquals "hello".toByteArray() })
                listener(match { it == "BROADCAST" },
                         match { it.contents contentEquals "world".toByteArray() })
            }
            confirmVerified(listener)
        }

        @Test
        fun `fetchMessage throws on bad input`() {
            val admin = app.login("who", "ha").join()

            assertThrows<InvalidTokenException> {
                app.fetchMessage("invalid token", 4).joinException()
            }

            assertThrows<NoSuchEntityException> {
                app.fetchMessage(admin, 4).joinException()
            }

            val id = app.login("someone", "1234")
                .thenCompose { token ->
                    app.makeAdministrator(admin, "someone")
                        .thenCompose { app.channelJoin(token, "#wawa") }
                        .thenCompose {
                            messageFactory.create(MediaType.TEXT,
                                                  "important message".toByteArray())
                        }
                        .thenCompose { msg ->
                            app.channelSend(token, "#wawa", msg)
                                .thenApply { msg.id }
                        }
                }.join()

            assertThrows<UserNotAuthorizedException> {
                app.fetchMessage(admin, id).joinException()
            }
        }

        @Test
        fun `fetchMessage throws NoSuchEntityException on non channel message`() {
            val (token, message) = app.login("who", "ha")
                .thenCompose { admin ->
                    app.login("someone", "1234")
                        .thenApply { Pair(admin, it) }
                }.thenCompose { (admin, notAdmin) ->
                    messageFactory.create(MediaType.TEXT, "broad !".toByteArray())
                        .thenCompose { msg -> app.broadcast(admin, msg)
                            .thenApply { Pair(notAdmin, msg) }
                        }
                }.join()

            assertThrows<NoSuchEntityException> {
                app.fetchMessage(token, message.id).joinException()
            }
        }
    }

    @Nested
    inner class Statistics {
        @Test
        fun `top 10 channels`() {
            val tokens = ArrayList<String>()

            app.login("admin", "pass")
                .thenApply {
                    for (i in 1..20) app.channelJoin(it, "#ch$i").join()
                }.thenApply {
                    for (i in 1..20) {
                        app.login("name$i", "pass")
                            .thenAccept {
                                for (j in 1..i) app.channelJoin(it, "#ch$j").join()
                                tokens.add(it)
                            }.join()
                    }
                }.join()

            runWithTimeout(ofSeconds(10)) {
                assertThat(statistics.top10ChannelsByUsers().join(),
                           containsElementsInOrder(
                                   "#ch1", "#ch2", "#ch3", "#ch4", "#ch5",
                                   "#ch6", "#ch7", "#ch8", "#ch9", "#ch10"))
            }


            // Test order by creation time (index)
            app.channelPart(tokens[0], "#ch1").join()
            runWithTimeout(ofSeconds(10)) {
                assertThat(statistics.top10ChannelsByUsers().join(),
                           containsElementsInOrder(
                                   "#ch1", "#ch2", "#ch3", "#ch4", "#ch5",
                                   "#ch6", "#ch7", "#ch8", "#ch9", "#ch10"))
            }

            // Test order by count
            app.channelPart(tokens[1], "#ch1").join()
            runWithTimeout(ofSeconds(10)) {
                assertThat(statistics.top10ChannelsByUsers().join(),
                           containsElementsInOrder(
                                   "#ch2", "#ch1", "#ch3", "#ch4", "#ch5",
                                   "#ch6", "#ch7", "#ch8", "#ch9", "#ch10"))
            }
        }

        @Test
        fun `top 10 Active`() {
            app.login("admin", "pass")
                .thenAccept {
                    for (j in 1..2) app.channelJoin(it, "#ch$j").join()
                }.join()

            val tokens = ArrayList<String>()
            for (i in 1..2) {
                app.login("name$i", "pass")
                    .thenCompose {
                        tokens.add(it)
                        app.channelJoin(it, "#ch$i")
                    }.join()
            }

            val token3 = app.login("name3", "pass")
                .thenCompose { token ->
                    app.channelJoin(token, "#ch1").thenApply { token }
                }.join()

            // (3,2) in channels
            runWithTimeout(ofSeconds(10)) {
                assertThat(statistics.top10ActiveChannelsByUsers().join(),
                           containsElementsInOrder("#ch1", "#ch2"))
            }

            app.logout(token3).join()
            // (2,2) in channels
            runWithTimeout(ofSeconds(10)) {
                assertThat(statistics.top10ActiveChannelsByUsers().join(),
                           containsElementsInOrder("#ch1", "#ch2"))
            }


            app.logout(tokens[0]).join()
            // (1,2) in channels
            runWithTimeout(ofSeconds(10)) {
                assertThat(statistics.top10ActiveChannelsByUsers().join(),
                           containsElementsInOrder("#ch2", "#ch1"))
            }


            app.login("name1", "pass").join()
            // (2,2) in channels
            runWithTimeout(ofSeconds(10)) {
                assertThat(statistics.top10ActiveChannelsByUsers().join(),
                           containsElementsInOrder("#ch1", "#ch2"))
            }
        }

        @Test
        fun `top 10 Users`() {

            app.login("admin", "pass")
                .thenAccept { for (j in 1..20) app.channelJoin(it, "#ch$j").join() }
                .join()

            val tokens = ArrayList<String>()
            for (i in 1..20) {
                app.login("name$i", "pass")
                    .thenAccept {
                        for (j in 1..i) app.channelJoin(it, "#ch$j").join()
                        tokens.add(it)
                    }.join()
            }

            // admin in all channels and created first. for rest later users have more channels
            runWithTimeout(ofSeconds(10)) {
                assertThat(statistics.top10UsersByChannels().join(),
                           containsElementsInOrder(
                                   "admin", "name20", "name19", "name18", "name17",
                                   "name16", "name15", "name14", "name13", "name12"))
            }
        }

        @Test
        fun `top 10 Users with less than 10`() {
            app.login("admin", "pass")
                .thenAccept { for (j in 1..6) app.channelJoin(it, "#ch$j").join() }
                .join()

            val tokens = ArrayList<String>()
            for (i in 1..6) {
                app.login("name$i", "pass")
                    .thenAccept {
                        for (j in 1..i) app.channelJoin(it, "#ch$j").join()
                        tokens.add(it)
                    }.join()
            }

            // admin in all channels and created first. for rest later users have more channels
            runWithTimeout(ofSeconds(10)) {
                assertThat(statistics.top10UsersByChannels().join(),
                           containsElementsInOrder("admin",
                                                   "name6",
                                                   "name5",
                                                   "name4",
                                                   "name3",
                                                   "name2",
                                                   "name1"))
            }
        }

        @Test
        fun top10ChannelsByMessages() {
            // create 20 channels
            val token = app.login("admin", "pass")
                .thenApply {token ->
                    repeat(20) { app.channelJoin(token, "#c$it").join() }
                    token
                }.join()

            // 11111 11111 11111
            // -----|-----|-----|-----|
            for (i in 0 until 15) {
                messageFactory.create(MediaType.TEXT, "@#$".toByteArray())
                    .thenCompose { app.channelSend(token, "#c$i", it) }.join()
            }

            // 11111 11111 22222
            // -----|-----|-----|-----|
            for (i in 10 until 15) {
                messageFactory.create(MediaType.TEXT, "@#$".toByteArray())
                    .thenCompose { app.channelSend(token, "#c$i", it) }.join()
            }

            // 33333 11111 22222
            // -----|-----|-----|-----|
            for (i in 0 until 5) {
                repeat(2) {
                    messageFactory.create(MediaType.TEXT, "@#$".toByteArray())
                        .thenCompose { app.channelSend(token, "#c$i", it) }.join()
                }
            }

            // 33333 11111 22222   5
            // -----|-----|-----|-----|
            repeat(5) {
                messageFactory.create(MediaType.TEXT, "@#$".toByteArray())
                    .thenCompose { app.channelSend(token, "#c18", it) }.join()

            }

            // admin in all channels and created first. for rest later users have more channels
            runWithTimeout(ofSeconds(10)) {
                assertThat(statistics.top10ChannelsByMessages().join(),
                           containsElementsInOrder("#c18",
                                                   "#c0",
                                                   "#c1",
                                                   "#c2",
                                                   "#c3",
                                                   "#c4",
                                                   "#c10",
                                                   "#c11",
                                                   "#c12",
                                                   "#c13"))
            }
        }

        @Test
        fun `user count statistics`() {
            val tokens = ArrayList<String>()
            for (i in 1..20) {
                app.login("name$i", "pass")
                    .thenAccept { tokens.add(it) }
                    .join()
            }

            assertEquals(20, statistics.totalUsers().join().toInt())
            assertEquals(20, statistics.loggedInUsers().join().toInt())
            app.logout(tokens[0])
                .thenCompose { app.logout(tokens[5]) }
                .thenCompose { app.logout(tokens[10]) }
                .join()
            assertEquals(20, statistics.totalUsers().join().toInt())
            assertEquals(17, statistics.loggedInUsers().join().toInt())
        }
    }
}
