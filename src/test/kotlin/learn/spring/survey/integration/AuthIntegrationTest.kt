package learn.spring.survey.integration

import learn.spring.survey.dto.AuthResponse
import learn.spring.survey.dto.LoginRequest
import learn.spring.survey.dto.RegisterRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeTest
    fun clean() {
        jdbcTemplate.execute("DELETE FROM questions")
        jdbcTemplate.execute("DELETE FROM surveys")
        jdbcTemplate.execute("DELETE FROM users")
    }

    @Test
    fun `should register and login user`() {
        val registerRequest = RegisterRequest("alice", "test@example.com", "password")
        val registerResponse = webTestClient.post()
            .uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerRequest)
            .exchange()
            .expectStatus().isCreated
            .expectBody(AuthResponse::class.java)
            .returnResult()
            .responseBody

        assertTrue(registerResponse?.token?.isNotBlank() ?: false)

        val loginRequest = LoginRequest("test@example.com", "password")
        val loginResponse = webTestClient.post()
            .uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(loginRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody(AuthResponse::class.java)
            .returnResult().responseBody

        assertTrue(loginResponse?.token?.isNotBlank() ?: false)
    }


    @Test
    fun `should prevent duplicate registration`() {
        val request = RegisterRequest("alice", "duplicate@test.com", "password")
        webTestClient.post()
            .uri("/auth/register")
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated

        webTestClient.post()
            .uri("/auth/register")
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Email already registered")
    }


    @Test
    fun `should validate login credentials`() {
        val registerRequest = RegisterRequest("alice", "test@example.com", "password")
        webTestClient.post()
            .uri("/auth/register")
            .bodyValue(registerRequest)
            .exchange()
            .expectStatus().isCreated

        val invalidPasswordRequest = LoginRequest("example@test.com", "wrong-password")
        webTestClient.post()
            .uri("/auth/login")
            .bodyValue(invalidPasswordRequest)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Invalid email or password")

        val invalidEmailRequest = LoginRequest("example@test.com", "wrong-password")
        webTestClient.post()
            .uri("/auth/login")
            .bodyValue(invalidEmailRequest)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Invalid email or password")
    }

    @Test
    fun `should validate request bodies`() {
        val invalidRegisterRequest = RegisterRequest("", "invalid-email", "")
        webTestClient.post()
            .uri("/auth/register")
            .bodyValue(invalidRegisterRequest)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.errors").exists()

        val invalidLoginRequest = LoginRequest("", "")
        webTestClient.post()
            .uri("/auth/login")
            .bodyValue(invalidLoginRequest)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.errors").exists()

    }

}