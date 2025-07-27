package learn.spring.survey.service

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.Keys
import learn.spring.survey.config.JwtProperties
import learn.spring.survey.model.User
import org.springframework.stereotype.Service
import java.security.SignatureException
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

    fun extractEmail(token: String): String {
        return try {
            parseToken(token).subject
        } catch (e: Exception) {
            throw when (e) {
                is ExpiredJwtException -> e
                is SignatureException -> e
                else -> MalformedJwtException("Invalid token", e)
            }
        }
    }

    fun isTokenValid(token: String, user: User): Boolean {
        val claims = try {
            parseToken(token)
        } catch (e: Exception) {
            return false
        }
        return claims.subject == user.email
    }
}