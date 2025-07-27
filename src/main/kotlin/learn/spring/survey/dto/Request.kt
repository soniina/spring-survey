package learn.spring.survey.dto

import com.fasterxml.jackson.annotation.JsonTypeInfo
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import learn.spring.survey.model.QuestionType
import learn.spring.survey.model.SurveyType

data class RegisterRequest(
    @field:NotBlank
    val username: String,

    @field:Email
    val email: String,

    @field:NotBlank
    @field:Size(min = 6)
    val password: String
)

data class LoginRequest(
    @field:Email
    val email: String,

    @field:NotBlank
    @field:Size(min = 6)
    val password: String
)

data class SurveyRequest(
    @field:NotBlank
    val title: String,

    val type: SurveyType = SurveyType.STANDARD,

    @field:Size(min = 1)
    @field:Valid
    val questions: List<@NotBlank QuestionRequest>
)

data class QuestionRequest(
    @field:NotBlank
    val text: String,

    val type: QuestionType = QuestionType.TEXT,

    val options: List<OptionRequest>? = null
) {
    @get:AssertTrue(message = "Options must be present for choice-based questions and absent for text questions")
    val isValidOptions: Boolean
        get() = when (type) {
            QuestionType.TEXT -> options.isNullOrEmpty()
            QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE -> !options.isNullOrEmpty() && options.distinct().size == options.size
        }
}

data class OptionRequest(
    @field:NotBlank
    val text: String,

    val points: Int? = null,
    val isCorrect: Boolean = false
)

data class AnswerRequest(
    @field:NotEmpty
    val answers: List<AnswerSubmission>
)

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
sealed class AnswerSubmission {
    data class TextAnswer(val text: String) : AnswerSubmission()
    data class SingleChoiceAnswer(val optionId: Long) : AnswerSubmission()
    data class MultipleChoiceAnswer(val optionIds: List<Long>) : AnswerSubmission()
}
