package learn.spring.survey.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import learn.spring.survey.config.JwtProperties
import learn.spring.survey.model.User
import org.springframework.stereotype.Service
import java.util.*

@Service
class JwtService(private val properties: JwtProperties) {

    private val key = Keys.hmacShaKeyFor(properties.secret.toByteArray())

    fun generateToken(email: String): String {
        val now = Date()
        val expiry = Date(now.time + properties.expiration)

        return Jwts.builder()
            .setSubject(email)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key)
            .compact()
    }

    private fun parseToken(token: String) = Jwts.parserBuilder()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .body

    fun extractEmail(token: String): String = parseToken(token).subject

    fun isTokenValid(token: String, user: User): Boolean {
        return try {
            val claims = parseToken(token)
            claims.expiration.after(Date()) && claims.subject == user.email
        } catch (e: Exception) {
            false
        }
    }
}