package learn.spring.survey.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import learn.spring.survey.dto.RegisterRequest
import learn.spring.survey.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class UserServiceTest {

    @MockK
    lateinit var userRepository: UserRepository

    @InjectMockKs
    lateinit var userService: UserService

    @Test
    fun `should register new user`() {
        every { userRepository.existsByEmail("test@example.com") } returns false
        every { userRepository.save(any()) } answers { firstArg() }

        val user = userService.register(
            RegisterRequest("alice","test@example.com", "password")
        )

        assertEquals("alice", user.username)
        assertEquals("test@example.com", user.email)
        assertEquals("password", user.password)
    }

    @Test
    fun `should throw if email already exists`() {
        every { userRepository.existsByEmail("test@example.com") } returns true

        val ex = assertFailsWith<IllegalArgumentException> {
            userService.register(
                RegisterRequest("alice","test@example.com", "password")
            )
        }

        assertEquals("Email already registered", ex.message)
    }

}