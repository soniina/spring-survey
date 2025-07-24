package learn.spring.survey.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityNotFoundException
import learn.spring.survey.dto.SurveyRequest
import learn.spring.survey.model.Question
import learn.spring.survey.model.Survey
import learn.spring.survey.model.User
import learn.spring.survey.repository.SurveyRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class SurveyServiceTest {

    @MockK
    lateinit var surveyRepository: SurveyRepository

    @InjectMockKs
    lateinit var surveyService: SurveyService

    private val title = "survey"
    private val author = User("alice", "test@example.com", "password")
    private val questions = listOf("question1", "question2")

    @Test
    fun `should create new survey`() {
        every { surveyRepository.existsByTitle(title) } returns false
        every { surveyRepository.save(any()) } answers { firstArg() }

        val response = surveyService.createSurvey(SurveyRequest(title, questions), author)

        assertEquals(title, response.title)
        assertEquals(2, response.questions.size)
        assertEquals("question1", response.questions[0].text)
        assertEquals("question2", response.questions[1].text)

        val savedSurveySlot = slot<Survey>()
        verify(exactly = 1) { surveyRepository.save(capture(savedSurveySlot)) }

        val savedSurvey = savedSurveySlot.captured
        assertEquals(title, savedSurvey.title)
        assertEquals(author.email, savedSurvey.author.email)
        assertEquals(questions, savedSurvey.questions.map { it.text })
    }

    @Test
    fun `should not create when title already exists`() {
        every { surveyRepository.existsByTitle(title) } returns true

        val ex = assertFailsWith<IllegalArgumentException> {
            surveyService.createSurvey(SurveyRequest(title, questions), author)
        }

        assertEquals("Survey with this title already exists", ex.message)
    }

    @Test
    fun `should get survey questions successfully`() {
        val surveyId = 1L
        val survey = Survey(id = surveyId, title = title, author = author)
        val questions = questions.map { Question(text = it, survey = survey) }.toMutableList()
        survey.questions.addAll(questions)

        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)

        val result = surveyService.getSurveyQuestions(surveyId)

        assertEquals(questions.map { it.text }, result.map { it.text} )
    }


    @Test
    fun `should not get questions when survey doesn't exist`() {
        val surveyId = 1L
        every { surveyRepository.findById(surveyId) } returns Optional.empty()

        val ex = assertFailsWith<EntityNotFoundException> {
            surveyService.getSurveyQuestions(surveyId)
        }

        assertEquals("Survey with id=$surveyId not found", ex.message)
    }
}