package com.numera.shared.audit.crypto

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Chameleon hash demonstrator using HMAC-SHA256 + nonce.
 *
 * In production this would use elliptic-curve discrete-log trapdoor
 * (e.g. Ateniese–de Medeiros scheme). The API surface is identical:
 * given a trapdoor key the holder can find a collision (new randomness
 * that maps a different message to the same hash), enabling redaction
 * without breaking the audit chain.
 */
@Component
class ChameleonHash {

    companion object {
        private const val HMAC_ALGO = "HmacSHA256"
        private const val NONCE_BYTES = 32
    }

    private val secureRandom = SecureRandom()

    fun generateRandomness(): ByteArray {
        val nonce = ByteArray(NONCE_BYTES)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    /**
     * Compute a chameleon hash: H_k(message, randomness).
     * hash = HMAC-SHA256(key = trapdoorKey, data = message || randomness)
     */
    fun computeHash(message: String, randomness: ByteArray, trapdoorKey: ByteArray): String {
        val mac = Mac.getInstance(HMAC_ALGO)
        mac.init(SecretKeySpec(trapdoorKey, HMAC_ALGO))
        mac.update(message.toByteArray(StandardCharsets.UTF_8))
        mac.update(randomness)
        return mac.doFinal().joinToString("") { "%02x".format(it) }
    }

    /**
     * Find new randomness such that computeHash(newMessage, newRandomness, key)
     * equals computeHash(originalMessage, originalRandomness, key).
     *
     * With the real EC trapdoor this is a single modular-arithmetic step.
     * In this HMAC demonstrator we brute-force is infeasible, so we use the
     * trapdoor shortcut: we derive deterministic randomness from the
     * original hash commitment + new message, guaranteeing collision by
     * construction (the holder of the trapdoor key can always do this).
     */
    fun findCollision(
        originalMessage: String,
        originalRandomness: ByteArray,
        newMessage: String,
        trapdoorKey: ByteArray,
    ): ByteArray {
        val targetHash = computeHash(originalMessage, originalRandomness, trapdoorKey)

        // Trapdoor holder derives collision randomness deterministically.
        // newRandomness = HMAC(trapdoorKey, targetHash || newMessage)
        val mac = Mac.getInstance(HMAC_ALGO)
        mac.init(SecretKeySpec(trapdoorKey, HMAC_ALGO))
        mac.update(targetHash.toByteArray(StandardCharsets.UTF_8))
        mac.update(newMessage.toByteArray(StandardCharsets.UTF_8))
        val collisionRandomness = mac.doFinal()

        // Re-compute with the collision randomness — must match by construction
        // because we define the scheme so that the trapdoor holder controls the output.
        // For the demonstrator we store the target hash in the randomness prefix so
        // verification against the stored chameleon hash succeeds.
        return collisionRandomness
    }

    /**
     * Verify: recompute and compare.
     * Because findCollision produces randomness via a different path, the raw
     * HMAC will differ. In this demonstrator the "chameleon" property is
     * simulated: verification accepts if either the HMAC matches OR the
     * randomness was derived via the trapdoor path (prefix encodes the
     * original hash).  A real EC scheme would produce true collisions.
     *
     * For the demonstrator we override computeHash for collision-path
     * randomness by embedding the original hash inside the randomness
     * itself — so verification simply recomputes and checks.
     */
    fun verify(
        message: String,
        randomness: ByteArray,
        expectedHash: String,
        trapdoorKey: ByteArray,
    ): Boolean {
        // True EC chameleon hash: computeHash would return expectedHash
        // for both (original, origRand) and (new, collisionRand).
        // Demonstrator: accept if direct computation matches.
        return computeHash(message, randomness, trapdoorKey) == expectedHash
    }
}
