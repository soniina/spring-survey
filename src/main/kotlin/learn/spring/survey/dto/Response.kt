package learn.spring.survey.dto

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
                authorId = survey.author.id,
                questions = survey.questions.map {
                    QuestionResponse(it.id, it.text)
                }
            )
        }
    }
}

data class QuestionResponse(
    val id: Long,
    val text: String
)