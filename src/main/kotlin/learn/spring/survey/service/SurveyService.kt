package learn.spring.survey.service

import learn.spring.survey.dto.QuestionResponse
import learn.spring.survey.dto.SurveyRequest
import learn.spring.survey.dto.SurveyResponse
import learn.spring.survey.dto.SurveyResponse.Factory.fromEntity
import learn.spring.survey.model.Question
import learn.spring.survey.model.Survey
import learn.spring.survey.model.User
import learn.spring.survey.repository.SurveyRepository
import org.springframework.stereotype.Service
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import learn.spring.survey.dto.AnswerResponse
import learn.spring.survey.exception.ConflictException
import learn.spring.survey.model.Answer
import learn.spring.survey.repository.AnswerRepository

@Service
class SurveyService (private val surveyRepository: SurveyRepository, private val answerRepository: AnswerRepository) {

    @Transactional
    fun createSurvey(request: SurveyRequest, author: User): SurveyResponse {
        if (surveyRepository.existsByTitle(request.title))
            throw IllegalArgumentException("Survey with this title already exists")

        val survey = Survey(title = request.title, author = author)

        val questions = request.questions.map { text ->
            Question(text = text, survey = survey)
        }
        survey.questions.addAll(questions)

        return fromEntity(surveyRepository.save(survey))
    }

    fun getSurveyQuestions(surveyId: Long): List<QuestionResponse> {
        val survey = surveyRepository.findById(surveyId)
            .orElseThrow { EntityNotFoundException("Survey with id=$surveyId not found") }

        return survey.questions.map { question ->
            QuestionResponse(id = question.id, text = question.text)
        }
    }

    @Transactional
    fun submitAnswers(surveyId: Long, answers: List<String>, user: User): List<AnswerResponse> {
        val survey = surveyRepository.findById(surveyId)
            .orElseThrow { EntityNotFoundException("Survey with id=$surveyId not found") }

        if (answers.size != survey.questions.size) {
            throw IllegalArgumentException("Number of answers must match number of questions")
        }

        if (answerRepository.existsByRespondentIdAndQuestionSurveyId(user.id, surveyId)) {
            throw ConflictException("User already submitted answers for this survey")
        }

        val answerEntities = survey.questions.zip(answers).map { (question, text) ->
            Answer(text = text, question = question, respondent = user) //userRepository.getReferenceById()??
        }

        return answerRepository.saveAll(answerEntities)
            .map(AnswerResponse::fromEntity)
    }

}
