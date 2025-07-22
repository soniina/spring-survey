package learn.spring.survey.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import learn.spring.survey.dto.SurveyRequest
import learn.spring.survey.model.Survey
import learn.spring.survey.model.User
import learn.spring.survey.repository.SurveyRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class SurveyServiceTest {

    @MockK
    lateinit var surveyRepository: SurveyRepository

    @InjectMockKs
    lateinit var surveyService: SurveyService

    @Test
    fun `should create new survey`() {
        val title = "survey"
        val author = User("alice", "test@example.com", "password")
        val questions = listOf("question1", "question2")

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
}