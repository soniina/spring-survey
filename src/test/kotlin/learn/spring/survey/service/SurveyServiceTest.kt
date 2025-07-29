package learn.spring.survey.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityNotFoundException
import learn.spring.survey.dto.*
import learn.spring.survey.exception.ConflictException
import learn.spring.survey.model.*
import learn.spring.survey.repository.AnswerOptionRepository
import learn.spring.survey.repository.AnswerRepository
import learn.spring.survey.repository.SurveyRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import kotlin.test.*

@ExtendWith(MockKExtension::class)
class SurveyServiceTest {

    @MockK
    lateinit var surveyRepository: SurveyRepository

    @MockK
    lateinit var answerRepository: AnswerRepository

    @MockK
    lateinit var optionRepository: AnswerOptionRepository

    @InjectMockKs
    lateinit var surveyService: SurveyService

    private val surveyId = 1L
    private val title = "Test Survey"
    private val author = User(id = 1L, email = "author@example.com", username = "author", password = "1234")
    private val respondent = User(id = 2L, email = "user@example.com", username = "user", password = "pass")

    private fun buildQuestion(
        text: String,
        type: QuestionType,
        options: List<OptionRequest>? = null
    ) = QuestionRequest(text = text, type = type, options = options)

    private fun buildOption(
        text: String,
        isCorrect: Boolean? = null,
        points: Int? = null
    ) = OptionRequest(text = text, isCorrect = isCorrect, points = points)

    private val textQuestionId = 1L
    private val singleChoiceQuestionId = 2L
    private val multipleChoiceQuestionId = 3L

    private val option1IdFromSingle = 1L
    private val option2IdFromSingle = 2L
    private val option1IdFromMultiple = 3L
    private val option2IdFromMultiple = 4L
    private val option3IdFromMultiple = 5L

    private fun createSurvey(type: SurveyType = SurveyType.STANDARD): Survey {
        return Survey(
            id = surveyId,
            title = title,
            type = type,
            author = author,
        ).apply {
            questions.addAll(listOf(
                Question(id = textQuestionId, text = "Text Q", type = QuestionType.TEXT, survey = this),
                Question(id = singleChoiceQuestionId, text = "Single Choice", type = QuestionType.SINGLE_CHOICE, survey = this).apply {
                    options.addAll(listOf(
                        AnswerOption(id = option1IdFromSingle, text = "Correct", isCorrect = true, points = 5),
                        AnswerOption(id = option2IdFromSingle, text = "Wrong", isCorrect = false, points = 2)
                    ))
                },
                Question(id = multipleChoiceQuestionId, text = "Multiple Choice", type = QuestionType.MULTIPLE_CHOICE, survey = this).apply {
                    options.addAll(listOf(
                        AnswerOption(id = option1IdFromMultiple, text = "Option A", isCorrect = true, points = 2),
                        AnswerOption(id = option2IdFromMultiple, text = "Option B", points = 3),
                        AnswerOption(id = option3IdFromMultiple, text = "Option C", points = 1)
                    ))
                }
            ))
        }
    }


