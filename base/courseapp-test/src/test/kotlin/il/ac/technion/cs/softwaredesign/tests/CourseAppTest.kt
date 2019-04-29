package il.ac.technion.cs.softwaredesign.tests

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.CourseAppInitializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import kotlin.random.Random

class CourseAppTest {

    private val courseAppInitializer = CourseAppInitializer()

    init {
        courseAppInitializer.setup()
    }

    private val courseApp = CourseApp(MockDBAccess())

    @Test
    fun `test invalid tokens throwing`(){
        assertThrows<IllegalArgumentException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.isUserLoggedIn("a", "any") }
        }

        assertThrows<IllegalArgumentException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.logout("a") }
        }
    }

    @Test
    fun `basic double login`(){
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
        assert(false) { "TODO" }
    }

    @Test
    fun `password check`() {
        val oldtoken = courseApp.login("name", "pass")
        courseApp.logout(oldtoken)

        assertThrows<IllegalArgumentException> {
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
}