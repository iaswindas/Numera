package com.numera.auth.api

import com.numera.auth.application.AuthService
import com.numera.auth.dto.AuthMeResponse
import com.numera.auth.dto.LoginRequest
import com.numera.auth.dto.LoginResponse
import com.numera.auth.dto.RefreshRequest
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse = authService.login(request)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): LoginResponse = authService.refresh(request)

    @GetMapping("/me")
    fun me(authentication: Authentication): AuthMeResponse = authService.me(authentication.name)
}
