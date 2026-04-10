package com.numera.covenant.api

import com.numera.covenant.application.WaiverService
import com.numera.covenant.application.WaiverLetterResult
import com.numera.covenant.dto.WaiverRequest
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/covenants/waivers")
class WaiverController(
    private val waiverService: WaiverService,
) {

    /**
     * Process a Waive or Not-Waive action.
     * Returns the generated letter HTML; the client renders it for download or sends via email.
     *
     * POST /api/covenants/waivers
     */
    @PostMapping
    fun processWaiver(
        @RequestBody request: WaiverRequest,
        @RequestParam actorId: UUID,
    ): WaiverLetterResult = waiverService.processWaiver(request, actorId)
}
