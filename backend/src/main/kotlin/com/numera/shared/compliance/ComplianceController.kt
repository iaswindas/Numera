package com.numera.shared.compliance

import com.numera.shared.security.CurrentUserProvider
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import jakarta.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/compliance")
class ComplianceController(
    private val dataExportService: DataExportService,
    private val dataDeletionService: DataDeletionService,
    private val consentService: ConsentService,
    private val currentUserProvider: CurrentUserProvider,
) {

    // ── Data Export (GDPR Art. 20) ───────────────────────────────

    @GetMapping("/export/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    fun exportUserData(
        @PathVariable userId: String,
        @RequestHeader("X-Tenant-Id") tenantId: String,
    ): ResponseEntity<ByteArray> {
        val json = dataExportService.exportAsJson(tenantId, userId)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"numera-data-export-$userId.json\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
    }

    // ── Data Deletion (GDPR Art. 17) ────────────────────────────

    @DeleteMapping("/erasure/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun eraseUserData(
        @PathVariable userId: String,
        @RequestHeader("X-Tenant-Id") tenantId: String,
    ): ResponseEntity<DataDeletionService.DeletionResult> {
        val result = dataDeletionService.eraseUserData(tenantId, userId)
        return ResponseEntity.ok(result)
    }

    // ── Consent Management ──────────────────────────────────────

    @GetMapping("/consents/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    fun getConsents(
        @PathVariable userId: String,
        @RequestHeader("X-Tenant-Id") tenantId: String,
    ): ResponseEntity<List<ConsentService.ConsentRecord>> {
        return ResponseEntity.ok(consentService.getActiveConsents(tenantId, userId))
    }

    @PostMapping("/consents/{userId}/{consentType}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    fun grantConsent(
        @PathVariable userId: String,
        @PathVariable consentType: String,
        @RequestHeader("X-Tenant-Id") tenantId: String,
        request: HttpServletRequest,
    ): ResponseEntity<ConsentService.ConsentRecord> {
        val record = consentService.grantConsent(tenantId, userId, consentType, request.remoteAddr)
        return ResponseEntity.ok(record)
    }

    @DeleteMapping("/consents/{userId}/{consentType}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    fun revokeConsent(
        @PathVariable userId: String,
        @PathVariable consentType: String,
        @RequestHeader("X-Tenant-Id") tenantId: String,
    ): ResponseEntity<Map<String, Any>> {
        val rows = consentService.revokeConsent(tenantId, userId, consentType)
        return ResponseEntity.ok(mapOf("revoked" to rows))
    }

    @GetMapping("/consents/{userId}/{consentType}/check")
    fun checkConsent(
        @PathVariable userId: String,
        @PathVariable consentType: String,
        @RequestHeader("X-Tenant-Id") tenantId: String,
    ): ResponseEntity<Map<String, Boolean>> {
        return ResponseEntity.ok(mapOf("hasConsent" to consentService.hasConsent(tenantId, userId, consentType)))
    }
}
