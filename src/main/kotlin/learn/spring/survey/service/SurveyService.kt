package learn.spring.survey.service

import learn.spring.survey.dto.SurveyResponse.Factory.fromEntity
import learn.spring.survey.repository.SurveyRepository
import org.springframework.stereotype.Service
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import learn.spring.survey.dto.*
import learn.spring.survey.exception.ConflictException
import learn.spring.survey.model.*
import learn.spring.survey.repository.AnswerOptionRepository
import learn.spring.survey.repository.AnswerRepository

@Service
class SurveyService(
    private val surveyRepository: SurveyRepository,
    private val answerRepository: AnswerRepository,
    private val optionRepository: AnswerOptionRepository
) {

    @Transactional
    fun createSurvey(request: SurveyRequest, author: User): SurveyResponse {
        if (surveyRepository.existsByTitle(request.title))
            throw IllegalArgumentException("Survey with this title already exists")

        val survey = Survey(title = request.title, author = author)

        val questions = request.questions.map { questionRequest ->
            Question(
                text = questionRequest.text,
                type = questionRequest.type,
                survey = survey
            ).apply {
                if (questionRequest.type != QuestionType.TEXT) {
                    questionRequest.options?.forEach { optionText ->
                        options.add(AnswerOption(text = optionText, question = this))
                    }
                }
            }
        }
        survey.questions.addAll(questions)

        return fromEntity(surveyRepository.save(survey))
    }

    fun getSurveyQuestions(surveyId: Long): List<QuestionResponse> {
        val survey = surveyRepository.findById(surveyId)
            .orElseThrow { EntityNotFoundException("Survey with id=$surveyId not found") }

        return survey.questions.map { question ->
            QuestionResponse(id = question.id, text = question.text, type = question.type,
                options = if (question.type != QuestionType.TEXT) {
                    question.options.map { OptionResponse(it.id, it.text) }
                } else null
            )
        }
    }

    @Transactional
    fun submitAnswers(surveyId: Long, request: AnswerRequest, user: User): List<AnswerResponse> {
        val survey = surveyRepository.findById(surveyId)
            .orElseThrow { EntityNotFoundException("Survey with id=$surveyId not found") }

        if (request.answers.size != survey.questions.size) {
            throw IllegalArgumentException("Number of answers must match number of questions")
        }

        if (answerRepository.existsByRespondentIdAndQuestionSurveyId(user.id, surveyId)) {
            throw ConflictException("User already submitted answers for this survey")
        }

        val answers = mutableListOf<Answer>()
        survey.questions.forEachIndexed { index, question ->
            val submission = request.answers[index]

            val answer = when (question.type) {
                QuestionType.TEXT -> {
                    if (submission !is AnswerSubmission.TextAnswer) {
                        throw IllegalArgumentException("Invalid answer type for text question")
                    }
                    Answer(question = question, respondent = user, text = submission.text)
                }

                QuestionType.SINGLE_CHOICE -> {
                    if (submission !is AnswerSubmission.SingleChoiceAnswer) {
                        throw IllegalArgumentException("Invalid answer type for single choice question")
                    }
                    val option = optionRepository.findById(submission.optionId)
                        .orElseThrow { throw IllegalArgumentException("Option not found") }

                    Answer(question = question, respondent = user).apply {
                        selectedOptions.add(SelectedOption(answer = this, option = option))
                    }
                }

                QuestionType.MULTIPLE_CHOICE -> {
                    if (submission !is AnswerSubmission.MultipleChoiceAnswer) {
                        throw IllegalArgumentException("Invalid answer type for multiple choice question")
                    }
                    val options = optionRepository.findAllById(submission.optionIds)
                    if (options.size != submission.optionIds.size) {
                        throw IllegalArgumentException("Some options not found")
                    }

                    Answer(question = question, respondent = user).apply {
                        selectedOptions.addAll(options.map {  SelectedOption(answer = this, option = it) })
                    }
                }
            }
            answers.add(answer)
        }

        return answerRepository.saveAll(answers).map { AnswerResponse.fromEntity(it) }
    }

}
