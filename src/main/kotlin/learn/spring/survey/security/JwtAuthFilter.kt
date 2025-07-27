package learn.spring.survey.security

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.SignatureException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import learn.spring.survey.repository.UserRepository
import learn.spring.survey.service.JwtService
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Profile("!test")
class JwtAuthFilter(private val jwtService: JwtService, private val userRepository: UserRepository) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val authHeader = request.getHeader("Authorization") ?: return filterChain.doFilter(request, response)
        if (!authHeader.startsWith("Bearer ")) return filterChain.doFilter(request, response)

        val token = authHeader.substringAfter("Bearer ")

        try {
            val email = jwtService.extractEmail(token)
            val user = userRepository.findByEmail(email) ?: throw UserNotFoundException()

            if (!jwtService.isTokenValid(token, user)) {
                throw InvalidTokenException()
            }

            if (SecurityContextHolder.getContext().authentication == null) {
                val userPrincipal = UserPrincipal(user)
                val auth = UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.authorities)
                    .apply { details = WebAuthenticationDetailsSource().buildDetails(request) }
                SecurityContextHolder.getContext().authentication = auth
            }
            filterChain.doFilter(request, response)

        } catch (e: Exception) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"

            val message = "Authentication failed: ${when (e) {
                is ExpiredJwtException -> "JWT token expired"
                is SignatureException -> "JWT signature invalid"
                is MalformedJwtException -> "JWT token malformed"
                is UserNotFoundException -> "User not found"
                is InvalidTokenException -> "Invalid token"
                else -> e.message
            }}"

            response.writer.write("""{"error": "$message"}""")
        }

    }
}

class UserNotFoundException : RuntimeException()
class InvalidTokenException : RuntimeException()