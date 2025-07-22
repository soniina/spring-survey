package learn.spring.survey.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import learn.spring.survey.dto.LoginRequest
import learn.spring.survey.dto.RegisterRequest
import learn.spring.survey.model.User
import learn.spring.survey.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.Test
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class UserServiceTest {

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var passwordEncoder: PasswordEncoder

    @MockK
    lateinit var jwtService: JwtService

    @InjectMockKs
    lateinit var userService: UserService

    @Test
    fun `should register new user`() {

        val username = "alice"
        val email = "test@example.com"
        val rawPassword = "password"
        val hashedPassword = "hashedPassword"
        val token = "token"

        every { userRepository.existsByEmail(email) } returns false
        every { userRepository.save(any()) } answers { firstArg() }
        every { passwordEncoder.encode(rawPassword) } returns hashedPassword
        every { jwtService.generateToken(email) } returns token

        val response = userService.register(
            RegisterRequest(username, email, rawPassword)
        )

        val savedUserSlot = slot<User>()
        verify(exactly = 1) { userRepository.save(capture(savedUserSlot)) }

        val savedUser = savedUserSlot.captured
        assertEquals(username, savedUser.username)
        assertEquals(email, savedUser.email)
        assertEquals(hashedPassword, savedUser.password)

        assertEquals(token, response.token)
    }

    @Test
    fun `should not register when email already exists`() {
        val email = "test@example.com"
        every { userRepository.existsByEmail(email) } returns true

        val ex = assertFailsWith<IllegalArgumentException> {
            userService.register(
                RegisterRequest("alice",email, "password")
            )
        }

        assertEquals("Email already registered", ex.message)
    }

    @Test
    fun `should login user successfully`() {
        val email = "test@example.com"
        val rawPassword = "password"
        val hashedPassword = "hashed-password"
        val token = "token"

        every { userRepository.findByEmail(email) } returns User("alice", email, hashedPassword)
        every { passwordEncoder.matches(rawPassword, hashedPassword) } returns true
        every { jwtService.generateToken(email) } returns token

        val response = userService.login(
            LoginRequest(email, rawPassword)
        )

        assertEquals(token, response.token)
    }


    @Test
    fun `should not login when email doesn't exist`() {
        val email = "test@example.com"

        every { userRepository.findByEmail(email) } returns null

        val ex = assertFailsWith<IllegalArgumentException> {
            userService.login(
                LoginRequest(email, "password")
            )
        }

        assertEquals("Invalid email or password", ex.message)
    }


    @Test
    fun `should not login when password is incorrect`() {
        val email = "test@example.com"
        val hashedPassword = "hashed-password"

        every { userRepository.findByEmail(email) } returns User("alice", email, hashedPassword)
        every { passwordEncoder.matches(any(), hashedPassword) } returns false

        val ex = assertFailsWith<IllegalArgumentException> {
            userService.login(
                LoginRequest(email, "password")
            )
        }

        assertEquals("Invalid email or password", ex.message)
    }
}