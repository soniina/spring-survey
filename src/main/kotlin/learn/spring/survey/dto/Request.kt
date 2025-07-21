package learn.spring.survey.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

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
