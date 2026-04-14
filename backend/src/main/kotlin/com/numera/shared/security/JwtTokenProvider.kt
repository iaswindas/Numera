package com.numera.shared.security

import com.numera.shared.config.NumeraProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val config: NumeraProperties,
    private val redisTemplate: StringRedisTemplate,
) {
    private val key: SecretKey = run {
        val normalized = config.jwt.secret.trim()
        require(normalized.isNotEmpty()) {
            "JWT secret is required. Set JWT_SECRET to a strong random value."
        }
        val insecureDefaults = setOf(
            "change-me-please-change-me-please-change-me",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        )
        require(normalized !in insecureDefaults) {
            "JWT secret uses a known insecure default. Set JWT_SECRET to a strong random value."
        }
        val secretBytes = normalized.toByteArray(Charsets.UTF_8)
        require(secretBytes.size >= 32) {
            "JWT secret must be at least 32 bytes (256 bits). Current length: ${secretBytes.size}. " +
                "Set a strong secret via JWT_SECRET environment variable."
        }
        Keys.hmacShaKeyFor(secretBytes)
    }

    fun generateAccessToken(email: String, tenantId: String, roles: List<String>): String {
        val now = Date()
        val expiry = Date(now.time + config.jwt.accessExpirationMs)
        return Jwts.builder()
            .subject(email)
            .issuedAt(now)
            .expiration(expiry)
            .claim("tenantId", tenantId)
            .claim("roles", roles)
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(email: String): String {
        val now = Date()
        val expiry = Date(now.time + config.jwt.refreshExpirationMs)
        return Jwts.builder()
            .subject(email)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun parseClaims(token: String): Claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    fun isValid(token: String): Boolean = runCatching {
        val claims = parseClaims(token)
        if (claims.expiration.before(Date())) return false
        val hash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
        if (redisTemplate.hasKey("numera:jwt:blacklist:$hash") == true) return false
        true
    }.getOrDefault(false)

    fun blacklistToken(token: String) {
        runCatching {
            val claims = parseClaims(token)
            val remainingMs = claims.expiration.time - System.currentTimeMillis()
            if (remainingMs > 0) {
                val hash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
                redisTemplate.opsForValue().set("numera:jwt:blacklist:$hash", "1", Duration.ofMillis(remainingMs))
            }
        }
    }

    fun getAuthentication(token: String): Authentication {
        val claims = parseClaims(token)
        val authorities = (claims["roles"] as? Collection<*>)
            ?.map { role ->
                val name = role.toString()
                SimpleGrantedAuthority(if (name.startsWith("ROLE_")) name else "ROLE_$name")
            }
            ?: emptyList()
        val tenantId = claims["tenantId"]?.toString()
        if (tenantId != null) {
            TenantContext.set(tenantId)
        }
        return UsernamePasswordAuthenticationToken(claims.subject, token, authorities)
    }
}