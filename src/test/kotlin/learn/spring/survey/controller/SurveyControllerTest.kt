package learn.spring.survey.controller

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import learn.spring.survey.dto.*
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

    private val questions = listOf("question1", "question2", "question3")
    private val questionResponses = questions.mapIndexed { i, q ->
        QuestionResponse(id = i.toLong(), text = q)
    }

    private val answers = listOf("answer1", "answer2", "answer3")
    private val answersRequest = AnswerRequest(answers)
    private val answersResponse = answers.mapIndexed { i, a ->
        AnswerResponse(id = i.toLong(), text = a, questionId = i.toLong(), surveyId = surveyId)
    }

    private val surveyId = 1L
    private val surveyRequest = SurveyRequest("survey", questions)
    private val author = User(id = 1L, email = "author@example.com", username = "author", password = "1234")
    private val surveyResponse = SurveyResponse(surveyId, "survey", author.id, questionResponses)

    private val respondent = User(id = 2L, email = "user@example.com", username = "user", password = "pass")

    private fun authenticate(user: User) {
        val authentication = UsernamePasswordAuthenticationToken(UserPrincipal(user), null, emptyList())
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Test
    fun `should return 201 and create survey`() {
        Mockito.`when`(surveyService.createSurvey(surveyRequest, author)).thenReturn(surveyResponse)

        authenticate(author)

        val result = mockMvc.post("/surveys") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(surveyRequest)
        }.andExpect {
            status { isCreated() }
        }.andReturn()

        val actualResponse = objectMapper.readValue(
            result.response.contentAsString,
            SurveyResponse::class.java
        )

        assertEquals(surveyResponse, actualResponse)
    }

    @Test
    fun `should return 400 when survey request is invalid`() {
        val invalidRequest = SurveyRequest("", emptyList())

        mockMvc.post("/surveys") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(invalidRequest)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.title") { value("must not be blank") }
            jsonPath("$.errors.questions") { value("must not be empty") }
        }
    }

    @Test
    fun `should return 400 when survey title already exists`() {
        Mockito.`when`(surveyService.createSurvey(surveyRequest, author)).thenThrow(IllegalArgumentException("Survey with this title already exists"))

        authenticate(author)

        mockMvc.post("/surveys") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(surveyRequest)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Survey with this title already exists") }
        }
    }

    @Test
    fun `should return 200 with questions when survey exists`() {
        Mockito.`when`(surveyService.getSurveyQuestions(surveyId)).thenReturn(questionResponses)

        mockMvc.get("/surveys/$surveyId") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.size()") { value(questionResponses.size) }
            jsonPath("$[0].text") { value(questions[0]) }
            jsonPath("$[1].text") { value(questions[1]) }
            jsonPath("$[2].text") { value(questions[2]) }
        }
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
    fun `should return 201 and submit answers`() {
        Mockito.`when`(surveyService.submitAnswers(surveyId, answersRequest, respondent)).thenReturn(answersResponse)

        authenticate(respondent)

        val result = mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(answersRequest)
        }.andExpect {
            status { isCreated() }
        }.andReturn()

        val actualResponse = objectMapper.readValue(
            result.response.contentAsString,
            Array<AnswerResponse>::class.java
        ).toList()

        assertEquals(answersResponse, actualResponse)
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
    fun `should return 404 and not submit answers when survey not found`() {
        Mockito.`when`(surveyService.submitAnswers(surveyId, answersRequest, respondent))
            .thenThrow(EntityNotFoundException("Survey with id=$surveyId not found"))

        authenticate(respondent)

        mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(answersRequest)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("Survey with id=$surveyId not found") }
        }
    }

    @Test
    fun `should return 400 when number of answers does not match number of questions`() {
        val mismatchedAnswers = AnswerRequest(listOf("only one answer"))

        Mockito.`when`(surveyService.submitAnswers(surveyId, mismatchedAnswers, respondent))
            .thenThrow(IllegalArgumentException("Number of answers must match number of questions"))

        authenticate(respondent)

        mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mismatchedAnswers)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Number of answers must match number of questions") }
        }
    }

    @Test
    fun `should return 409 when user already submitted answers`() {
        Mockito.`when`(surveyService.submitAnswers(surveyId, answersRequest, respondent))
            .thenThrow(learn.spring.survey.exception.ConflictException("User already submitted answers for this survey"))
        authenticate(respondent)

        mockMvc.post("/surveys/$surveyId/submit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(answersRequest)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("User already submitted answers for this survey") }
        }
    }


}