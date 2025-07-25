package learn.spring.survey.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityNotFoundException
import learn.spring.survey.dto.AnswerRequest
import learn.spring.survey.dto.SurveyRequest
import learn.spring.survey.exception.ConflictException
import learn.spring.survey.model.Answer
import learn.spring.survey.model.Question
import learn.spring.survey.model.Survey
import learn.spring.survey.model.User
import learn.spring.survey.repository.AnswerRepository
import learn.spring.survey.repository.SurveyRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@ExtendWith(MockKExtension::class)
class SurveyServiceTest {

    @MockK
    lateinit var surveyRepository: SurveyRepository

    @MockK
    lateinit var answerRepository: AnswerRepository

    @InjectMockKs
    lateinit var surveyService: SurveyService

    private val surveyId = 1L
    private val title = "survey"
    private val author = User(id = 1L, email = "author@example.com", username = "author", password = "1234")
    private val respondent = User(id = 2L, email = "user@example.com", username = "user", password = "pass")

    private val survey = Survey(
        id = surveyId,
        title = title,
        author = author
    )

    private val question1 = Question(1L, "question1", survey)
    private val question2 = Question(2L, "question2", survey)
    private val questions = listOf(question1.text, question2.text)

    private val answer1 = Answer(1L, "answer1", question1, respondent)
    private val answer2 = Answer(2L, "answer2", question2, respondent)
    private val answers = listOf(answer1.text, answer2.text)

    @BeforeTest
    fun setUp() {
        survey.questions.add(question1)
        survey.questions.add(question2)
    }


    @Test
    fun `should create new survey successfully`() {
        every { surveyRepository.existsByTitle(title) } returns false
        every { surveyRepository.save(any()) } answers { firstArg() }

        val result = surveyService.createSurvey(SurveyRequest(title, questions), author)

        assertEquals(title, result.title)
        assertEquals(questions.size, result.questions.size)
        assertEquals(questions, result.questions.map { it.text })

        val savedSurveySlot = slot<Survey>()
        verify(exactly = 1) { surveyRepository.save(capture(savedSurveySlot)) }

        val savedSurvey = savedSurveySlot.captured
        assertEquals(title, savedSurvey.title)
        assertNotNull(savedSurvey.author)
        assertEquals(author.email, savedSurvey.author!!.email)
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
        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)

        val result = surveyService.getSurveyQuestions(surveyId)

        assertEquals(questions, result.map { it.text} )
    }


    @Test
    fun `should not get questions when survey doesn't exist`() {
        every { surveyRepository.findById(surveyId) } returns Optional.empty()

        val ex = assertFailsWith<EntityNotFoundException> {
            surveyService.getSurveyQuestions(surveyId)
        }

        assertEquals("Survey with id=$surveyId not found", ex.message)
    }

    @Test
    fun `should submit answers successfully`() {
        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)
        every { answerRepository.existsByRespondentIdAndQuestionSurveyId(respondent.id, surveyId) } returns false
        every { answerRepository.saveAll(any<List<Answer>>()) } answers { firstArg<List<Answer>>() }

        val result = surveyService.submitAnswers(surveyId, AnswerRequest(answers), respondent)

        assertEquals(answers.size, result.size)
        assertEquals(answers, result.map { it.text })
        assertEquals(listOf(answer1, answer2).map { it.question?.id ?: -1}, result.map { it.questionId })
        assertEquals(survey.id, result.first().surveyId)

        val savedAnswersSlot = slot<List<Answer>>()
        verify(exactly = 1) { answerRepository.saveAll(capture(savedAnswersSlot)) }

        val savedAnswers = savedAnswersSlot.captured
        assertEquals(answers.size, savedAnswers.size)
        assertEquals(answers, savedAnswers.map { it.text })
        assertEquals(listOf(answer1, answer2).map { it.question?.id ?: -1}, savedAnswers.map { it.question?.id ?: -2 })
        assertEquals(respondent.id, savedAnswers.first().respondent?.id ?: -1)
    }

    @Test
    fun `should not submit answers when survey doesn't exist`() {
        every { surveyRepository.findById(surveyId) } returns Optional.empty()

        val ex = assertFailsWith<EntityNotFoundException> {
            surveyService.submitAnswers(surveyId, AnswerRequest(answers), respondent)
        }

        assertEquals("Survey with id=$surveyId not found", ex.message)
    }

    @Test
    fun `should not submit answers when number of answers does not match the number of questions`() {
        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)

        val mismatchedAnswers = listOf(answer1.text, answer2.text, "excess answer")

        val ex = assertFailsWith<IllegalArgumentException> {
            surveyService.submitAnswers(surveyId, AnswerRequest(mismatchedAnswers), respondent)
        }

        assertEquals("Number of answers must match number of questions", ex.message)
    }

    @Test
    fun `should not submit answers when user already submitted answers`() {
        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)
        every { answerRepository.existsByRespondentIdAndQuestionSurveyId(respondent.id, surveyId) } returns true

        val ex = assertFailsWith<ConflictException> {
            surveyService.submitAnswers(surveyId, AnswerRequest(answers), respondent)
        }

        assertEquals("User already submitted answers for this survey", ex.message)
    }

}