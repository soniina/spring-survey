package learn.spring.survey.dto

import jakarta.validation.constraints.*
import learn.spring.survey.model.QuestionType

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

    @field:NotEmpty
    val questions: List<@NotBlank QuestionRequest>
)

data class QuestionRequest(
    @field:NotBlank
    val text: String,

    val type: QuestionType = QuestionType.TEXT
)

data class AnswerRequest(
    @field:NotEmpty
    val answers: List<@NotBlank String>
)