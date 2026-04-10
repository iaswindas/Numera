package com.numera.shared.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val tokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header?.startsWith("Bearer ") == true) {
            val token = header.removePrefix("Bearer ").trim()
            if (tokenProvider.isValid(token)) {
                SecurityContextHolder.getContext().authentication = tokenProvider.getAuthentication(token)
            }
        }
        filterChain.doFilter(request, response)
        TenantContext.clear()
    }
}