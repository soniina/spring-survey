package learn.spring.survey.service

import io.mockk.junit5.MockKExtension
import learn.spring.survey.config.JwtProperties
import learn.spring.survey.model.User
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.*

@ExtendWith(MockKExtension::class)
class JwtServiceTest {

    private val properties = JwtProperties().apply {
        secret = "my-very-secure-secret-key-with-min-256-bits-length"
        expiration = 3600000
    }

    private val jwtService = JwtService(properties)

    private val user = User("alice", "test@example.com", "password")


    @Test
    fun `should generate and extract email from token`() {
        val token = jwtService.generateToken(user.email)

        assertEquals(user.email, jwtService.extractEmail(token))
    }


    @Test
    fun `should return true for valid token`() {
        val token = jwtService.generateToken(user.email)

        assertTrue(jwtService.isTokenValid(token, user))
    }

    @Test
    fun `should return false for token with different user`() {
        val token = jwtService.generateToken(user.email)
        val otherUser = User("bob", "bob@example.com", "password")

        assertFalse(jwtService.isTokenValid(token, otherUser))
    }

    @Test
    fun `should return false for expired token`() {
        val expiredProperties = JwtProperties().apply {
            secret = "my-very-secure-secret-key-with-min-256-bits-length"
            expiration = 0
        }
        val expiredJwtService = JwtService(expiredProperties)

        val token = expiredJwtService.generateToken(user.email)
        assertFalse(jwtService.isTokenValid(token, user))
    }

    @Test
    fun `should throw when extracting from invalid token`() {
        val invalidToken = "invalid.token.string"
        assertFailsWith<Exception> { jwtService.extractEmail(invalidToken) }
    }

}