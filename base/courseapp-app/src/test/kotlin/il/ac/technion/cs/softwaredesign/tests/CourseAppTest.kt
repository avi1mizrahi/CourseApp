package il.ac.technion.cs.softwaredesign.tests

import com.authzee.kotlinguice4.KotlinModule
import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.google.inject.Injector
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration.*


class CourseAppTest {
    // We Inject a mocked KeyValueStore and not rely on a KeyValueStore that relies on another DB layer
    private var injector: Injector
    private var courseApp: CourseApp
    private var courseAppStatistics: CourseAppStatistics

    init {
        class CourseAppModuleMock : KotlinModule() {
            override fun configure() {
                bind<KeyValueStore>().toInstance(MockKeyValueStore())
                bind<CourseApp>().to<CourseAppImpl>()
                bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()
            }
        }

        injector = Guice.createInjector(CourseAppModuleMock())
        courseApp = injector.getInstance<CourseApp>()
        courseAppStatistics = injector.getInstance<CourseAppStatistics>()
    }


    @Test
    fun `Empty test`() {

    }

    @Test
    fun `after login, a user is logged in`() {
        courseApp.login("gal", "hunter2")
        courseApp.login("imaman", "31337")

        val token = courseApp.login("matan", "s3kr1t")

        assertThat(runWithTimeout(ofSeconds(10)) { courseApp.isUserLoggedIn(token, "gal") },
                present(isTrue))
    }

