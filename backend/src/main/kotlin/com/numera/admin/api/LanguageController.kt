package com.numera.admin.api

import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/languages")
@PreAuthorize("hasRole('ADMIN')")
class LanguageController {
    private val languages = mutableListOf(
        mutableMapOf<String, Any>("code" to "en", "name" to "English", "ocrSupported" to true, "enabled" to true),
        mutableMapOf<String, Any>("code" to "de", "name" to "German", "ocrSupported" to true, "enabled" to true),
        mutableMapOf<String, Any>("code" to "fr", "name" to "French", "ocrSupported" to true, "enabled" to true),
        mutableMapOf<String, Any>("code" to "es", "name" to "Spanish", "ocrSupported" to true, "enabled" to false),
        mutableMapOf<String, Any>("code" to "ja", "name" to "Japanese", "ocrSupported" to false, "enabled" to false),
        mutableMapOf<String, Any>("code" to "zh", "name" to "Chinese", "ocrSupported" to false, "enabled" to false),
    )

    @GetMapping
    fun list(): List<Map<String, Any>> = languages

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody body: Map<String, Any>): Map<String, Any> {
        val code = body["code"] as? String ?: throw IllegalArgumentException("code required")
        val name = body["name"] as? String ?: throw IllegalArgumentException("name required")
        if (languages.any { it["code"] == code }) {
            throw IllegalArgumentException("Language code already exists: $code")
        }
        val lang = mutableMapOf<String, Any>(
            "code" to code,
            "name" to name,
            "ocrSupported" to (body["ocrSupported"] as? Boolean ?: false),
            "enabled" to (body["enabled"] as? Boolean ?: true),
        )
        languages.add(lang)
        return lang
    }

    @PutMapping("/{code}/toggle")
    fun toggle(@PathVariable code: String, @RequestBody body: Map<String, Boolean>): Map<String, Any> {
        val lang = languages.find { it["code"] == code }
            ?: throw IllegalArgumentException("Language not found: $code")
        lang["enabled"] = body["enabled"] ?: !(lang["enabled"] as Boolean)
        return lang
    }
}