    @Test
    fun `should create new survey successfully`() {
        val questionRequests = listOf(
            buildQuestion("Text question", QuestionType.TEXT),
            buildQuestion(
                "Single Choice",
                QuestionType.SINGLE_CHOICE,
                options = listOf(buildOption("Correct", true), buildOption("Wrong", false))
            ),
            buildQuestion(
                "Multiple Choice",
                QuestionType.MULTIPLE_CHOICE,
                options = listOf(buildOption("A", points = 1), buildOption("B", points = 2))
            )
        )

        every { surveyRepository.existsByTitle(title) } returns false
        every { surveyRepository.save(any()) } answers { firstArg() }

        val result = surveyService.createSurvey(SurveyRequest(title = title, questions = questionRequests), author)

        assertEquals(title, result.title)
        assertEquals(SurveyType.STANDARD, result.type)
        assertEquals(author.id, result.authorId)
        assertEquals(questionRequests.size, result.questions.size)

        val textQuestion = result.questions[0]
        assertEquals(questionRequests[0].text, textQuestion.text)
        assertEquals(QuestionType.TEXT, textQuestion.type)
        assertNull(textQuestion.options)

        val singleChoiceQuestion = result.questions[1]
        assertEquals(questionRequests[1].text, singleChoiceQuestion.text)
        assertEquals(questionRequests[1].type, singleChoiceQuestion.type)
        assertNotNull(singleChoiceQuestion.options)
        assertEquals(questionRequests[1].options!!.size, singleChoiceQuestion.options!!.size)
        assertEquals(questionRequests[1].options!!.map { it.text }, singleChoiceQuestion.options!!.map { it.text })

        val multiChoiceQuestion = result.questions[2]
        assertEquals(questionRequests[2].text, multiChoiceQuestion.text)
        assertEquals(questionRequests[2].type, multiChoiceQuestion.type)
        assertNotNull(multiChoiceQuestion.options)
        assertEquals(questionRequests[2].options!!.size, multiChoiceQuestion.options!!.size)
        assertEquals(questionRequests[2].options!!.map { it.text }, multiChoiceQuestion.options!!.map { it.text })

        val savedSurveySlot = slot<Survey>()
        verify(exactly = 1) { surveyRepository.save(capture(savedSurveySlot)) }

        val savedSurvey = savedSurveySlot.captured
        assertEquals(title, savedSurvey.title)
        assertNotNull(savedSurvey.author)
        assertEquals(author.id, savedSurvey.author!!.id)

        val savedTextQuestion = savedSurvey.questions[0]
        assertEquals(questionRequests[0].text, savedTextQuestion.text)
        assertEquals(questionRequests[0].type, savedTextQuestion.type)
        assertTrue(savedTextQuestion.options.isEmpty())

        val savedSingleChoice = savedSurvey.questions[1]
        assertEquals(questionRequests[1].text, savedSingleChoice.text)
        assertEquals(questionRequests[1].type, savedSingleChoice.type)
        assertNotNull(questionRequests[1].options)
        assertEquals(questionRequests[1].options!!.size, savedSingleChoice.options.size)
        assertEquals(questionRequests[1].options!!.map { it.text }, savedSingleChoice.options.map { it.text })
        assertEquals(questionRequests[1].options!!.map { it.isCorrect }, savedSingleChoice.options.map { it.isCorrect })
        assertTrue(savedSingleChoice.options.all { it.points == 0 })

        val savedMultiChoice = savedSurvey.questions[2]
        assertEquals(questionRequests[2].text, savedMultiChoice.text)
        assertEquals(QuestionType.MULTIPLE_CHOICE, savedMultiChoice.type)
        assertEquals(questionRequests[2].options!!.size, savedMultiChoice.options.size)
        assertEquals(questionRequests[2].options!!.map { it.text }, savedMultiChoice.options.map { it.text })
        assertEquals(questionRequests[2].options!!.map { it.points }, savedMultiChoice.options.map { it.points })
        assertTrue(savedMultiChoice.options.none { it.isCorrect })
    }


    @Test
    fun `should not create survey when title already exists`() {
        val questionRequests = listOf(
            buildQuestion("Text question", QuestionType.TEXT),
            buildQuestion(
                "Single Choice",
                QuestionType.SINGLE_CHOICE,
                options = listOf(buildOption("Correct", true), buildOption("Wrong", false))
            ),
            buildQuestion(
                "Multiple Choice",
                QuestionType.MULTIPLE_CHOICE,
                options = listOf(buildOption("A", points = 2), buildOption("B", points = 1))
            )
        )

        every { surveyRepository.existsByTitle(title) } returns true

        val ex = assertFailsWith<IllegalArgumentException> {
            surveyService.createSurvey(SurveyRequest(title = title, questions = questionRequests), author)
        }

        assertEquals("Survey with this title already exists", ex.message)
    }

