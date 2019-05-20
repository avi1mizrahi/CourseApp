package il.ac.technion.cs.softwaredesign.tests

import com.authzee.kotlinguice4.KotlinModule
import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.google.inject.Provider
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserAlreadyLoggedInException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration



class CourseAppProvider : Provider<CourseApp> {
    var lastCourseApp : CourseAppImpl? = null
    override fun get() : CourseApp {

        class StorageInject : KotlinModule() {
            override fun configure() {
                bind<KeyValueStore>().to<MockKeyValueStore>()
                bind<CourseApp>().to<CourseAppImpl>()
            }
        }

        val app = Guice.createInjector(StorageInject()).getInstance<CourseApp>()

        lastCourseApp = app as CourseAppImpl
        lastCourseApp!!.init()

        return app
    }
    inner class CourseAppStatsProvider : Provider<CourseAppStatistics> {
        override fun get() : CourseAppStatistics {
            return lastCourseApp!!
        }
    }

}

class CourseAppModuleMock : KotlinModule() {
    override fun configure() {
        bind<CourseAppInitializer>().to<CourseAppImplInitializer>()
        bind<KeyValueStore>().to<MockKeyValueStore>()

        val provider = CourseAppProvider()
        bind<CourseApp>().toProvider(provider)
        bind<CourseAppStatistics>().toProvider(provider.CourseAppStatsProvider())

    }
}

val injector = Guice.createInjector(CourseAppModuleMock())

class CourseAppTest {

    private val courseApp = injector.getInstance<CourseApp>()
    private val courseAppInitializer = injector.getInstance<CourseAppInitializer>()
    private val courseAppStatistics = injector.getInstance<CourseAppStatistics>()

    init {
        courseAppInitializer.setup()
    }

    @Test
    fun `Empty test`() {

    }

    @Test
    fun `after login, a user is logged in`() {
        courseApp.login("gal", "hunter2")
        courseApp.login("imaman", "31337")

        val token = courseApp.login("matan", "s3kr1t")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) { courseApp.isUserLoggedIn(token, "gal") },
                present(isTrue))
    }

    @Test
    fun `an authentication token is invalidated after logout`() {
        val token = courseApp.login("matan", "s3kr1t")

        courseApp.logout(token)

        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.isUserLoggedIn(token, "matan") }
        }
    }

    @Test
    fun `throw on invalid tokens`(){
        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.isUserLoggedIn("a", "any") }
        }

        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.logout("a") }
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
        assertThat(runWithTimeout(Duration.ofSeconds(10)) { courseApp.isUserLoggedIn(newtoken, "name") },
                present(isTrue))
    }

    @Test
    fun `throws when already logged in`() {
        courseApp.login("someone", "123")

        assertThrows<UserAlreadyLoggedInException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.login("someone", "123") }
        }
    }

    @Test
    fun `bad password throws nosuchEntity`() {
        val oldtoken = courseApp.login("name", "pass")
        courseApp.logout(oldtoken)

        assertThrows<NoSuchEntityException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.login("name", "badpass") }
        }
    }

    @Test
    fun `One user checking another`() {
        val token1 = courseApp.login("name1", "pass")
        val token2 = courseApp.login("name2", "pass")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) { courseApp.isUserLoggedIn(token1, "name2") },
                present(isTrue))
        assertThat(runWithTimeout(Duration.ofSeconds(10)) { courseApp.isUserLoggedIn(token2, "name1") },
                present(isTrue))


    }

    @Test
    fun `User is logged out after log out`() {
        val token1 = courseApp.login("name1", "pass")
        val token2 = courseApp.login("name2", "pass")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) { courseApp.isUserLoggedIn(token2, "name1") },
                present(isTrue))
        courseApp.logout(token1)
        assertThat(runWithTimeout(Duration.ofSeconds(10)) { courseApp.isUserLoggedIn(token2, "name1") },
                present(equalTo(false)))


    }

    @Test
    fun `User not existing returns null when asked if logged in`()
    {
        val token1 = courseApp.login("name1", "pass")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) { courseApp.isUserLoggedIn(token1, "name2") }, absent())
    }





    // TODO copy of staff tests from here, delete later
    @Test
    fun `administrator can create channel and is a member of it`() {
        val administratorToken = courseApp.login("admin", "admin")
        courseApp.channelJoin(administratorToken, "#mychannel")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
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
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.makeAdministrator(nonAdminToken, "gal") }
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

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
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

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
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

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.isUserInChannel(adminToken, "#236700", "matan")
        },
                isFalse)
    }

    @Test
    fun `total user count in a channel is correct with a single user`() {
        val adminToken = courseApp.login("admin", "admin")
        courseApp.channelJoin(adminToken, "#test")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.numberOfTotalUsersInChannel(adminToken, "#test")
        },
                equalTo(1L))
    }

    @Test
    fun `active user count in a channel is correct with a single user`() {
        val adminToken = courseApp.login("admin", "admin")
        courseApp.channelJoin(adminToken, "#test")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.numberOfActiveUsersInChannel(adminToken, "#test")
        },
                equalTo(1L))
    }

    @Test
    fun `logged in user count is correct when no user is logged in`() {
        assertThat(runWithTimeout(Duration.ofSeconds(10)) { courseAppStatistics.loggedInUsers() }, equalTo(0L))
    }

    @Test
    fun `total user count is correct when no users exist`() {
        assertThat(runWithTimeout(Duration.ofSeconds(10)) { courseAppStatistics.totalUsers() }, equalTo(0L))
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

        runWithTimeout(Duration.ofSeconds(10)) {
            assertThat(courseAppStatistics.top10ActiveChannelsByUsers(),
                    containsElementsInOrder("#test", "#other"))
        }
    }



}