    @Test
    fun `an authentication token is invalidated after logout`() {
        val token = courseApp.login("matan", "s3kr1t")

        courseApp.logout(token)

        assertThrows<InvalidTokenException> {
            runWithTimeout(ofSeconds(10)) { courseApp.isUserLoggedIn(token, "matan") }
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
        // log in and log out
        val oldtoken = courseApp.login("name", "pass")
        courseApp.logout(oldtoken)

        // log in again
        val newtoken = courseApp.login("name", "pass")

        // new token works
        assertThat(runWithTimeout(ofSeconds(10)) { courseApp.isUserLoggedIn(newtoken, "name") },
                present(isTrue))
    }

    @Test
    fun `throws when already logged in`() {
        courseApp.login("someone", "123")

        assertThrows<UserAlreadyLoggedInException> {
            runWithTimeout(ofSeconds(10)) { courseApp.login("someone", "123") }
        }
    }

    @Test
    fun `bad password throws nosuchEntity`() {
        val oldtoken = courseApp.login("name", "pass")
        courseApp.logout(oldtoken)

        assertThrows<NoSuchEntityException> {
            runWithTimeout(ofSeconds(10)) { courseApp.login("name", "badpass") }
        }
    }

    @Test
    fun `One user checking another`() {
        val token1 = courseApp.login("name1", "pass")
        val token2 = courseApp.login("name2", "pass")

        assertThat(runWithTimeout(ofSeconds(10)) { courseApp.isUserLoggedIn(token1, "name2") },
                present(isTrue))
        assertThat(runWithTimeout(ofSeconds(10)) { courseApp.isUserLoggedIn(token2, "name1") },
                present(isTrue))


    }

    @Test
    fun `User is logged out after log out`() {
        val token1 = courseApp.login("name1", "pass")
        val token2 = courseApp.login("name2", "pass")

        assertThat(runWithTimeout(ofSeconds(10)) { courseApp.isUserLoggedIn(token2, "name1") },
                present(isTrue))
        courseApp.logout(token1)
        assertThat(runWithTimeout(ofSeconds(10)) { courseApp.isUserLoggedIn(token2, "name1") },
                present(equalTo(false)))


    }

    @Test
    fun `User not existing returns null when asked if logged in`() {
        val token1 = courseApp.login("name1", "pass")

        assertThat(runWithTimeout(ofSeconds(10)) { courseApp.isUserLoggedIn(token1, "name2") }, absent())
    }


    // HW1 tests from here
    @Test
    fun `First user is admin and making others admin causes no exceptions`() {
        val tokenAdmin = courseApp.login("name1", "pass")
        courseApp.login("name2", "pass")

        courseApp.makeAdministrator(tokenAdmin, "name2")

    }

    @Test
    fun `Second user is not an admin`() {
        courseApp.login("name1", "pass")
        val tokenSecond = courseApp.login("name2", "pass")


        assertThrows<UserNotAuthorizedException> { courseApp.makeAdministrator(tokenSecond, "name1") }
    }

    @Test
    fun `Test Channel name`() {
        val tokenAdmin = courseApp.login("name1", "pass")


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
        val tokenAdmin = courseApp.login("name1", "pass")
        val tokenSecond = courseApp.login("name2", "pass")

        courseApp.channelJoin(tokenAdmin, "#ch1")
        assertThrows<UserNotAuthorizedException> { courseApp.channelJoin(tokenSecond, "#ch2") }
    }

    @Test
    fun `Non admin cant join deleted channel`() {
        val tokenAdmin = courseApp.login("name1", "pass")
        val tokenSecond = courseApp.login("name2", "pass")

        courseApp.channelJoin(tokenAdmin, "#ch1")
        courseApp.channelPart(tokenAdmin, "#ch1")
        assertThrows<UserNotAuthorizedException> { courseApp.channelJoin(tokenSecond, "#ch1") }
    }


    @Test
    fun `Admins cant kick from channels they're not in`() {
        val tokenAdmin = courseApp.login("name1", "pass")
        val tokenAdmin2 = courseApp.login("name2", "pass")

        courseApp.makeAdministrator(tokenAdmin, "name2")

        courseApp.channelJoin(tokenAdmin2, "#ch1")
        assertThrows<UserNotAuthorizedException> {
            courseApp.channelKick(tokenAdmin, "#ch1", "name2")
        }

    }

    @Test
    fun `Operator can make operators and can kick admins`() {
        val tokenAdmin = courseApp.login("name1", "pass")
        val tokenSecond = courseApp.login("name2", "pass")

        courseApp.channelJoin(tokenAdmin, "#ch1")
        courseApp.channelJoin(tokenSecond, "#ch1")
        assertThrows<UserNotAuthorizedException> {
            courseApp.channelKick(tokenSecond, "#ch1", "name1")
        }


        assert(courseApp.numberOfTotalUsersInChannel(tokenSecond, "#ch1").toInt() == 2)
        courseApp.channelMakeOperator(tokenAdmin, "#ch1", "name2")
        courseApp.channelKick(tokenSecond, "#ch1", "name1")
        assert(courseApp.numberOfTotalUsersInChannel(tokenSecond, "#ch1").toInt() == 1)
    }

    @Test
    fun `Nothing happens when joining or leaving channel twice`() {
        val tokenAdmin = courseApp.login("name1", "pass")

        courseApp.channelJoin(tokenAdmin, "#ch1")
        assert(courseApp.numberOfTotalUsersInChannel(tokenAdmin, "#ch1").toInt() == 1)
        courseApp.channelJoin(tokenAdmin, "#ch1")
        assert(courseApp.numberOfTotalUsersInChannel(tokenAdmin, "#ch1").toInt() == 1)

        courseApp.channelPart(tokenAdmin, "#ch1")
        assertThrows<NoSuchEntityException> { courseApp.numberOfTotalUsersInChannel(tokenAdmin, "#ch1") }
        assertThrows<NoSuchEntityException> { courseApp.channelPart(tokenAdmin, "#ch1") }
    }


    @Test
    fun `IsUserInChannel throws on bad input and works correctly`() {

        assertThrows<InvalidTokenException> { courseApp.isUserInChannel("aaa", "#ch1", "name1") }
        val tokenAdmin = courseApp.login("name1", "pass")
        val tokenOther = courseApp.login("name2", "pass")
        courseApp.channelJoin(tokenAdmin, "#ch1")
        assert(courseApp.isUserInChannel(tokenAdmin, "#ch1", "name3") == null)
        assert(courseApp.isUserInChannel(tokenAdmin, "#ch1", "name2") == false)
        assert(courseApp.isUserInChannel(tokenAdmin, "#ch1", "name1") == true)


        assertThrows<NoSuchEntityException> { courseApp.isUserInChannel(tokenAdmin, "#ch2", "name1") }
        assertThrows<UserNotAuthorizedException> { courseApp.isUserInChannel(tokenOther, "#ch1", "name1") }
    }

    @Test
    fun `Test channel active and nonactive user count`() {
        val tokenAdmin = courseApp.login("name1", "pass")
        courseApp.channelJoin(tokenAdmin, "#ch1")

        val tokens = ArrayList<String>()
        for (i in 101..130) {
            val t = courseApp.login("name$i", "pass")
            courseApp.channelJoin(t, "#ch1")
            tokens.add(t)
        }
        for (i in 2..30) {
            val t = courseApp.login("name$i", "pass")
            courseApp.channelJoin(t, "#ch1")
        }
        for (i in 201..230) {
            courseApp.login("name$i", "pass")
        }

        assert(courseApp.numberOfTotalUsersInChannel(tokenAdmin, "#ch1").toInt() == 60)
        assert(courseApp.numberOfActiveUsersInChannel(tokenAdmin, "#ch1").toInt() == 60)


        tokens.forEach { courseApp.logout(it) }
        assert(courseApp.numberOfTotalUsersInChannel(tokenAdmin, "#ch1").toInt() == 60)
        assert(courseApp.numberOfActiveUsersInChannel(tokenAdmin, "#ch1").toInt() == 30)

    }

    @Test
    fun `Test top 10 channels`() {
        val tokenAdmin = courseApp.login("admin", "pass")

        for (j in 1..20) courseApp.channelJoin(tokenAdmin, "#ch$j")

        val tokens = ArrayList<String>()
        for (i in 1..20) {
            val t = courseApp.login("name$i", "pass")
            for (j in 1..i) courseApp.channelJoin(t, "#ch$j")
            tokens.add(t)
        }


        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ChannelsByUsers(),
                    containsElementsInOrder(
                            "#ch1", "#ch2", "#ch3", "#ch4", "#ch5",
                            "#ch6", "#ch7", "#ch8", "#ch9", "#ch10"))
        }


