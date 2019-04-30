package il.ac.technion.cs.softwaredesign.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.CourseAppInitializer
import il.ac.technion.cs.softwaredesign.KeyValueStore
import il.ac.technion.cs.softwaredesign.Storage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration.ofSeconds


class MockStorage : Storage {
    private val encoding = Charsets.UTF_8

    private val keyvalDB = HashMap<String, ByteArray>()

    override fun read(key: ByteArray): ByteArray? {
        return keyvalDB[key.toString(encoding)]
    }

    override fun write(key: ByteArray, value: ByteArray) {
        keyvalDB[key.toString(encoding)] = value
    }
}

class CourseAppStaffTest {
    private val courseAppInitializer = CourseAppInitializer()

    init {
        courseAppInitializer.setup()
    }

    private val courseApp = CourseApp(KeyValueStore(MockStorage()))

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

        assertThrows<IllegalArgumentException> {
            runWithTimeout(ofSeconds(10)) { courseApp.isUserLoggedIn(token, "matan") }
        }
    }

}