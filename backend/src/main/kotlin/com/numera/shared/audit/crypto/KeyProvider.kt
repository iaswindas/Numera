package com.numera.shared.audit.crypto

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstraction for chameleon-hash trapdoor key management.
 *
 * Production deployments should replace [InMemoryKeyProvider] with a
 * Vault- or KMS-backed implementation (e.g. AWS KMS, Azure Key Vault,
 * HashiCorp Vault Transit engine).
 */
interface KeyProvider {
    fun getTrapdoorKey(tenantId: String): ByteArray
    fun getVerificationKey(tenantId: String): ByteArray
}

/**
 * Dev / test key provider that generates and caches keys in memory.
 * NOT suitable for production — keys are lost on restart and are not
 * protected at rest.
 */
@Component
class InMemoryKeyProvider : KeyProvider {

    private val keys = ConcurrentHashMap<String, ByteArray>()
    private val secureRandom = SecureRandom()

    override fun getTrapdoorKey(tenantId: String): ByteArray =
        keys.computeIfAbsent(tenantId) { generateKey() }

    override fun getVerificationKey(tenantId: String): ByteArray =
        // In a real EC scheme the verification key is derived from the trapdoor.
        // For the HMAC demonstrator, trapdoor == verification key.
        getTrapdoorKey(tenantId)

    private fun generateKey(): ByteArray {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        return key
    }
}
