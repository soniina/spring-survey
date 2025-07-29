package learn.spring.survey.dto

import learn.spring.survey.model.Answer
import learn.spring.survey.model.QuestionType
import learn.spring.survey.model.Survey
import learn.spring.survey.model.SurveyType

data class AuthResponse(val token: String)

data class SurveyResponse(
    val id: Long,
    val type: SurveyType,
    val title: String,
    val authorId: Long,
    val questions: List<QuestionResponse>
) {
    companion object Factory {
        fun fromEntity(survey: Survey) =
            SurveyResponse(
                id = survey.id,
                type = survey.type,
                title = survey.title,
                authorId = survey.author?.id ?: 0L,
                questions = survey.questions.map { question ->
                    QuestionResponse(question.id, question.text, question.type, question.options
                        .takeIf { it.isNotEmpty() }
                        ?.map { OptionResponse(it.id, it.text) })
                }
            )
    }
}

data class QuestionResponse(
    val id: Long,
    val text: String,
    val type: QuestionType,
    val options: List<OptionResponse>? = null
)

data class OptionResponse(
    val id: Long,
    val text: String
)

data class SubmissionResponse(
    val answers: List<AnswerResponse>,
    val totalScore: Int? = null,
    val correctAnswers: Map<Long, Boolean>? = null
)

data class AnswerResponse(
    val id: Long,
    val text: String? = null,
    val optionIds: List<Long>? = null,
    val questionId: Long
) {
    companion object Factory {
        fun fromEntity(answer: Answer) =
            AnswerResponse(
                id = answer.id,
                text = answer.text,
                optionIds = answer.selectedOptions.takeIf { it.isNotEmpty() }?.map { it.option?.id ?: 0L},
                questionId = answer.question?.id ?: 0L
            )
    }
}
