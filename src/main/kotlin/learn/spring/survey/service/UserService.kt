package learn.spring.survey.service

import learn.spring.survey.dto.AuthResponse
import learn.spring.survey.dto.LoginRequest
import learn.spring.survey.dto.RegisterRequest
import learn.spring.survey.model.User
import learn.spring.survey.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService(private val userRepository: UserRepository,
                  private val passwordEncoder: PasswordEncoder,
                  private val jwtService: JwtService) {

    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email))
            throw IllegalArgumentException("Email already registered")

        userRepository.save(User(
            username = request.username,
            email = request.email,
            password = passwordEncoder.encode(request.password)
        ))

        return AuthResponse(jwtService.generateToken(request.email))
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email) ?: throw IllegalArgumentException("Invalid email or password")

        if (!passwordEncoder.matches(request.password, user.password)) throw IllegalArgumentException("Invalid email or password")

        return AuthResponse(jwtService.generateToken(request.email))
    }

}