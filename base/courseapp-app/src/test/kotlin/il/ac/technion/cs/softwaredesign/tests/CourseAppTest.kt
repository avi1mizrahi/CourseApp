package il.ac.technion.cs.softwaredesign.tests

import com.authzee.kotlinguice4.KotlinModule
import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.google.inject.Injector
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration.ofSeconds


class CourseAppTest {
    // We Inject a mocked KeyValueStore and not rely on a KeyValueStore that relies on another DB layer
    private var injector: Injector
    private var app: CourseApp
    private var statistics: CourseAppStatistics

    init {
        class CourseAppModuleMock : KotlinModule() {
            override fun configure() {
                bind<KeyValueStore>().toInstance(VolatileKeyValueStore())
                bind<CourseApp>().to<CourseAppImpl>()
                bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()
            }
        }

        injector = Guice.createInjector(CourseAppModuleMock())
        app = injector.getInstance<CourseApp>()
        statistics = injector.getInstance<CourseAppStatistics>()
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
                // TODO: which should throw? the login or the future? i.e. do we need the join or not?
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
        }

        @Test
        fun `Only admin can make channels`() {
            val tokenAdmin = app.login("name1", "pass").join()
            val tokenSecond = app.login("name2", "pass").join()

            app.channelJoin(tokenAdmin, "#ch1").join()
            assertThrows<UserNotAuthorizedException> { app.channelJoin(tokenSecond, "#ch2").joinException() }
        }

        @Test
        fun `Non admin can't join deleted channel`() {
            val tokenAdmin = app.login("name1", "pass").join()
            val tokenSecond = app.login("name2", "pass").join()

            app.channelJoin(tokenAdmin, "#ch1")
                .thenCompose { app.channelPart(tokenAdmin, "#ch1") }
                .join()
            assertThrows<UserNotAuthorizedException> { app.channelJoin(tokenSecond, "#ch1").joinException()}
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

            assertThrows<NoSuchEntityException> { app.channelPart(tokenAdmin, "#ch1").joinException() }
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
    inner class Statistics {
        @Test
        fun `Test top 10 channels`() {
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
        fun `Test top 10 Active`() {
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
        fun `Test top 10 Users`() {

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
        fun `Test user count statistics`() {
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
