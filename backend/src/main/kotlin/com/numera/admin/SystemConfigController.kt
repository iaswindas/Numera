package com.numera.admin

import com.numera.shared.config.NumeraProperties
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/system-config")
@PreAuthorize("hasRole('ADMIN')")
class SystemConfigController(
    private val numeraProperties: NumeraProperties,
) {
    @GetMapping
    fun current(): Map<String, Any> = mapOf(
        "tenantId" to "00000000-0000-0000-0000-000000000001",
        "ml" to mapOf(
            "ocrServiceUrl" to numeraProperties.ml.ocrServiceUrl,
            "mlServiceUrl" to numeraProperties.ml.mlServiceUrl,
        ),
        "storage" to mapOf(
            "endpoint" to numeraProperties.storage.endpoint,
            "bucket" to numeraProperties.storage.bucket,
        ),
        "jwt" to mapOf(
            "accessExpirationMs" to numeraProperties.jwt.accessExpirationMs,
            "refreshExpirationMs" to numeraProperties.jwt.refreshExpirationMs,
        ),
    )

    @PostMapping
    fun save(@RequestBody request: Map<String, Any?>): Map<String, Any?> = mapOf(
        "saved" to true,
        "message" to "Configuration accepted",
        "payload" to request,
    )
}
