package learn.spring.survey.dto

import jakarta.validation.constraints.*

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
    val questions: List<@NotBlank String>
)

data class AnswerRequest(
    @field:NotEmpty
    val answers: List<@NotBlank String>
)