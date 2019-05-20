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
    private var injector : Injector
    private var courseApp : CourseApp
    private var courseAppStatistics : CourseAppStatistics
    init {
        class CourseAppModuleMock : KotlinModule() {
            override fun configure() {
                val mockKV = MockKeyValueStore()
                bind<KeyValueStore>().toInstance(mockKV)
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
    fun `throw on invalid tokens`(){
        assertThrows<InvalidTokenException> {
            runWithTimeout(ofSeconds(10)) { courseApp.isUserLoggedIn("a", "any") }
        }

        assertThrows<InvalidTokenException> {
            runWithTimeout(ofSeconds(10)) { courseApp.logout("a") }
        }
    }

    @Test
    fun `login after logout`(){
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
    fun `User not existing returns null when asked if logged in`()
    {
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


        assertThrows<UserNotAuthorizedException> {courseApp.makeAdministrator(tokenSecond, "name1")}
    }

    @Test
    fun `Test Channel name`() {
        val tokenAdmin = courseApp.login("name1", "pass")


        assertThrows<NameFormatException> {courseApp.channelJoin(tokenAdmin, "hello")}
        assertThrows<NameFormatException> {courseApp.channelJoin(tokenAdmin, "1234")}
        assertThrows<NameFormatException> {courseApp.channelJoin(tokenAdmin, "a1")}
        assertThrows<NameFormatException> {courseApp.channelJoin(tokenAdmin, "עברית")}
        assertThrows<NameFormatException> {courseApp.channelJoin(tokenAdmin, "#עברית")}
        assertThrows<NameFormatException> {courseApp.channelJoin(tokenAdmin, "#hello[")}
        courseApp.channelJoin(tokenAdmin, "#hello")
    }


    @Test
    fun `Only admin can make channels`() {
        val tokenAdmin = courseApp.login("name1", "pass")
        val tokenSecond = courseApp.login("name2", "pass")

        courseApp.channelJoin(tokenAdmin, "#ch1")
        assertThrows<UserNotAuthorizedException> {courseApp.channelJoin(tokenSecond, "#ch2")}
    }

    @Test
    fun `Non admin cant join deleted channel`() {
        val tokenAdmin = courseApp.login("name1", "pass")
        val tokenSecond = courseApp.login("name2", "pass")

        courseApp.channelJoin(tokenAdmin, "#ch1")
        courseApp.channelPart(tokenAdmin, "#ch1")
        assertThrows<UserNotAuthorizedException> {courseApp.channelJoin(tokenSecond, "#ch1")}
    }

    // TODO function description conflicts with staff test
    @Test
    fun `channelMakeOperator`() {

    }

    @Test
    fun `Nothing happens when joining or leaving channel twice`() {
        val tokenAdmin = courseApp.login("name1", "pass")

        courseApp.channelJoin(tokenAdmin, "#ch1")
        assert(courseApp.numberOfTotalUsersInChannel(tokenAdmin,"#ch1").toInt() == 1)
        courseApp.channelJoin(tokenAdmin, "#ch1")
        assert(courseApp.numberOfTotalUsersInChannel(tokenAdmin,"#ch1").toInt() == 1)

        courseApp.channelPart(tokenAdmin, "#ch1")
        assertThrows<NoSuchEntityException>{ courseApp.numberOfTotalUsersInChannel(tokenAdmin,"#ch1") }
        assertThrows<NoSuchEntityException>{ courseApp.channelPart(tokenAdmin, "#ch1") }
    }

    // TODO function description conflicts with staff test
    @Test
    fun `channelKick`() {

    }


    @Test
    fun `IsUserInChannel throws on bad input and works correctly`() {

        assertThrows<InvalidTokenException> {courseApp.isUserInChannel("aaa", "#ch1", "name1")}
        val tokenAdmin = courseApp.login("name1", "pass")
        val tokenOther = courseApp.login("name2", "pass")
        courseApp.channelJoin(tokenAdmin, "#ch1")
        assert(courseApp.isUserInChannel(tokenAdmin, "#ch1", "name3") == null)
        assert(courseApp.isUserInChannel(tokenAdmin, "#ch1", "name2") == false)
        assert(courseApp.isUserInChannel(tokenAdmin, "#ch1", "name1") == true)


        assertThrows<NoSuchEntityException> {courseApp.isUserInChannel(tokenAdmin, "#ch2", "name1")}
        assertThrows<UserNotAuthorizedException> {courseApp.isUserInChannel(tokenOther, "#ch1", "name1")}
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

        assert ( courseApp.numberOfTotalUsersInChannel(tokenAdmin, "#ch1").toInt() == 60)
        assert ( courseApp.numberOfActiveUsersInChannel(tokenAdmin, "#ch1").toInt() == 60)


        tokens.forEach {courseApp.logout(it)}
        assert ( courseApp.numberOfTotalUsersInChannel(tokenAdmin, "#ch1").toInt()  == 60)
        assert ( courseApp.numberOfActiveUsersInChannel(tokenAdmin, "#ch1").toInt() == 30)

    }


    // TODO copy of staff tests from here, delete later
    @Test
    fun `administrator can create channel and is a member of it`() {
        val administratorToken = courseApp.login("admin", "admin")
        courseApp.channelJoin(administratorToken, "#mychannel")

        assertThat(runWithTimeout(ofSeconds(10)) {
            courseApp.isUserInChannel(administratorToken, "#mychannel", "admin")
        },
                isTrue)
    }

    @Test
    fun `non-administrator can not make administrator`() {
        courseApp.login("admin", "admin")
        val nonAdminToken = courseApp.login("matan", "1234")
        courseApp.login("gal", "hunter2")

        assertThrows<UserNotAuthorizedException> {
            runWithTimeout(ofSeconds(10)) { courseApp.makeAdministrator(nonAdminToken, "gal") }
        }
    }

    @Test
    fun `non-administrator can join existing channel and be made operator`() {
        val adminToken = courseApp.login("admin", "admin")
        val nonAdminToken = courseApp.login("matan", "1234")

        courseApp.channelJoin(adminToken, "#test")
        courseApp.channelJoin(nonAdminToken, "#test")

        // TODO conflict
        courseApp.channelMakeOperator(adminToken, "#test", "matan")

        assertThat(runWithTimeout(ofSeconds(10)) {
            courseApp.isUserInChannel(adminToken, "#test", "matan")
        },
                isTrue)
    }

    @Test
    fun `user is not in channel after parting from it`() {
        val adminToken = courseApp.login("admin", "admin")
        val nonAdminToken = courseApp.login("matan", "1234")
        courseApp.channelJoin(adminToken, "#mychannel")
        courseApp.channelJoin(nonAdminToken, "#mychannel")
        courseApp.channelPart(nonAdminToken, "#mychannel")

        assertThat(runWithTimeout(ofSeconds(10)) {
            courseApp.isUserInChannel(adminToken, "#mychannel", "matan")
        },
                isFalse)
    }


    @Test
    fun `user is not in channel after being kicked`() {
        val adminToken = courseApp.login("admin", "admin")
        val nonAdminToken = courseApp.login("matan", "4321")
        courseApp.channelJoin(adminToken, "#236700")
        courseApp.channelJoin(nonAdminToken, "#236700")
        courseApp.channelKick(adminToken, "#236700", "matan")

        assertThat(runWithTimeout(ofSeconds(10)) {
            courseApp.isUserInChannel(adminToken, "#236700", "matan")
        },
                isFalse)
    }

    @Test
    fun `total user count in a channel is correct with a single user`() {
        val adminToken = courseApp.login("admin", "admin")
        courseApp.channelJoin(adminToken, "#test")

        assertThat(runWithTimeout(ofSeconds(10)) {
            courseApp.numberOfTotalUsersInChannel(adminToken, "#test")
        },
                equalTo(1L))
    }

    @Test
    fun `active user count in a channel is correct with a single user`() {
        val adminToken = courseApp.login("admin", "admin")
        courseApp.channelJoin(adminToken, "#test")

        assertThat(runWithTimeout(ofSeconds(10)) {
            courseApp.numberOfActiveUsersInChannel(adminToken, "#test")
        },
                equalTo(1L))
    }

    @Test
    fun `logged in user count is correct when no user is logged in`() {
        assertThat(runWithTimeout(ofSeconds(10)) { courseAppStatistics.loggedInUsers() }, equalTo(0L))
    }

    @Test
    fun `total user count is correct when no users exist`() {
        assertThat(runWithTimeout(ofSeconds(10)) { courseAppStatistics.totalUsers() }, equalTo(0L))
    }

    @Test
    fun `top 10 channel list counts only logged in users`() {
        val adminToken = courseApp.login("admin", "admin")
        val nonAdminToken = courseApp.login("matan", "4321")
        courseApp.makeAdministrator(adminToken, "matan")

        assert(courseAppStatistics.top10ActiveChannelsByUsers().size == 0)
        courseApp.channelJoin(adminToken, "#test")
        assert(courseAppStatistics.top10ActiveChannelsByUsers().size == 1)
        courseApp.channelJoin(nonAdminToken, "#other")
        assert(courseAppStatistics.top10ActiveChannelsByUsers().size == 2)
        courseApp.logout(nonAdminToken)

        runWithTimeout(ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ActiveChannelsByUsers(),
                    containsElementsInOrder("#test", "#other"))
        }
    }
}
