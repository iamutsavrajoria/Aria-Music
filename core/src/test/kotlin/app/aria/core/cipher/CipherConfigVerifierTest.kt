// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.cipher

import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.Assert.*
import org.junit.Test

class CipherConfigVerifierTest {
    private val pair = Ed25519Sign.KeyPair.newKeyPair()
    private val other = Ed25519Sign.KeyPair.newKeyPair()
    private var now = 10_000_000L
    private var lastVersion = 4L
    private val keys = setOf("playerConfigs", "version")

    private fun verifier() = CipherConfigVerifier(listOf(pair.publicKey), keys, maxAgeMs = 7 * 86_400_000L, lastAcceptedVersion = { lastVersion }, clock = { now })
    private fun sign(payload: ByteArray, key: ByteArray = pair.privateKey) =
        Ed25519Sign(key).sign(CipherConfigVerifier.signingInput(payload))
    private fun envelope(version: Long = 5, issued: Long = now, config: String = """{"playerConfigs":{"a":{"regex":"[a-z]+"}}}""") =
        """{"version":$version,"issuedAtEpochMs":$issued,"config":$config}""".toByteArray()
    private fun check(payload: ByteArray, sig: ByteArray = sign(payload)) = verifier().verify(payload, sig)
    private fun assertInvalid(r: CipherVerifyResult, part: String) {
        assertTrue("expected Invalid but was $r", r is CipherVerifyResult.Invalid)
        assertTrue((r as CipherVerifyResult.Invalid).reason, r.reason.contains(part, ignoreCase = true))
    }

    @Test fun validEnvelopePasses() {
        val r = check(envelope()); assertTrue(r.toString(), r is CipherVerifyResult.Valid)
        assertEquals(5L, (r as CipherVerifyResult.Valid).version)
    }
    @Test fun wrongKeyRejected() = assertInvalid(check(envelope(), sign(envelope(), other.privateKey)), "signature")
    @Test fun tamperedPayloadRejected() { val p = envelope(); val s = sign(p); assertInvalid(check(envelope(version = 6), s), "signature") }
    @Test fun oversizedRejected() = assertInvalid(check(ByteArray(256 * 1024 + 1)), "larger")
    @Test fun unknownEnvelopeFieldRejected() =
        assertInvalid(check("""{"version":5,"issuedAtEpochMs":$now,"config":{},"extra":1}""".toByteArray()), "malformed")
    @Test fun unknownTopLevelKeyRejected() = assertInvalid(check(envelope(config = """{"playerConfigs":{},"evil":{}}""")), "key")
    @Test fun rollbackRejected() { assertInvalid(check(envelope(version = 4)), "version"); assertInvalid(check(envelope(version = 2)), "version") }
    @Test fun staleRejected() = assertInvalid(check(envelope(issued = now - 8 * 86_400_000L)), "old")
    @Test fun futureRejected() = assertInvalid(check(envelope(issued = now + 600_000L)), "future")
    @Test fun deepJsonRejected() = assertInvalid(check(envelope(config = """{"playerConfigs":{"a":{"b":{"c":{"d":{"e":{"f":{}}}}}}}}""")), "deep")
    @Test fun longStringRejected() = assertInvalid(check(envelope(config = """{"playerConfigs":{"a":"${"x".repeat(2049)}"}}""")), "long")
    @Test fun unsafeRegexRejected() = assertInvalid(check(envelope(config = """{"playerConfigs":{"a":{"regex":"(a+)+$"}}}""")), "regex")
}
