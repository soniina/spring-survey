package learn.spring.survey.integration

import learn.spring.survey.dto.*
import org.junit.jupiter.api.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.BeforeTest
import kotlin.test.Test

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SurveyIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private fun registerAndLogin(): String {
        val registerRequest = RegisterRequest("alice", "test@example.com", "password")

        webTestClient.post()
            .uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerRequest)
            .exchange()
            .expectStatus().isCreated

        val loginRequest = LoginRequest("test@example.com", "password")
        val loginResponse = webTestClient.post()
            .uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(loginRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody(AuthResponse::class.java)
            .returnResult()
            .responseBody

        assertNotNull(loginResponse)
        return loginResponse.token
    }

    @BeforeTest
    fun clean() {
        jdbcTemplate.execute("DELETE FROM answers")
        jdbcTemplate.execute("DELETE FROM questions")
        jdbcTemplate.execute("DELETE FROM surveys")
        jdbcTemplate.execute("DELETE FROM users")
    }

    @Test
    fun `should create survey, get questions and submit answers`() {
        val token = registerAndLogin()

        val surveyRequest = SurveyRequest(
            title = "Programming Languages",
            questions = listOf(
                "What is your primary programming language?",
                "How many years have you been using it?"
            )
        )

        val survey = webTestClient.post()
            .uri("/surveys")
            .header("Authorization", "Bearer $token")
            .bodyValue(surveyRequest)
            .exchange()
            .expectStatus().isCreated
            .expectBody(SurveyResponse::class.java)
            .returnResult()
            .responseBody

        assertNotNull(survey)

        val questions = webTestClient.get()
            .uri("/surveys/${survey.id}")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(QuestionResponse::class.java)
            .hasSize(2)
            .returnResult()
            .responseBody

        assertNotNull(questions)

        val answerRequest = AnswerRequest(answers = listOf("Kotlin", "5 years"))

        webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(answerRequest)
            .exchange()
            .expectStatus().isCreated
            .expectBodyList(AnswerResponse::class.java)
            .hasSize(2)
    }

    @Test
    fun `should not allow duplicate survey titles`() {
        val token = registerAndLogin()
        val title = "Unique Title"

        webTestClient.post()
            .uri("/surveys")
            .header("Authorization", "Bearer $token")
            .bodyValue(SurveyRequest(title, listOf("question1")))
            .exchange()
            .expectStatus().isCreated

        webTestClient.post()
            .uri("/surveys")
            .header("Authorization", "Bearer $token")
            .bodyValue(SurveyRequest(title, listOf("question1")))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Survey with this title already exists")
    }

    @Test
    fun `should prevent multiple submissions from same user`() {
        val token = registerAndLogin()

        val survey = webTestClient.post()
            .uri("/surveys")
            .header("Authorization", "Bearer $token")
            .bodyValue(SurveyRequest("Single Submission", listOf("Favorite color?")))
            .exchange()
            .expectStatus().isCreated
            .expectBody(SurveyResponse::class.java)
            .returnResult()
            .responseBody

        assertNotNull(survey)

        webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(AnswerRequest(listOf("Blue")))
            .exchange()
            .expectStatus().isCreated

        webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(AnswerRequest(listOf("Red")))
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.error").isEqualTo("User already submitted answers for this survey")
    }

    @Test
    fun `should validate answer count matches question count`() {
        val token = registerAndLogin()

        val survey = webTestClient.post()
            .uri("/surveys")
            .header("Authorization", "Bearer $token")
            .bodyValue(SurveyRequest("Survey", listOf("Q1", "Q2", "Q3")))
            .exchange()
            .expectStatus().isCreated
            .expectBody(SurveyResponse::class.java)
            .returnResult()
            .responseBody

        assertNotNull(survey)

        webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(AnswerRequest(listOf("A1", "A2")))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Number of answers must match number of questions")


        webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(AnswerRequest(listOf("A1", "A2", "A3")))
            .exchange()
            .expectStatus().isCreated
    }

    @Test
    fun `should return 404 for non-existent survey`() {
        val token = registerAndLogin()

        webTestClient.get()
            .uri("/surveys/999")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("Survey with id=999 not found")

        webTestClient.post()
            .uri("/surveys/999/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(AnswerRequest(listOf("Answer")))
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("Survey with id=999 not found")
    }

    @Test
    fun `should not allow unauthenticated user create survey`() {
        val surveyRequest = SurveyRequest("Unauthenticated Survey", listOf("Question"))

        webTestClient.post()
            .uri("/surveys")
            .bodyValue(surveyRequest)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `should not allow unauthenticated get survey questions`() {
        val token = registerAndLogin()
        val survey = webTestClient.post()
            .uri("/surveys")
            .header("Authorization", "Bearer $token")
            .bodyValue(SurveyRequest("survey", listOf("question")))
            .exchange()
            .expectStatus().isCreated
            .expectBody(SurveyResponse::class.java)
            .returnResult()
            .responseBody

        assertNotNull(survey)

        webTestClient.get()
            .uri("/surveys/${survey.id}")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `should not allow unauthenticated submit answers`() {
        val token = registerAndLogin()
        val survey = webTestClient.post()
            .uri("/surveys")
            .header("Authorization", "Bearer $token")
            .bodyValue(SurveyRequest("survey", listOf("question")))
            .exchange()
            .expectStatus().isCreated
            .expectBody(SurveyResponse::class.java)
            .returnResult()
            .responseBody

        assertNotNull(survey)

        webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .bodyValue(AnswerRequest(listOf("answer")))
            .exchange()
            .expectStatus().isUnauthorized
    }

}
