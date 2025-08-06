package learn.spring.survey.integration

import learn.spring.survey.dto.*
import learn.spring.survey.model.QuestionType
import learn.spring.survey.model.SurveyType
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        jdbcTemplate.execute("DELETE FROM selected_options")
        jdbcTemplate.execute("DELETE FROM answers")
        jdbcTemplate.execute("DELETE FROM answer_options")
        jdbcTemplate.execute("DELETE FROM questions")
        jdbcTemplate.execute("DELETE FROM surveys")
        jdbcTemplate.execute("DELETE FROM users")
    }

    @Test
    fun `should create STANDARD survey, get questions and submit answers`() {
        val token = registerAndLogin()

        val surveyRequest = SurveyRequest(
            title = "Programming Languages",
            type = SurveyType.STANDARD,
            questions = listOf(
                QuestionRequest(
                    text = "What is your primary programming language?",
                    type = QuestionType.TEXT
                ),
                QuestionRequest(
                    text = "How many years have you been using it?",
                    type = QuestionType.TEXT
                )
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

        val answerRequest = AnswerRequest(
            answers = listOf(
                AnswerSubmission.TextAnswer("Kotlin"),
                AnswerSubmission.TextAnswer("5 years")
            )
        )

        val submission = webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(answerRequest)
            .exchange()
            .expectStatus().isCreated
            .expectBody(SubmissionResponse::class.java)
            .returnResult()
            .responseBody

        assertNotNull(submission)
        assertEquals(2, submission.answers.size)
    }

    @Test
    fun `should create SCORED survey, get questions, submit answers and calculate total score`() {
        val token = registerAndLogin()

        val surveyRequest = SurveyRequest(
            title = "Developer Skills",
            type = SurveyType.SCORED,
            questions = listOf(
                QuestionRequest(
                    text = "Rate your Kotlin skills",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        OptionRequest("Beginner", points = 1),
                        OptionRequest("Intermediate", points = 3),
                        OptionRequest("Expert", points = 5)
                    )
                ),
                QuestionRequest(
                    text = "Select technologies you know",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf(
                        OptionRequest("Spring Boot", points = 3),
                        OptionRequest("Ktor", points = 3),
                        OptionRequest("Micronaut", points = 2)
                    )
                )
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

        val kotlinQuestion = questions.find { it.text == "Rate your Kotlin skills" }!!
        val techQuestion = questions.find { it.text == "Select technologies you know" }!!
        val expertOptionId = kotlinQuestion.options!!.find { it.text == "Expert" }!!.id
        val springOptionId = techQuestion.options!!.find { it.text == "Spring Boot" }!!.id
        val ktorOptionId = techQuestion.options!!.find { it.text == "Ktor" }!!.id

        val answerRequest = AnswerRequest(
            answers = listOf(
                AnswerSubmission.SingleChoiceAnswer(expertOptionId),
                AnswerSubmission.MultipleChoiceAnswer(listOf(springOptionId, ktorOptionId))
            )
        )

        val response = webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(answerRequest)
            .exchange()
            .expectStatus().isCreated
            .expectBody(SubmissionResponse::class.java)
            .returnResult()
            .responseBody

        assertNotNull(response)
        assertEquals(2, response.answers.size)
        assertEquals(11, response.totalScore)
        assertNull(response.correctAnswers)
    }

    @Test
    fun `should create QUIZ survey, get questions, submit answers and check correctness`() {
        val token = registerAndLogin()

        val surveyRequest = SurveyRequest(
            title = "Java Quiz",
            type = SurveyType.QUIZ,
            questions = listOf(
                QuestionRequest(
                    text = "What is the capital of Java?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        OptionRequest("Jakarta", isCorrect = true),
                        OptionRequest("Surabaya", isCorrect = false),
                        OptionRequest("Bandung", isCorrect = false)
                    )
                ),
                QuestionRequest(
                    text = "Which are Java versions?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf(
                        OptionRequest("Java 8", isCorrect = true),
                        OptionRequest("Java 11", isCorrect = true),
                        OptionRequest("Java 15", isCorrect = true),
                        OptionRequest("JavaScript", isCorrect = false)
                    )
                )
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

        val capitalQuestion = questions.find { it.text == "What is the capital of Java?" }!!
        val versionsQuestion = questions.find { it.text == "Which are Java versions?" }!!
        val jakartaOptionId = capitalQuestion.options!!.find { it.text == "Jakarta" }!!.id
        val java8OptionId = versionsQuestion.options!!.find { it.text == "Java 8" }!!.id
        val java11OptionId = versionsQuestion.options!!.find { it.text == "Java 11" }!!.id

        val answerRequest = AnswerRequest(
            answers = listOf(
                AnswerSubmission.SingleChoiceAnswer(jakartaOptionId),
                AnswerSubmission.MultipleChoiceAnswer(listOf(java8OptionId, java11OptionId))
            )
        )

        val response = webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(answerRequest)
            .exchange()
            .expectStatus().isCreated
            .expectBody(SubmissionResponse::class.java)
            .returnResult()
            .responseBody

        assertNotNull(response)
        assertEquals(2, response.answers.size)
        assertEquals(1, response.totalScore)
        assertNotNull(response.correctAnswers)
        assertEquals(true, response.correctAnswers!![capitalQuestion.id])
        assertEquals(false, response.correctAnswers!![versionsQuestion.id])
    }

    @Test
    fun `should validate answer types`() {
        val token = registerAndLogin()

        val surveyRequest = SurveyRequest(
            title = "Type Validation",
            type = SurveyType.STANDARD,
            questions = listOf(
                QuestionRequest(
                    text = "Text question",
                    type = QuestionType.TEXT
                ),
                QuestionRequest(
                    text = "Single choice",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(OptionRequest("Option 1"))
                )
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

        val invalidRequest = AnswerRequest(
            answers = listOf(
                AnswerSubmission.SingleChoiceAnswer(1L),
                AnswerSubmission.SingleChoiceAnswer(1L)
            )
        )

        webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Invalid answer type for text question")
    }


    @Test
    fun `should not allow duplicate survey titles`() {
        val token = registerAndLogin()
        val title = "Unique Survey Title"

        webTestClient.post()
            .uri("/surveys")
            .header("Authorization", "Bearer $token")
            .bodyValue(SurveyRequest(title = title, questions = listOf(QuestionRequest("Q1", QuestionType.TEXT))))
            .exchange()
            .expectStatus().isCreated

        webTestClient.post()
            .uri("/surveys")
            .header("Authorization", "Bearer $token")
            .bodyValue(SurveyRequest(title = title, questions = listOf(QuestionRequest("Q1", QuestionType.SINGLE_CHOICE, listOf(OptionRequest(text = "O1", isCorrect = true))))))
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
            .bodyValue(SurveyRequest(title = "Single Submission", questions = listOf(QuestionRequest("Favorite color?", QuestionType.TEXT))))
            .exchange()
            .expectStatus().isCreated
            .expectBody(SurveyResponse::class.java)
            .returnResult()
            .responseBody

        assertNotNull(survey)

        webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(AnswerRequest(
                answers = listOf(AnswerSubmission.TextAnswer("Blue"))
            ))
            .exchange()
            .expectStatus().isCreated

        webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(AnswerRequest(
                answers = listOf(AnswerSubmission.TextAnswer("Red"))
            ))
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.error").isEqualTo("User already submitted answers for this survey")
    }

    @Test
    fun `should validate answer count matches question count`() {
        val token = registerAndLogin()

        val surveyRequest = SurveyRequest(
            title = "Three Questions",
            type = SurveyType.STANDARD,
            questions = listOf(
                QuestionRequest("Q1", QuestionType.TEXT),
                QuestionRequest("Q2", QuestionType.TEXT),
                QuestionRequest("Q3", QuestionType.TEXT)
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

        webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(AnswerRequest(
                answers = listOf(
                    AnswerSubmission.TextAnswer("A1"),
                    AnswerSubmission.TextAnswer("A2")
                )
            ))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Number of answers must match number of questions")

        webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(AnswerRequest(
                answers = listOf(
                    AnswerSubmission.TextAnswer("A1"),
                    AnswerSubmission.TextAnswer("A2"),
                    AnswerSubmission.TextAnswer("A3"),
                    AnswerSubmission.TextAnswer("A4")
                )
            ))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Number of answers must match number of questions")


        webTestClient.post()
            .uri("/surveys/${survey.id}/submit")
            .header("Authorization", "Bearer $token")
            .bodyValue(AnswerRequest(
                answers = listOf(
                    AnswerSubmission.TextAnswer("A1"),
                    AnswerSubmission.TextAnswer("A2"),
                    AnswerSubmission.TextAnswer("A3")
                )
            ))
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
            .bodyValue(AnswerRequest(listOf(AnswerSubmission.TextAnswer("Answer"))))
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("Survey with id=999 not found")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/surveys", "/surveys/1", "/surveys/1/submit"])
    fun `should enforce authentication for all endpoints`(url: String) {
        val token = registerAndLogin()
        val surveyRequest = SurveyRequest(
            title = "Unauthenticated Survey",
            questions = listOf(
                QuestionRequest("Q1", QuestionType.TEXT)
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

        webTestClient.post()
            .uri(url)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @ParameterizedTest
    @ValueSource(strings = ["/surveys", "/surveys/1", "/surveys/1/submit"])
    fun `should validate token for all endpoints`(url: String) {
        val token = registerAndLogin()
        val surveyRequest = SurveyRequest(
            title = "Unauthenticated Survey",
            questions = listOf(
                QuestionRequest("Q1", QuestionType.TEXT)
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

        webTestClient.post()
            .uri(url)
            .header("Authorization", "Bearer invalid.token")
            .exchange()
            .expectStatus().isUnauthorized
    }

}