    @Test
    fun `should create QUIZ survey with correct answer options`() {
        val quizQuestions = listOf(
            QuestionRequest(
                text = "Quiz question",
                type = QuestionType.SINGLE_CHOICE,
                options = listOf(
                    OptionRequest("Correct", isCorrect = true, points = 5),
                    OptionRequest("Wrong", isCorrect = false)
                )
            )
        )
        val request = SurveyRequest(title = "Quiz Survey", type = SurveyType.QUIZ, questions = quizQuestions)

        every { surveyRepository.existsByTitle(any()) } returns false
        every { surveyRepository.save(any()) } answers { firstArg() }

        val result = surveyService.createSurvey(request, author)

        assertEquals(SurveyType.QUIZ, result.type)

        val savedSurveySlot = slot<Survey>()
        verify(exactly = 1) { surveyRepository.save(capture(savedSurveySlot)) }

        val savedQuestions = savedSurveySlot.captured.questions

        assertEquals(quizQuestions.size, savedQuestions.size)
         val quizQuestion = quizQuestions.first()
         val savedQuestion = savedQuestions.first()

        assertEquals(quizQuestion.options!!.map { it.isCorrect ?: false }, savedQuestion.options.map { it.isCorrect })
        assertEquals(quizQuestion.options!!.map { it.points ?: 0}, savedQuestion.options.map { it.points })
    }


    @Test
    fun `should create SCORED survey with points`() {
        val scoredQuestions = listOf(
            QuestionRequest(
                text = "Scored question",
                type = QuestionType.MULTIPLE_CHOICE,
                options = listOf(
                    OptionRequest("Option A", points = 3),
                    OptionRequest("Option B", points = 5)
                )
            )
        )
        val request = SurveyRequest(title = "Scored Survey", type = SurveyType.SCORED, questions = scoredQuestions)

        every { surveyRepository.existsByTitle(any()) } returns false
        every { surveyRepository.save(any()) } answers { firstArg() }

        val result = surveyService.createSurvey(request, author)

        assertEquals(SurveyType.SCORED, result.type)

        val savedSurveySlot = slot<Survey>()
        verify(exactly = 1) { surveyRepository.save(capture(savedSurveySlot)) }

        val savedQuestions = savedSurveySlot.captured.questions

        assertEquals(scoredQuestions.size, savedQuestions.size)
         val scoredQuestion = scoredQuestions.first()
         val savedQuestion = savedQuestions.first()

        assertEquals(scoredQuestion.options!!.map { it.isCorrect ?: false }, savedQuestion.options.map { it.isCorrect })
        assertEquals(scoredQuestion.options!!.map { it.points ?: 0}, savedQuestion.options.map { it.points })
    }

    @Test
    fun `should ignore options for TEXT questions even when provided`() {
        val questions = listOf(
            QuestionRequest(
                text = "Invalid text question",
                type = QuestionType.TEXT,
                options = listOf(OptionRequest("Should not be saved"))
            )
        )

        every { surveyRepository.existsByTitle(any()) } returns false
        every { surveyRepository.save(any()) } answers { firstArg() }

        val result = surveyService.createSurvey(SurveyRequest(title, questions = questions), author)

        assertEquals(SurveyType.STANDARD, result.type)

        val savedSurveySlot = slot<Survey>()
        verify(exactly = 1) { surveyRepository.save(capture(savedSurveySlot)) }

        val savedQuestions = savedSurveySlot.captured.questions

        assertEquals(questions.size, savedQuestions.size)
        assertEquals(QuestionType.TEXT, savedQuestions.first().type)
        assertTrue(savedQuestions.first().options.isEmpty())
    }

    @Test
    fun `should get survey questions successfully`() {
        val survey = createSurvey()

        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)

        val result = surveyService.getSurveyQuestions(surveyId)

