package com.numera.shared.security

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class RateLimitFilter : OncePerRequestFilter() {
    private val buckets = ConcurrentHashMap<String, Bucket>()

    private val rateLimitedPaths = mapOf(
        "/api/auth/login" to Pair(5L, Duration.ofMinutes(1)),
        "/api/auth/register" to Pair(3L, Duration.ofMinutes(1)),
        "/api/auth/mfa/verify" to Pair(5L, Duration.ofMinutes(1)),
        "/api/auth/refresh" to Pair(10L, Duration.ofMinutes(1)),
    )

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val path = request.requestURI
        val rateConfig = rateLimitedPaths[path]

        if (rateConfig != null && request.method == "POST") {
            val ip = request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim() ?: request.remoteAddr
            val key = "$path:$ip"
            val bucket = buckets.computeIfAbsent(key) {
                Bucket.builder()
                    .addLimit { it.capacity(rateConfig.first).refillIntervally(rateConfig.first, rateConfig.second) }
                    .build()
            }

            val probe = bucket.tryConsumeAndReturnRemaining(1)
            if (!probe.isConsumed) {
                response.status = 429
                response.setHeader("Retry-After", (probe.nanosToWaitForRefill / 1_000_000_000).toString())
                response.contentType = "application/json"
                response.writer.write("""{"error":"TOO_MANY_REQUESTS","message":"Rate limit exceeded. Try again later."}""")
                return
            }
            response.setHeader("X-Rate-Limit-Remaining", probe.remainingTokens.toString())
        }

        filterChain.doFilter(request, response)
    }
}