        // Test order by creation time (index)
        courseApp.channelPart(tokens[0], "#ch1")
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ChannelsByUsers(),
                    containsElementsInOrder(
                            "#ch1", "#ch2", "#ch3", "#ch4", "#ch5",
                            "#ch6", "#ch7", "#ch8", "#ch9", "#ch10"))
        }

        // Test order by count
        courseApp.channelPart(tokens[1], "#ch1")
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ChannelsByUsers(),
                    containsElementsInOrder(
                            "#ch2", "#ch1", "#ch3", "#ch4", "#ch5",
                            "#ch6", "#ch7", "#ch8", "#ch9", "#ch10"))
        }

    }

    @Test
    fun `Test top 10 Active`() {
        val tokenAdmin = courseApp.login("admin", "pass")
        for (j in 1..2) courseApp.channelJoin(tokenAdmin, "#ch$j")

        val tokens = ArrayList<String>()
        for (i in 1..2) {
            val t = courseApp.login("name$i", "pass")
            courseApp.channelJoin(t, "#ch$i")
            tokens.add(t)
        }

        val token3 = courseApp.login("name3", "pass")
        courseApp.channelJoin(token3, "#ch1")

        // (3,2) in channels
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ActiveChannelsByUsers(),
                    containsElementsInOrder("#ch1", "#ch2"))
        }

        courseApp.logout(token3)
        // (2,2) in channels
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ActiveChannelsByUsers(),
                    containsElementsInOrder("#ch1", "#ch2"))
        }


        courseApp.logout(tokens[0])
        // (1,2) in channels
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ActiveChannelsByUsers(),
                    containsElementsInOrder("#ch2", "#ch1"))
        }


        courseApp.login("name1", "pass")
        // (2,2) in channels
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ActiveChannelsByUsers(),
                    containsElementsInOrder("#ch1", "#ch2"))
        }


    }

    @Test
    fun `Test top 10 Users`() {

        val tokenAdmin = courseApp.login("admin", "pass")
        for (j in 1..20) courseApp.channelJoin(tokenAdmin, "#ch$j")

        val tokens = ArrayList<String>()
        for (i in 1..20) {
            val t = courseApp.login("name$i", "pass")
            for (j in 1..i) courseApp.channelJoin(t, "#ch$j")
            tokens.add(t)
        }


        // admin in all channels and created first. for rest later users have more channels
        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10UsersByChannels(),
                    containsElementsInOrder(
                            "admin", "name20", "name19", "name18", "name17",
                            "name16", "name15", "name14", "name13", "name12"))
        }

    }

    @Test
    fun `Test user count statistics`() {
        val tokens = ArrayList<String>()
        for (i in 1..20) {
            val t = courseApp.login("name$i", "pass")
            tokens.add(t)
        }


        assert(courseAppStatistics.totalUsers().toInt() == 20)
        assert(courseAppStatistics.loggedInUsers().toInt() == 20)
        courseApp.logout(tokens[0])
        courseApp.logout(tokens[5])
        courseApp.logout(tokens[10])
        assert(courseAppStatistics.totalUsers().toInt() == 20)
        assert(courseAppStatistics.loggedInUsers().toInt() == 17)

    }

}