        assertEquals(survey.questions.size, result.size)

        assertEquals(survey.questions.map { it.text }, result.map { it.text })
        assertEquals(survey.questions.map { it.type }, result.map { it.type })

        assertNull(result[0].options)
        assertNotNull(result[1].options)
        assertEquals(survey.questions[1].options.map { it.text }, result[1].options!!.map { it.text })
        assertEquals(survey.questions[2].options.map { it.text }, result[2].options!!.map { it.text })
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
    fun `should submit answers successfully for STANDARD survey`() {
        val survey = createSurvey(SurveyType.STANDARD)
        val request = AnswerRequest(listOf(
            AnswerSubmission.TextAnswer("Free text"),
            AnswerSubmission.SingleChoiceAnswer(option1IdFromSingle),
            AnswerSubmission.MultipleChoiceAnswer(listOf(option1IdFromMultiple, option2IdFromMultiple))
        ))

        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)
        every { answerRepository.existsByRespondentIdAndQuestionSurveyId(respondent.id, surveyId) } returns false
        every { optionRepository.findById(option1IdFromSingle) } returns Optional.of(survey.questions[1].options[0])
        every { optionRepository.findAllById(listOf(option1IdFromMultiple, option2IdFromMultiple)) } returns listOf(
            survey.questions[2].options[0],
            survey.questions[2].options[1]
        )
        every { answerRepository.saveAll(any<List<Answer>>()) } answers { firstArg() }

        val result = surveyService.submitAnswers(surveyId, request, respondent)

        assertEquals(request.answers.size, result.answers.size)
        assertNull(result.totalScore)
        assertNull(result.correctAnswers)

        assertEquals("Free text", result.answers[0].text)
        assertNull(result.answers[0].optionIds)

        assertNull(result.answers[1].text)
        assertEquals(listOf(option1IdFromSingle), result.answers[1].optionIds)

