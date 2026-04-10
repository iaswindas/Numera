package com.numera.shared.security

import com.numera.shared.config.NumeraProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val config: NumeraProperties,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(config.jwt.secret.padEnd(64, 'x').toByteArray())

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
        claims.expiration.after(Date())
    }.getOrDefault(false)

    fun getAuthentication(token: String): Authentication {
        val claims = parseClaims(token)
        val authorities = (claims["roles"] as? Collection<*>)
            ?.map { SimpleGrantedAuthority(it.toString()) }
            ?: emptyList()
        val tenantId = claims["tenantId"]?.toString()
        if (tenantId != null) {
            TenantContext.set(tenantId)
        }
        return UsernamePasswordAuthenticationToken(claims.subject, token, authorities)
    }
}