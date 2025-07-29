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

        val survey = Survey(title = request.title, author = author, type = request.type)

        val questions = request.questions.map { questionRequest ->
            Question(
                text = questionRequest.text,
                type = questionRequest.type,
                survey = survey
            ).apply {
                if (questionRequest.type != QuestionType.TEXT) {
                    questionRequest.options?.forEach { optionRequest ->
                        options.add(AnswerOption(text = optionRequest.text, isCorrect = optionRequest.isCorrect ?: false, points = optionRequest.points ?: 0, question = this))
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
    fun submitAnswers(surveyId: Long, request: AnswerRequest, user: User): SubmissionResponse {
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

        return calculateSubmissionResult(survey, answerRepository.saveAll(answers))
    }


    private fun calculateSubmissionResult(survey: Survey, answers: List<Answer>): SubmissionResponse {
        var totalScore = 0
        val correctAnswers = mutableMapOf<Long, Boolean>()

        answers.forEach { answer ->
            answer.question?.let { question ->
                when (survey.type) {
                    SurveyType.SCORED -> {
                        totalScore += answer.selectedOptions.sumOf { it.option?.points ?: 0 }
                    }

                    SurveyType.QUIZ -> when (question.type) {
                        QuestionType.SINGLE_CHOICE -> {
                            val isCorrect = answer.selectedOptions.singleOrNull()?.option?.isCorrect == true

                            correctAnswers[question.id] = isCorrect
                            if (isCorrect) totalScore ++
                        }

                        QuestionType.MULTIPLE_CHOICE -> {
                            val correctOptionIds = question.options.filter { it.isCorrect }.map { it.id }.toSet()

                            val selectedOptionIds = answer.selectedOptions.mapNotNull { it.option?.id }.toSet()

                            val isCorrect = selectedOptionIds == correctOptionIds
                            correctAnswers[question.id] = isCorrect
                            if (isCorrect) totalScore ++
                        }

                        else -> {}
                    }

                    else -> {}
                }
            }
        }

        return SubmissionResponse(
            answers = answers.map { AnswerResponse.fromEntity(it) },
            totalScore = if (survey.type != SurveyType.STANDARD) totalScore else null,
            correctAnswers = if (survey.type == SurveyType.QUIZ) correctAnswers else null
        )
    }

}