        assertNull(result.answers[2].text)
        assertEquals(listOf(option1IdFromMultiple, option2IdFromMultiple), result.answers[2].optionIds)
    }

    @Test
    fun `should not calculate score for STANDARD survey`() {
        val survey = createSurvey()
        val request = AnswerRequest(listOf(
            AnswerSubmission.TextAnswer("Text"),
            AnswerSubmission.SingleChoiceAnswer(option1IdFromSingle),
            AnswerSubmission.MultipleChoiceAnswer(listOf(option1IdFromMultiple, option2IdFromMultiple))
        ))

        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)
        every { answerRepository.existsByRespondentIdAndQuestionSurveyId(any(), any()) } returns false
        every { optionRepository.findById(option1IdFromSingle) } returns Optional.of(survey.questions[1].options[0])
        every { optionRepository.findAllById(any()) } returns survey.questions[2].options.take(2)
        every { answerRepository.saveAll(any<List<Answer>>()) } answers { firstArg() }

        val result = surveyService.submitAnswers(surveyId, request, respondent)

        assertNull(result.totalScore)
        assertNull(result.correctAnswers)
    }

    @Test
    fun `should calculate scores for QUIZ survey`() {
        val survey = createSurvey(SurveyType.QUIZ)
        val request = AnswerRequest(listOf(
            AnswerSubmission.TextAnswer("Ignored for quiz"),
            AnswerSubmission.SingleChoiceAnswer(option1IdFromSingle),
            AnswerSubmission.MultipleChoiceAnswer(listOf(option1IdFromMultiple, option2IdFromMultiple))
        ))

        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)
        every { answerRepository.existsByRespondentIdAndQuestionSurveyId(any(), any()) } returns false
        every { optionRepository.findById(option1IdFromSingle) } returns Optional.of(survey.questions[1].options[0])
        every { optionRepository.findAllById(any()) } returns survey.questions[2].options.take(2)
        every { answerRepository.saveAll(any<List<Answer>>()) } answers { firstArg() }

        val result = surveyService.submitAnswers(surveyId, request, respondent)

        assertEquals(1, result.totalScore)
        assertNotNull(result.correctAnswers)
        assertEquals(2, result.correctAnswers!!.size)
        assertEquals(true, result.correctAnswers!![singleChoiceQuestionId])
        assertEquals(false, result.correctAnswers!![multipleChoiceQuestionId])
    }

    @Test
    fun `should calculate scores for SCORED survey`() {
        val survey = createSurvey(SurveyType.SCORED)
        val request = AnswerRequest(listOf(
            AnswerSubmission.TextAnswer("No points"),
            AnswerSubmission.SingleChoiceAnswer(option1IdFromSingle),
            AnswerSubmission.MultipleChoiceAnswer(listOf(option1IdFromMultiple, option3IdFromMultiple))
        ))

        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)
        every { answerRepository.existsByRespondentIdAndQuestionSurveyId(any(), any()) } returns false
        every { optionRepository.findById(any()) } returns Optional.of(survey.questions[1].options[0])
        every { optionRepository.findAllById(any()) } returns listOf(
            survey.questions[2].options[0],
            survey.questions[2].options[2]
        )
        every { answerRepository.saveAll(any<List<Answer>>()) } answers { firstArg() }

        val result = surveyService.submitAnswers(surveyId, request, respondent)

        assertEquals(8, result.totalScore)
        assertNull(result.correctAnswers)
    }

    @Test
    fun `should not submit answer when survey doesn't exist`() {
        every { surveyRepository.findById(surveyId) } returns Optional.empty()

        val ex = assertFailsWith<EntityNotFoundException> {
            surveyService.submitAnswers(surveyId, AnswerRequest(emptyList()), respondent)
        }
        assertEquals("Survey with id=$surveyId not found", ex.message)
    }


    @Test
    fun `should not submit answers when number of answers does not match the number of questions`() {
        val survey = createSurvey()
        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)

        val ex = assertFailsWith<IllegalArgumentException> {
            surveyService.submitAnswers(surveyId, AnswerRequest(listOf(AnswerSubmission.TextAnswer("Only one"))), respondent)
        }

        assertEquals("Number of answers must match number of questions", ex.message)
    }

    @Test
    fun `should not submit answers when user already submitted answers`() {
        val survey = createSurvey()
        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)
        every { answerRepository.existsByRespondentIdAndQuestionSurveyId(respondent.id, surveyId) } returns true

        val request = AnswerRequest(List(survey.questions.size) {
            AnswerSubmission.TextAnswer("Test")
        })

        val ex = assertFailsWith<ConflictException> {
            surveyService.submitAnswers(surveyId, request, respondent)
        }

        assertEquals("User already submitted answers for this survey", ex.message)
    }

    @Test
    fun `should throw for invalid answer type - text expected`() {
        val survey = createSurvey()
        val request = AnswerRequest(listOf(
            AnswerSubmission.SingleChoiceAnswer(option1IdFromSingle),
            AnswerSubmission.SingleChoiceAnswer(option2IdFromSingle),
            AnswerSubmission.MultipleChoiceAnswer(listOf(option1IdFromMultiple))
        ))

        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)
        every { answerRepository.existsByRespondentIdAndQuestionSurveyId(any(), any()) } returns false

        val exception = assertFailsWith<IllegalArgumentException> {
            surveyService.submitAnswers(surveyId, request, respondent)
        }
        assertEquals("Invalid answer type for text question", exception.message)
    }

    @Test
    fun `should throw for invalid answer type - single choice expected`() {
        val survey = createSurvey()
        val request = AnswerRequest(listOf(
            AnswerSubmission.TextAnswer("Valid"),
            AnswerSubmission.TextAnswer("Invalid for choice"),
            AnswerSubmission.MultipleChoiceAnswer(listOf(option1IdFromMultiple))
        ))

        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)
        every { answerRepository.existsByRespondentIdAndQuestionSurveyId(any(), any()) } returns false

        val exception = assertFailsWith<IllegalArgumentException> {
            surveyService.submitAnswers(surveyId, request, respondent)
        }
        assertEquals("Invalid answer type for single choice question", exception.message)
    }

    @Test
    fun `should throw for invalid answer type - multiple choice expected`() {
        val survey = createSurvey()
        val request = AnswerRequest(listOf(
            AnswerSubmission.TextAnswer("Valid"),
            AnswerSubmission.SingleChoiceAnswer(option1IdFromSingle),
            AnswerSubmission.SingleChoiceAnswer(option1IdFromMultiple)
        ))

        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)
        every { answerRepository.existsByRespondentIdAndQuestionSurveyId(any(), any()) } returns false
        every { optionRepository.findById(any()) } returns Optional.of(survey.questions[1].options[0])

        val exception = assertFailsWith<IllegalArgumentException> {
            surveyService.submitAnswers(surveyId, request, respondent)
        }
        assertEquals("Invalid answer type for multiple choice question", exception.message)
    }

    @Test
    fun `should throw when option not found for single choice`() {
        val survey = createSurvey()
        val request = AnswerRequest(listOf(
            AnswerSubmission.TextAnswer("Text"),
            AnswerSubmission.SingleChoiceAnswer(999L),
            AnswerSubmission.MultipleChoiceAnswer(listOf(option1IdFromMultiple))
        ))

        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)
        every { answerRepository.existsByRespondentIdAndQuestionSurveyId(any(), any()) } returns false
        every { optionRepository.findById(999L) } returns Optional.empty()

        val exception = assertFailsWith<IllegalArgumentException> {
            surveyService.submitAnswers(surveyId, request, respondent)
        }
        assertEquals("Option not found", exception.message)
    }

    @Test
    fun `should throw when options not found for multiple choice`() {
        val survey = createSurvey()
        val request = AnswerRequest(listOf(
            AnswerSubmission.TextAnswer("Text"),
            AnswerSubmission.SingleChoiceAnswer(option1IdFromSingle),
            AnswerSubmission.MultipleChoiceAnswer(listOf(option1IdFromSingle, option2IdFromMultiple))
        ))

        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)
        every { answerRepository.existsByRespondentIdAndQuestionSurveyId(any(), any()) } returns false
        every { optionRepository.findById(any()) } returns Optional.of(survey.questions[1].options[0])
        every { optionRepository.findAllById(listOf(option1IdFromSingle, option2IdFromMultiple)) } returns listOf(
            survey.questions[2].options[0]
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            surveyService.submitAnswers(surveyId, request, respondent)
        }
        assertEquals("Some options not found", exception.message)
    }

    @Test
    fun `should handle multiple choice quiz question correctly`() {
        val survey = createSurvey(SurveyType.QUIZ)

        val request = AnswerRequest(listOf(
            AnswerSubmission.TextAnswer("Ignored"),
            AnswerSubmission.SingleChoiceAnswer(option2IdFromSingle),
            AnswerSubmission.MultipleChoiceAnswer(listOf(option1IdFromMultiple, option2IdFromMultiple))
        ))

        every { surveyRepository.findById(surveyId) } returns Optional.of(survey)
        every { answerRepository.existsByRespondentIdAndQuestionSurveyId(any(), any()) } returns false
        every { optionRepository.findById(option2IdFromSingle) } returns Optional.of(survey.questions[1].options[1])
        every { optionRepository.findAllById(any()) } returns survey.questions[2].options.take(2)
        every { answerRepository.saveAll(any<List<Answer>>()) } answers { firstArg() }

        val result = surveyService.submitAnswers(surveyId, request, respondent)

        assertEquals(0, result.totalScore)
        assertEquals(false, result.correctAnswers?.get(singleChoiceQuestionId))
        assertEquals(false, result.correctAnswers?.get(multipleChoiceQuestionId))
    }
}