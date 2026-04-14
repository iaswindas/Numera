package com.numera.shared.util

object LogSanitizer {
    private val SENSITIVE_PATTERNS = listOf(
        Regex(""""password"\s*:\s*"[^"]*""""),
        Regex(""""passwordHash"\s*:\s*"[^"]*""""),
        Regex(""""secretKey"\s*:\s*"[^"]*""""),
        Regex(""""accessKey"\s*:\s*"[^"]*""""),
        Regex(""""token"\s*:\s*"[^"]*""""),
        Regex(""""refreshToken"\s*:\s*"[^"]*""""),
        Regex(""""mfaSecret"\s*:\s*"[^"]*""""),
        Regex(""""creditCardNumber"\s*:\s*"[^"]*""""),
    )

    fun sanitize(input: String?): String? {
        if (input == null) return null
        var result: String = input
        for (pattern in SENSITIVE_PATTERNS) {
            result = pattern.replace(result) { match ->
                val key = match.value.substringBefore(':').trim()
                """$key:"[REDACTED]""""
            }
        }
        return result
    }
}
