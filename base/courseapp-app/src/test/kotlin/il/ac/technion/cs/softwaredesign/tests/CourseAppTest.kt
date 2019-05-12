package il.ac.technion.cs.softwaredesign.tests

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserAlreadyLoggedInException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration


class CourseAppTest {

    // val courseAppInitializer = CourseAppInitializer() // TODO

    init {
        //courseAppInitializer.setup()
    }

    private val map = HashMap<List<String>, String>()
    private val keyValueStore = mockk<KeyValueStore>()
    private val courseApp = CourseAppImplementation(keyValueStore)
    private val keySlot = slot<List<String>>()
    private val valSlot = slot<String>()

    @BeforeEach
    fun before() {
        every { keyValueStore.read(key = capture(keySlot)) } answers { map[keySlot.captured] }
        every { keyValueStore.write(key = capture(keySlot), value = capture(valSlot)) } answers { map[keySlot.captured] = valSlot.captured }
        every { keyValueStore.delete(key = capture(keySlot)) } answers { map.remove(keySlot.captured) }
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
}