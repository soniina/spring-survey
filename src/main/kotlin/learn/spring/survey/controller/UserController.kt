package learn.spring.survey.controller

import jakarta.validation.Valid
import learn.spring.survey.dto.AuthResponse
import learn.spring.survey.dto.LoginRequest
import learn.spring.survey.dto.RegisterRequest
import learn.spring.survey.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class UserController(private val userService: UserService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): AuthResponse {
        return userService.register(request)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse {
        return userService.login(request)
    }
}