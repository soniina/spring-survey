package learn.spring.survey.service

import learn.spring.survey.dto.RegisterRequest
import learn.spring.survey.model.User
import learn.spring.survey.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class UserService(private val userRepository: UserRepository) {
    fun register(request: RegisterRequest): User {
        if (userRepository.existsByEmail(request.email))
            throw IllegalArgumentException("Email already registered")
        val user = User(
            username = request.username,
            email = request.email,
            password = request.password
        )
        return userRepository.save(user)
    }
}