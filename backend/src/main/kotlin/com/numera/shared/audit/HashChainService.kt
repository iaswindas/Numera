package com.numera.shared.audit

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Service
class HashChainService(
    private val objectMapper: ObjectMapper,
) {
    fun computeHash(previousHash: String, payload: Any): String {
        val body = objectMapper.writeValueAsString(payload)
        val material = "$previousHash::$body"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}