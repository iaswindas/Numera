package com.numera.auth.application

import com.numera.auth.domain.MfaBackupCode
import com.numera.auth.dto.MfaBackupCodesResponse
import com.numera.auth.dto.MfaSetupResponse
import com.numera.auth.dto.MfaVerifyRequest
import com.numera.auth.infrastructure.MfaBackupCodeRepository
import com.numera.auth.infrastructure.UserRepository
import com.numera.shared.exception.ApiException
import com.numera.shared.exception.ErrorCode
import dev.samstevens.totp.code.CodeGenerator
import dev.samstevens.totp.code.CodeVerifier
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.qr.QrData
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

@Service
class MfaService(
    private val userRepository: UserRepository,
    private val backupCodeRepository: MfaBackupCodeRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    private val secretGenerator = DefaultSecretGenerator(32)
    private val codeGenerator: CodeGenerator = DefaultCodeGenerator(HashingAlgorithm.SHA1, 6)
    private val timeProvider = SystemTimeProvider()
    private val codeVerifier: CodeVerifier = DefaultCodeVerifier(codeGenerator, timeProvider).also {
        it.setTimePeriod(30)
        it.setAllowedTimePeriodDiscrepancy(1)
    }

    /**
     * Step 1: Generate a TOTP secret and return the QR code data URI.
     * The secret is stored on the user but MFA is not yet verified.
     */
    @Transactional
    fun setupMfa(userId: UUID): MfaSetupResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "User not found") }

        if (user.mfaEnabled && user.mfaVerified) {
            throw ApiException(ErrorCode.CONFLICT, "MFA is already enabled")
        }

        val secret = secretGenerator.generate()
        user.mfaSecret = secret
        user.mfaEnabled = false
        user.mfaVerified = false
        userRepository.save(user)

        val qrData = QrData.Builder()
            .label(user.email)
            .secret(secret)
            .issuer("Numera")
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build()

        return MfaSetupResponse(
            secret = secret,
            qrUri = qrData.uri,
        )
    }

    /**
     * Step 2: Verify the first TOTP code to confirm setup.
     * Generates backup codes on successful verification.
     */
    @Transactional
    fun verifyAndEnable(userId: UUID, request: MfaVerifyRequest): MfaBackupCodesResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "User not found") }

        val secret = user.mfaSecret
            ?: throw ApiException(ErrorCode.BAD_REQUEST, "MFA setup not initiated")

        if (!codeVerifier.isValidCode(secret, request.code)) {
            throw ApiException(ErrorCode.UNAUTHORIZED, "Invalid TOTP code")
        }

        user.mfaEnabled = true
        user.mfaVerified = true
        userRepository.save(user)

        // Generate backup codes
        val backupCodes = generateBackupCodes(user.id!!)
        return MfaBackupCodesResponse(backupCodes = backupCodes)
    }

    /**
     * Validate a TOTP code during login.
     */
    fun validateCode(userId: UUID, code: String): Boolean {
        val user = userRepository.findById(userId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "User not found") }

        if (!user.mfaEnabled || !user.mfaVerified) return true // MFA not enabled

        val secret = user.mfaSecret ?: return false

        // Try TOTP code first
        if (codeVerifier.isValidCode(secret, code)) return true

        // Try backup codes
        val backupCodes = backupCodeRepository.findByUserIdAndUsedFalse(userId)
        for (backupCode in backupCodes) {
            if (passwordEncoder.matches(code, backupCode.codeHash)) {
                backupCode.used = true
                backupCodeRepository.save(backupCode)
                return true
            }
        }

        return false
    }

    /**
     * Disable MFA for a user (admin action or self-service).
     */
    @Transactional
    fun disableMfa(userId: UUID) {
        val user = userRepository.findById(userId)
            .orElseThrow { ApiException(ErrorCode.NOT_FOUND, "User not found") }

        user.mfaEnabled = false
        user.mfaVerified = false
        user.mfaSecret = null
        userRepository.save(user)
        backupCodeRepository.deleteByUserId(userId)
    }

    fun isMfaRequired(userId: UUID): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false
        return user.mfaEnabled && user.mfaVerified
    }

    private fun generateBackupCodes(userId: UUID): List<String> {
        backupCodeRepository.deleteByUserId(userId)
        val random = SecureRandom()
        val plainCodes = (1..10).map {
            val bytes = ByteArray(5)
            random.nextBytes(bytes)
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).take(8).uppercase()
        }

        val user = userRepository.findById(userId).orElseThrow()
        plainCodes.forEach { code ->
            backupCodeRepository.save(MfaBackupCode().also {
                it.user = user
                it.codeHash = passwordEncoder.encode(code)
            })
        }

        return plainCodes
    }
}
