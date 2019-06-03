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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration.ofSeconds


class CourseAppTest {
    // We Inject a mocked KeyValueStore and not rely on a KeyValueStore that relies on another DB layer
    private var injector: Injector
    private var courseApp: CourseApp
    private var courseAppStatistics: CourseAppStatistics

    init {
        class CourseAppModuleMock : KotlinModule() {
            override fun configure() {
                bind<KeyValueStore>().toInstance(VolatileKeyValueStore())
                bind<CourseApp>().to<CourseAppImpl>()
                bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()
            }
        }

        injector = Guice.createInjector(CourseAppModuleMock())
        courseApp = injector.getInstance<CourseApp>()
        courseAppStatistics = injector.getInstance<CourseAppStatistics>()
    }


    @Test
    fun `Empty test`() = Unit

    @Test
    fun `after login, a user is logged in`() {
        val token = courseApp.login("gal", "hunter2")
            .thenCompose { courseApp.login("imaman", "31337") }
            .thenCompose { courseApp.login("matan", "s3kr1t") }
            .join()

        assertThat(runWithTimeout(ofSeconds(10)) { courseApp.isUserLoggedIn(token, "gal").join() },
                   present(isTrue))
    }

    @Test
    fun `an authentication token is invalidated after logout`() {
        val token = courseApp
            .login("matan", "s3kr1t")
            .thenCompose { token -> courseApp.logout(token).thenApply { token } }
            .join()

        assertThrows<InvalidTokenException> {
            runWithTimeout(ofSeconds(10)) {
                courseApp.isUserLoggedIn(token, "matan").join()
            }
        }
    }

    @Test
    fun `throw on invalid tokens`() {
        assertThrows<InvalidTokenException> {
            runWithTimeout(ofSeconds(10)) { courseApp.isUserLoggedIn("a", "any") }
        }

        assertThrows<InvalidTokenException> {
            runWithTimeout(ofSeconds(10)) { courseApp.logout("a") }
        }
    }

    @Test
    fun `login after logout`() {
        val token = courseApp.login("name", "pass")
            .thenCompose { courseApp.logout(it) }
            .thenCompose { courseApp.login("name", "pass") }
            .join()

        // new token works
        assertThat(runWithTimeout(ofSeconds(10)) {
            courseApp.isUserLoggedIn(token, "name").join()
        }, present(isTrue))
    }

    @Test
    fun `throws when already logged in`() {
        courseApp.login("someone", "123").join()

        assertThrows<UserAlreadyLoggedInException> {
            runWithTimeout(ofSeconds(10)) {
                courseApp.login("someone", "123").join()
            }
        }
    }

    @Test
    fun `bad password throws nosuchEntity`() {
        courseApp.login("name", "pass")
            .thenCompose { courseApp.logout(it) }
            .join()

        assertThrows<NoSuchEntityException> {
            // TODO: which should throw? the login or the future? i.e. do we need the join or not?
            runWithTimeout(ofSeconds(10)) {
                courseApp.login("name", "badpass").join()
            }
        }
    }

