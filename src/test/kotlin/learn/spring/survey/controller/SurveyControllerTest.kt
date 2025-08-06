package learn.spring.survey.controller

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import learn.spring.survey.dto.*
import learn.spring.survey.dto.AnswerResponse
import learn.spring.survey.exception.ConflictException
import learn.spring.survey.model.QuestionType
import learn.spring.survey.model.SurveyType
import learn.spring.survey.model.User
import learn.spring.survey.security.UserPrincipal
import learn.spring.survey.service.SurveyService
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.*
import kotlin.test.Test
import kotlin.test.assertEquals


@WebMvcTest(SurveyController::class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class SurveyControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var surveyService: SurveyService

    private val surveyId = 1L
    private val userId = 1L
    private val user = User(id = 1L, email = "user@example.com", username = "user", password = "1234")

    private fun authenticate(user: User) {
        val authentication = UsernamePasswordAuthenticationToken(UserPrincipal(user), null, emptyList())
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Test
    fun `should return 201 and create survey`() {
        val request = SurveyRequest(
            title = "New Survey",
            type = SurveyType.STANDARD,
            questions = listOf(
                QuestionRequest(
                    text = "Question 1",
                    type = QuestionType.TEXT
                )
            )
        )

        val response = SurveyResponse(
            id = surveyId,
            type = SurveyType.STANDARD,
            title = request.title,
            authorId = userId,
            questions = listOf(
                QuestionResponse(
                    id = 1L,
                    text = "Question 1",
                    type = QuestionType.TEXT
                )
            )
        )
        Mockito.`when`(surveyService.createSurvey(request, user)).thenReturn(response)

        authenticate(user)

        val result = mockMvc.post("/surveys") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
        }.andReturn()

        val actualResponse = objectMapper.readValue(
            result.response.contentAsString,
            SurveyResponse::class.java
        )

        assertEquals(response, actualResponse)
    }

    @Test
    fun `should return 400 when survey request is invalid`() {
        val invalidRequest = SurveyRequest("", SurveyType.STANDARD, emptyList())

        mockMvc.post("/surveys") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(invalidRequest)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.title") { value("must not be blank") }
            jsonPath("$.errors.questions") { value("size must be between 1 and 2147483647") }
        }
    }

    @Test
    fun `should return 400 when questions options is invalid`() {
        val invalidRequest1 = SurveyRequest(
            title = "Invalid Options 1",
            type = SurveyType.STANDARD,
            questions = listOf(
                QuestionRequest(
                    text = "Text question with options",
                    type = QuestionType.TEXT,
                    options = listOf(OptionRequest("Should not have options"))
                )
            )
        )

        mockMvc.post("/surveys") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(invalidRequest1)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors['questions[0].validOptions']") { value("Options must be present for choice-based questions and absent for text questions") }
        }

        val invalidRequest2 = SurveyRequest(
            title = "Invalid Options 2",
            type = SurveyType.STANDARD,
            questions = listOf(
                QuestionRequest(
                    text = "Choice without options",
                    type = QuestionType.SINGLE_CHOICE
                )
            )
        )

        mockMvc.post("/surveys") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(invalidRequest2)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors['questions[0].validOptions']") { value("Options must be present for choice-based questions and absent for text questions") }
        }
    }


    @Test
    fun `should return 400 when survey title already exists`() {
        val request = SurveyRequest(
            title = "Existing Survey",
            type = SurveyType.STANDARD,
            questions = listOf(
                QuestionRequest(
                    text = "Question 1",
                    type = QuestionType.TEXT
                )
            )
        )

        Mockito.`when`(surveyService.createSurvey(request, user)).thenThrow(IllegalArgumentException("Survey with this title already exists"))
        authenticate(user)

        mockMvc.post("/surveys") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Survey with this title already exists") }
        }
    }

    @Test
    fun `should return 200 with questions when survey exists`() {
        val questions = listOf(
            QuestionResponse(
                id = 1L,
                text = "Question 1",
                type = QuestionType.TEXT
            ),
            QuestionResponse(
                id = 2L,
                text = "Question 2",
                type = QuestionType.SINGLE_CHOICE,
                options = listOf(
                    OptionResponse(1L, "Correct"),
                    OptionResponse(2L, "Wrong")
                )
            ),
            QuestionResponse(
                id = 3L,
                text = "Question 3",
                type = QuestionType.MULTIPLE_CHOICE,
                options = listOf(
                    OptionResponse(3L, "Option A"),
                    OptionResponse(4L, "Option B")
                )
            )
        )

        Mockito.`when`(surveyService.getSurveyQuestions(surveyId)).thenReturn(questions)

        val result = mockMvc.get("/surveys/$surveyId") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val actualResponse = objectMapper.readValue(
            result.response.contentAsString,
            Array<QuestionResponse>::class.java
        ).toList()

        assertEquals(questions, actualResponse)
    }

    @Test
    fun `should return 404 and not get questions when survey not found`() {
        Mockito.`when`(surveyService.getSurveyQuestions(surveyId))
            .thenThrow(EntityNotFoundException("Survey with id=$surveyId not found"))

        mockMvc.get("/surveys/$surveyId") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("Survey with id=$surveyId not found") }
        }
    }

    @Test
    fun `should return 201 and submit answers to STANDARD survey`() {
        val request = AnswerRequest(
            answers = listOf(
                AnswerSubmission.TextAnswer("Answer 1"),
                AnswerSubmission.SingleChoiceAnswer(1L)
            )
        )
        val response = SubmissionResponse(
            answers = listOf(
                AnswerResponse(1L, "Answer 1", questionId = 1L),
                AnswerResponse(2L, optionIds = listOf(1L), questionId = 2L)
            )
        )

        Mockito.`when`(surveyService.submitAnswers(surveyId, request, user)).thenReturn(response)
        authenticate(user)

        mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.answers[0].text") { value("Answer 1") }
            jsonPath("$.answers[1].optionIds[0]") { value(1) }
        }
    }

    @Test
    fun `should return 201 with score and submit answers to SCORED survey`() {
        val request = AnswerRequest(
            answers = listOf(
                AnswerSubmission.SingleChoiceAnswer(1L),
                AnswerSubmission.MultipleChoiceAnswer(listOf(2L, 3L))
            )
        )
        val response = SubmissionResponse(
            answers = listOf(
                AnswerResponse(1L, optionIds = listOf(1L), questionId = 1L),
                AnswerResponse(2L, optionIds = listOf(2L, 3L), questionId = 2L)
            ),
            totalScore = 15
        )

        Mockito.`when`(surveyService.submitAnswers(surveyId, request, user)).thenReturn(response)
        authenticate(user)

        mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.totalScore") { value(15) }
            jsonPath("$.correctAnswers") { value(null) }
        }
    }


    @Test
    fun `should return 201 with correct answers and submit answers to QUIZ survey`() {
        val request = AnswerRequest(
            answers = listOf(
                AnswerSubmission.SingleChoiceAnswer(1L),
                AnswerSubmission.MultipleChoiceAnswer(listOf(2L, 3L))
            )
        )
        val response = SubmissionResponse(
            answers = listOf(
                AnswerResponse(1L, optionIds = listOf(1L), questionId = 1L),
                AnswerResponse(2L, optionIds = listOf(2L, 3L), questionId = 2L)
            ),
            totalScore = 1,
            correctAnswers = mapOf(1L to false, 2L to true)
        )

        Mockito.`when`(surveyService.submitAnswers(surveyId, request, user)).thenReturn(response)
        authenticate(user)

        mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.totalScore") { value(1) }
            jsonPath("$.correctAnswers['1']") { value(false) }
            jsonPath("$.correctAnswers['2']") { value(true) }
        }
    }

    @Test
    fun `should return 409 when answers already submitted`() {
        val request = AnswerRequest(
            answers = listOf(AnswerSubmission.TextAnswer("Answer"))
        )

        Mockito.`when`(surveyService.submitAnswers(surveyId, request, user)).thenThrow(ConflictException("User already submitted answers for this survey"))
        authenticate(user)

        mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("User already submitted answers for this survey") }
        }
    }

    @Test
    fun `should return 400 when answers request in invalid`() {
        val invalidRequest = AnswerRequest(emptyList())

        mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(invalidRequest)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.answers") { value("must not be empty") }
        }
    }


    @Test
    fun `should return 400 when number of answers does not match number of questions`() {
        val request = AnswerRequest(answers = listOf(AnswerSubmission.TextAnswer("Only one answer")))

        Mockito.`when`(surveyService.submitAnswers(surveyId, request, user))
            .thenThrow(IllegalArgumentException("Number of answers must match number of questions"))
        authenticate(user)

        mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Number of answers must match number of questions") }
        }
    }

    @Test
    fun `should return 404 when option not found for single choice question`() {
        val request = AnswerRequest(
            answers = listOf(
                AnswerSubmission.SingleChoiceAnswer(999L)
            )
        )

        Mockito.`when`(surveyService.submitAnswers(surveyId, request, user))
            .thenThrow(EntityNotFoundException("Option not found"))
        authenticate(user)

        mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("Option not found") }
        }
    }

    @Test
    fun `should return 404 when some options not found for multiple choice question`() {
        val request = AnswerRequest(
            answers = listOf(
                AnswerSubmission.TextAnswer("Valid"),
                AnswerSubmission.SingleChoiceAnswer(1L),
                AnswerSubmission.MultipleChoiceAnswer(listOf(10L, 20L))
            )
        )

        Mockito.`when`(surveyService.submitAnswers(surveyId, request, user))
            .thenThrow(EntityNotFoundException("Some options not found"))
        authenticate(user)

        mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("Some options not found") }
        }
    }

    @Test
    fun `should return 400 when invalid answer type for single choice question is provided`() {
        val request = AnswerRequest(
            answers = listOf(
                AnswerSubmission.TextAnswer("Invalid"),
                AnswerSubmission.TextAnswer("Should be single choice"),
                AnswerSubmission.MultipleChoiceAnswer(listOf(2L))
            )
        )

        Mockito.`when`(surveyService.submitAnswers(surveyId, request, user))
            .thenThrow(IllegalArgumentException("Invalid answer type for single choice question"))
        authenticate(user)

        mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid answer type for single choice question") }
        }
    }

    @Test
    fun `should return 400 when invalid answer type for multiple choice question is provided`() {
        val request = AnswerRequest(
            answers = listOf(
                AnswerSubmission.TextAnswer("Invalid"),
                AnswerSubmission.SingleChoiceAnswer(1L),
                AnswerSubmission.SingleChoiceAnswer(2L)
            )
        )

        Mockito.`when`(surveyService.submitAnswers(surveyId, request, user))
            .thenThrow(IllegalArgumentException("Invalid answer type for multiple choice question"))
        authenticate(user)

        mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid answer type for multiple choice question") }
        }
    }


}