package com.numera.covenant.api

import com.numera.covenant.application.WaiverService
import com.numera.covenant.application.WaiverLetterResult
import com.numera.covenant.dto.WaiverGenerationRequest
import com.numera.covenant.dto.WaiverLetterResponse
import com.numera.covenant.dto.WaiverRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/covenants/waivers")
class WaiverController(
    private val waiverService: WaiverService,
) {

    /**
     * Process a Waive or Not-Waive action (legacy endpoint).
     * Returns the generated letter HTML; the client renders it for download or sends via email.
     * 
     * POST /api/covenants/waivers
     */
    @PostMapping
    fun processWaiver(
        @RequestBody request: WaiverRequest,
        @RequestParam actorId: UUID,
    ): WaiverLetterResult = waiverService.processWaiver(request, actorId)

    /**
     * Generate a waiver letter for a covenant monitoring item.
     * Persists the letter for later retrieval and PDF generation.
     *
     * POST /api/covenants/waivers/generate
     */
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    fun generateWaiverLetter(
        @RequestBody request: WaiverGenerationRequest,
        @RequestParam actorId: UUID,
    ): WaiverLetterResponse = waiverService.generateWaiverLetter(request, actorId)

    /**
     * Send a previously generated waiver letter to recipients via email.
     * Marks the letter as sent and closes the monitoring item.
     *
     * POST /api/covenants/waivers/{id}/send
     */
    @PostMapping("/{id}/send")
    fun sendWaiverLetter(
        @PathVariable id: UUID,
        @RequestParam recipients: List<String>,
        @RequestParam actorId: UUID,
    ): WaiverLetterResponse = waiverService.sendWaiverLetter(id, recipients, actorId)

    /**
     * Download a waiver letter as PDF.
     *
     * GET /api/covenants/waivers/{id}/download
     */
    @GetMapping("/{id}/download")
    fun downloadWaiverLetter(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val pdfBytes = waiverService.downloadWaiverLetter(id)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header("Content-Disposition", "attachment; filename=\"waiver-letter-$id.pdf\"")
            .body(pdfBytes)
    }
}
