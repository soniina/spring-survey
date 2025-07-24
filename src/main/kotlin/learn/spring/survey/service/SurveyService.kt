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

@Service
class SurveyService (private val surveyRepository: SurveyRepository) {

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

}