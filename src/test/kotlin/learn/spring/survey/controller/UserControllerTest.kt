package learn.spring.survey.controller

import com.fasterxml.jackson.databind.ObjectMapper
import learn.spring.survey.config.SecurityBeansConfig
import learn.spring.survey.dto.AuthResponse
import learn.spring.survey.dto.LoginRequest
import learn.spring.survey.dto.RegisterRequest
import learn.spring.survey.service.UserService
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.Test
import kotlin.test.assertEquals


@WebMvcTest(UserController::class)
@Import(SecurityBeansConfig::class)
class UserControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var userService: UserService


    private val registerRequest = RegisterRequest("alice", "test@example.com", "password")
    private val loginRequest = LoginRequest("test@example.com", "password")
    private val authResponse = AuthResponse("jwt-token")

    @Test
    fun `should register user successfully`() {
        Mockito.`when`(userService.register(registerRequest)).thenReturn(authResponse)

        val result = mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(registerRequest)
        }.andExpect {
            status { isCreated() }
        }.andReturn()

        val actualResponse = objectMapper.readValue(
            result.response.contentAsString,
            AuthResponse::class.java
        )

        assertEquals(authResponse, actualResponse)
    }

    @Test
    fun `should not register invalid request`() {
        val invalidRequest = RegisterRequest("", "email", "pass")

        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(invalidRequest)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.username") { value("must not be blank") }
            jsonPath("$.errors.email") { value("must be a well-formed email address") }
            jsonPath("$.errors.password") { value("size must be between 6 and 2147483647") }
        }
    }

    @Test
    fun `should not register if email already exists`() {
        Mockito.`when`(userService.register(registerRequest)).thenThrow(IllegalArgumentException("Email already registered"))

        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(registerRequest)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Email already registered") }
        }
    }

    @Test
    fun `should login user successfully`() {
        Mockito.`when`(userService.login(loginRequest)).thenReturn(authResponse)

        val result = mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(loginRequest)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val actualResponse = objectMapper.readValue(
            result.response.contentAsString,
            AuthResponse::class.java
        )

        assertEquals(authResponse, actualResponse)
    }

    @Test
    fun `should not login invalid request`() {
        val invalidRequest = LoginRequest("email", "")

        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(invalidRequest)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.email") { value("must be a well-formed email address") }
            jsonPath("$.errors.password") { value("must not be blank") }
        }
    }

}