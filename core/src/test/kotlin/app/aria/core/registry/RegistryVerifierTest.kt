// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.registry

import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryVerifierTest {
    private val now = 1_000_000_000L
    private val hash64 = "a".repeat(64)
    private val keyA = Ed25519Sign.KeyPair.newKeyPair()
    private val keyB = Ed25519Sign.KeyPair.newKeyPair()
    private val unknown = Ed25519Sign.KeyPair.newKeyPair()

    private fun verifier(maxAge: Long = 86_400_000L) = RegistryVerifier(
        publicKeys = listOf(keyA.publicKey, keyB.publicKey),
        allowedOperations = setOf("fetchPlaylist", "searchDesktop"),
        maxAgeMs = maxAge,
        clock = { now },
    )

    private fun json(
        issuedAt: Long = now,
        hash: String = hash64,
        op: String = "searchDesktop",
        client: String = "ANDROID_MUSIC",
    ) = """{"issuedAtEpochMs":$issuedAt,"operations":{"$op":{"hash":"$hash"}},"killSwitches":{"kugou":true},"youtubeClients":{"music":"$client"}}"""
        .toByteArray()

    private fun sign(payload: ByteArray, key: Ed25519Sign.KeyPair = keyA) =
        Ed25519Sign(key.privateKey).sign(RegistryVerifier.signingInput(payload))

    @Test fun validPayloadVerifies() {
        val p = json()
        val r = verifier().verify(p, sign(p))
        assertTrue(r is VerifyResult.Valid)
        assertEquals(true, (r as VerifyResult.Valid).registry.killSwitches["kugou"])
    }

    @Test fun secondEmbeddedKeyAlsoVerifies() {
        val p = json()
        assertTrue(verifier().verify(p, sign(p, keyB)) is VerifyResult.Valid)
    }

    @Test fun unknownKeyIsRejected() {
        val p = json()
        assertTrue(verifier().verify(p, sign(p, unknown)) is VerifyResult.Invalid)
    }

    @Test fun tamperedPayloadIsRejected() {
        val p = json()
        val sig = sign(p)
        p[p.size - 3] = (p[p.size - 3] + 1).toByte()
        assertTrue(verifier().verify(p, sig) is VerifyResult.Invalid)
    }

    @Test fun signatureOverBarePayloadWithoutContextIsRejected() {
        val p = json()
        val bare = Ed25519Sign(keyA.privateKey).sign(p)
        assertTrue(verifier().verify(p, bare) is VerifyResult.Invalid)
    }

    @Test fun farFutureRegistryIsRejected() {
        val p = json(issuedAt = now + 10 * 60_000L)
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
    }

    @Test fun slightlyFutureRegistryWithinSkewIsAccepted() {
        val p = json(issuedAt = now + 60_000L)
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Valid)
    }

    @Test fun staleRegistryIsRejected() {
        val p = json(issuedAt = now - 90_000_000L)
        assertTrue(verifier(maxAge = 86_400_000L).verify(p, sign(p)) is VerifyResult.Invalid)
    }

    @Test fun badHashIsRejected() {
        val p = json(hash = "XYZ")
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
        val upper = json(hash = "A".repeat(64))
        assertTrue(verifier().verify(upper, sign(upper)) is VerifyResult.Invalid)
    }

    @Test fun operationOutsideAllowlistIsRejected() {
        val p = json(op = "somethingElse")
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
    }

    @Test fun clientConfigMustBePlainData() {
        val p = json(client = "a; rm -rf /")
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
    }

    @Test fun oversizedPayloadIsRejected() {
        val big = ByteArray(65 * 1024) { 'x'.code.toByte() }
        assertTrue(verifier().verify(big, sign(big)) is VerifyResult.Invalid)
    }

    @Test fun malformedJsonIsRejected() {
        val p = "not json".toByteArray()
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
    }
}
