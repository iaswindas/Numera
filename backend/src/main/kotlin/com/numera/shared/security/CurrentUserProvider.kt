package com.numera.shared.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class CurrentUserProvider {
    fun email(): String? = SecurityContextHolder.getContext().authentication?.name
}