    @Test
    fun `One user checking another`() {
        val token1 = courseApp.login("name1", "pass").join()
        val token2 = courseApp.login("name2", "pass").join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            courseApp.isUserLoggedIn(token1, "name2").join()
        }, present(isTrue))
        assertThat(runWithTimeout(ofSeconds(10)) {
            courseApp.isUserLoggedIn(token2, "name1").join()
        }, present(isTrue))
    }

    @Test
    fun `User is logged out after log out`() {
        val token1 = courseApp.login("name1", "pass").join()
        val token2 = courseApp.login("name2", "pass").join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            courseApp.isUserLoggedIn(token2, "name1").join()
        }, present(isTrue))

        courseApp.logout(token1).join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            courseApp.isUserLoggedIn(token2, "name1").join()
        }, present(isFalse))
    }

    @Test
    fun `User not existing returns null when asked if logged in`() {
        val token1 = courseApp.login("name1", "pass").join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            courseApp.isUserLoggedIn(token1, "name2").join()
        }, absent())
    }

    @Test
    fun `First user is admin and making others admin causes no exceptions`() {
        val tokenAdmin = courseApp.login("name1", "pass")
            .thenCompose { admin ->
                courseApp.login("name2", "pass")
                    .thenApply { admin }
            }.join()

        assertDoesNotThrow { courseApp.makeAdministrator(tokenAdmin, "name2").join() }

    }

    @Test
    fun `Second user is not an admin`() {
        val second = courseApp.login("name1", "pass")
            .thenCompose { courseApp.login("name2", "pass") }
            .join()

        assertThrows<UserNotAuthorizedException> {
            courseApp.makeAdministrator(second, "name1")
        }
    }

    @Test
    fun `Test Channel name`() {
        val tokenAdmin = courseApp.login("name1", "pass").join()

        assertThrows<NameFormatException> { courseApp.channelJoin(tokenAdmin, "hello") }
        assertThrows<NameFormatException> { courseApp.channelJoin(tokenAdmin, "1234") }
        assertThrows<NameFormatException> { courseApp.channelJoin(tokenAdmin, "a1") }
        assertThrows<NameFormatException> { courseApp.channelJoin(tokenAdmin, "עברית") }
        assertThrows<NameFormatException> { courseApp.channelJoin(tokenAdmin, "#עברית") }
        assertThrows<NameFormatException> { courseApp.channelJoin(tokenAdmin, "#hello[") }
        courseApp.channelJoin(tokenAdmin, "#hello")
    }

    @Test
    fun `Only admin can make channels`() {
        val tokenAdmin = courseApp.login("name1", "pass").join()
        val tokenSecond = courseApp.login("name2", "pass").join()

        courseApp.channelJoin(tokenAdmin, "#ch1").join()
        assertThrows<UserNotAuthorizedException> { courseApp.channelJoin(tokenSecond, "#ch2") }
    }

    @Test
    fun `Non admin can't join deleted channel`() {
        val tokenAdmin = courseApp.login("name1", "pass").join()
        val tokenSecond = courseApp.login("name2", "pass").join()

        courseApp.channelJoin(tokenAdmin, "#ch1")
            .thenCompose { courseApp.channelPart(tokenAdmin, "#ch1") }
            .join()
        assertThrows<UserNotAuthorizedException> { courseApp.channelJoin(tokenSecond, "#ch1") }
    }


    @Test
    fun `Admins cant kick from channels they're not in`() {
        val admin = courseApp.login("name1", "pass").join()
        val notAdmin = courseApp.login("name2", "pass").join()

        courseApp.makeAdministrator(admin, "name2")
            .thenCompose { courseApp.channelJoin(notAdmin, "#ch1") }
            .join()

        assertThrows<UserNotAuthorizedException> {
            courseApp.channelKick(admin, "#ch1", "name2")
        }
    }

    @Test
    fun `Operator can make operators and can kick admins`() {
        val tokenAdmin = courseApp.login("name1", "pass").join()
        val tokenSecond = courseApp.login("name2", "pass").join()

        courseApp.channelJoin(tokenAdmin, "#ch1")
            .thenCompose { courseApp.channelJoin(tokenSecond, "#ch1") }.join()

        assertThrows<UserNotAuthorizedException> {
            courseApp.channelKick(tokenSecond, "#ch1", "name1")
        }

        assertEquals(2, courseApp.numberOfTotalUsersInChannel(tokenSecond, "#ch1").join().toInt())
        courseApp.channelMakeOperator(tokenAdmin, "#ch1", "name2")
            .thenCompose { courseApp.channelKick(tokenSecond, "#ch1", "name1") }
            .join()
        assertEquals(1, courseApp.numberOfTotalUsersInChannel(tokenSecond, "#ch1").join().toInt())
    }

    @Test
    fun `Nothing happens when joining channel twice`() {
        val tokenAdmin = courseApp.login("name1", "pass")
            .thenCompose { admin ->
                courseApp.channelJoin(admin, "#ch1")
                    .thenCompose { courseApp.channelJoin(admin, "#ch1") }
                    .thenApply { admin }
            }.join()

        assertEquals(1, courseApp.numberOfTotalUsersInChannel(tokenAdmin, "#ch1").join().toInt())
    }

    @Test
    fun `Throws when leaving channel twice`() {
        val tokenAdmin = courseApp.login("name1", "pass")
            .thenCompose { admin ->
                courseApp.channelJoin(admin, "#ch1")
                    .thenCompose { courseApp.channelPart(admin, "#ch1") }
                    .thenApply { admin }
            }.join()

        assertThrows<NoSuchEntityException> { courseApp.channelPart(tokenAdmin, "#ch1") }
        assertThrows<NoSuchEntityException> {
            courseApp.numberOfTotalUsersInChannel(tokenAdmin, "#ch1")
        }
    }

    @Test
    fun `IsUserInChannel return desired results`() {
        val tokenAdmin = courseApp.login("name1", "pass")
            .thenCompose { admin ->
                courseApp.login("name2", "pass")
                    .thenCompose { courseApp.channelJoin(admin, "#ch1") }
                    .thenApply { admin }
            }.join()

        assertNull(courseApp.isUserInChannel(tokenAdmin, "#ch1", "name3").join())
        assertFalse(courseApp.isUserInChannel(tokenAdmin, "#ch1", "name2").join()!!)
        assertTrue(courseApp.isUserInChannel(tokenAdmin, "#ch1", "name1").join()!!)
    }

    @Test
    fun `IsUserInChannel throws on bad input`() {
        assertThrows<InvalidTokenException> {
            courseApp.isUserInChannel("aaa", "#ch1", "name1").join()
        }
        val tokenAdmin = courseApp.login("name1", "pass").join()
        val tokenOther = courseApp.login("name2", "pass").join()

        courseApp.channelJoin(tokenAdmin, "#ch1").join()

        assertThrows<NoSuchEntityException> {
            courseApp.isUserInChannel(tokenAdmin,
                                      "#ch2",
                                      "name1").join()
        }
        assertThrows<UserNotAuthorizedException> {
            courseApp.isUserInChannel(tokenOther,
                                      "#ch1",
                                      "name1").join()
        }
    }

    @Test
    fun `Test channel active and nonactive user count`() {
        val tokenAdmin = courseApp.login("name1", "pass").join()
        courseApp.channelJoin(tokenAdmin, "#ch1").join()

        val tokens = ArrayList<String>()
        for (i in 101..130) {
            courseApp.login("name$i", "pass")
                .thenAccept {
                    courseApp.channelJoin(it, "#ch1").join()
                    tokens.add(it)
                }.join()
        }
        for (i in 2..30) {
            courseApp.login("name$i", "pass")
                .thenCompose { courseApp.channelJoin(it, "#ch1") }
                .join()
        }
        for (i in 201..230) {
            courseApp.login("name$i", "pass").join()
        }

        assertEquals(60, courseApp.numberOfTotalUsersInChannel(tokenAdmin, "#ch1").join().toInt())
        assertEquals(60, courseApp.numberOfActiveUsersInChannel(tokenAdmin, "#ch1").join().toInt())


        tokens.forEach { courseApp.logout(it).join() }

        assertEquals(60, courseApp.numberOfTotalUsersInChannel(tokenAdmin, "#ch1").join().toInt())
        assertEquals(30, courseApp.numberOfActiveUsersInChannel(tokenAdmin, "#ch1").join().toInt())
    }

    @Test
    fun `Test top 10 channels`() {
        val tokens = ArrayList<String>()

        courseApp.login("admin", "pass")
            .thenApply {
                for (i in 1..20) courseApp.channelJoin(it, "#ch$i").join()
            }.thenApply {
                for (i in 1..20) {
                    courseApp.login("name$i", "pass")
                        .thenAccept {
                            for (j in 1..i) courseApp.channelJoin(it, "#ch$j").join()
                            tokens.add(it)
                        }.join()
                }
            }.join()

        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ChannelsByUsers().join(),
                       containsElementsInOrder(
                               "#ch1", "#ch2", "#ch3", "#ch4", "#ch5",
                               "#ch6", "#ch7", "#ch8", "#ch9", "#ch10"))
        }


        // Test order by creation time (index)
        courseApp.channelPart(tokens[0], "#ch1").join()
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ChannelsByUsers().join(),
                       containsElementsInOrder(
                               "#ch1", "#ch2", "#ch3", "#ch4", "#ch5",
                               "#ch6", "#ch7", "#ch8", "#ch9", "#ch10"))
        }

        // Test order by count
        courseApp.channelPart(tokens[1], "#ch1").join()
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ChannelsByUsers().join(),
                       containsElementsInOrder(
                               "#ch2", "#ch1", "#ch3", "#ch4", "#ch5",
                               "#ch6", "#ch7", "#ch8", "#ch9", "#ch10"))
        }
    }

    @Test
    fun `Test top 10 Active`() {
        courseApp.login("admin", "pass")
            .thenAccept {
                for (j in 1..2) courseApp.channelJoin(it, "#ch$j").join()
            }.join()

        val tokens = ArrayList<String>()
        for (i in 1..2) {
            courseApp.login("name$i", "pass")
                .thenCompose {
                    tokens.add(it)
                    courseApp.channelJoin(it, "#ch$i")
                }.join()
        }

        val token3 = courseApp.login("name3", "pass")
            .thenCompose {token ->
                courseApp.channelJoin(token, "#ch1").thenApply { token }
            }.join()

        // (3,2) in channels
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ActiveChannelsByUsers().join(),
                       containsElementsInOrder("#ch1", "#ch2"))
        }

        courseApp.logout(token3).join()
        // (2,2) in channels
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ActiveChannelsByUsers().join(),
                       containsElementsInOrder("#ch1", "#ch2"))
        }


        courseApp.logout(tokens[0]).join()
        // (1,2) in channels
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ActiveChannelsByUsers().join(),
                       containsElementsInOrder("#ch2", "#ch1"))
        }


        courseApp.login("name1", "pass").join()
        // (2,2) in channels
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ActiveChannelsByUsers().join(),
                       containsElementsInOrder("#ch1", "#ch2"))
        }
    }

    @Test
    fun `Test top 10 Users`() {

        courseApp.login("admin", "pass")
            .thenAccept { for (j in 1..20) courseApp.channelJoin(it, "#ch$j").join() }
            .join()

        val tokens = ArrayList<String>()
        for (i in 1..20) {
            courseApp.login("name$i", "pass")
                .thenAccept {
                    for (j in 1..i) courseApp.channelJoin(it, "#ch$j").join()
                    tokens.add(it)
                }.join()
        }

        // admin in all channels and created first. for rest later users have more channels
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10UsersByChannels().join(),
                       containsElementsInOrder(
                               "admin", "name20", "name19", "name18", "name17",
                               "name16", "name15", "name14", "name13", "name12"))
        }

    }

    @Test
    fun `Test user count statistics`() {
        val tokens = ArrayList<String>()
        for (i in 1..20) {
            courseApp.login("name$i", "pass")
                .thenAccept { tokens.add(it) }
                .join()
        }


        assertEquals(20, courseAppStatistics.totalUsers().join().toInt())
        assertEquals(20, courseAppStatistics.loggedInUsers().join().toInt())
        courseApp.logout(tokens[0])
            .thenCompose { courseApp.logout(tokens[5]) }
            .thenCompose { courseApp.logout(tokens[10]) }
            .join()
        assertEquals(20, courseAppStatistics.totalUsers().join().toInt())
        assertEquals(17, courseAppStatistics.loggedInUsers().join().toInt())

    }

}
