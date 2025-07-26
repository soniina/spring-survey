package learn.spring.survey.dto

import learn.spring.survey.model.Answer
import learn.spring.survey.model.QuestionType
import learn.spring.survey.model.Survey

data class AuthResponse(val token: String)

data class SurveyResponse(
    val id: Long,
    val title: String,
    val authorId: Long,
    val questions: List<QuestionResponse>
) {
    companion object Factory {
        fun fromEntity(survey: Survey): SurveyResponse {
            return SurveyResponse(
                id = survey.id,
                title = survey.title,
                authorId = survey.author?.id ?: 0L,
                questions = survey.questions.map {
                    QuestionResponse(it.id, it.text, it.type)
                }
            )
        }
    }
}

data class QuestionResponse(
    val id: Long,
    val text: String,
    val type: QuestionType
)

data class AnswerResponse(
    val id: Long,
    val text: String,
    val questionId: Long,
    val surveyId: Long
) {
    companion object Factory {
        fun fromEntity(answer: Answer): AnswerResponse {
            return AnswerResponse(
                id = answer.id,
                text = answer.text,
                questionId = answer.question?.id ?: 0L,
                surveyId = answer.question?.id ?: 0L
            )
        }
    }
}
