package learn.spring.survey.controller

import jakarta.validation.Valid
import learn.spring.survey.model.User
import learn.spring.survey.dto.RegisterRequest
import learn.spring.survey.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class UserController(private val userService: UserService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): User {
        return userService.register(request)
    }
}