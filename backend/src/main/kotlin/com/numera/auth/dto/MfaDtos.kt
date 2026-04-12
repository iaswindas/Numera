package com.numera.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class MfaSetupResponse(
    val secret: String,
    val qrUri: String,
)

data class MfaVerifyRequest(
    @field:NotBlank
    @field:Size(min = 6, max = 8)
    val code: String = "",
)

data class MfaBackupCodesResponse(
    val backupCodes: List<String>,
)

data class MfaLoginRequest(
    @field:NotBlank
    val email: String = "",
    @field:NotBlank
    val password: String = "",
    val mfaCode: String? = null,
)
