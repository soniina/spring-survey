package learn.spring.survey.controller

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import learn.spring.survey.dto.QuestionResponse
import learn.spring.survey.dto.SurveyRequest
import learn.spring.survey.dto.SurveyResponse
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
    private val surveyRequest = SurveyRequest("survey", questions)

    private val author = User("alice", "test@example.com", "password")
    private val questionResponses = questions.mapIndexed { i, q -> QuestionResponse(id = i.toLong(), text = q) }
    private val surveyResponse = SurveyResponse(0, "survey", 0, questionResponses )

    @Test
    fun `should return 201 with survey response when valid request`() {
        Mockito.`when`(surveyService.createSurvey(surveyRequest, author)).thenReturn(surveyResponse)

        val authentication = UsernamePasswordAuthenticationToken(UserPrincipal(author), null, emptyList())
        SecurityContextHolder.getContext().authentication = authentication

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
    fun `should return 400 when request in invalid`() {
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

        val authentication = UsernamePasswordAuthenticationToken(UserPrincipal(author), null, emptyList())
        SecurityContextHolder.getContext().authentication = authentication

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
        val surveyId = 1L

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
    fun `should return 404 when survey not found`() {
        val surveyId = 99L
        Mockito.`when`(surveyService.getSurveyQuestions(surveyId))
            .thenThrow(EntityNotFoundException("Survey with id=$surveyId not found"))

        mockMvc.get("/surveys/$surveyId") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("Survey with id=$surveyId not found") }
        }
    }
}