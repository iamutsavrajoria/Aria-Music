// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.registry

import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryRepositoryTest {
    private val now = 1_000L
    private val key = Ed25519Sign.KeyPair.newKeyPair()
    private val verifier = RegistryVerifier(listOf(key.publicKey), setOf("searchDesktop"), 10_000L) { now }

    private class MemStore : RegistryStore {
        var bytes: ByteArray? = null
        var floor: Long? = null
        override fun read() = bytes
        override fun write(bytes: ByteArray) { this.bytes = bytes }
        override fun readFloor() = floor
        override fun writeFloor(issuedAtEpochMs: Long) { floor = issuedAtEpochMs }
    }

    private fun payload(issuedAt: Long, killKugou: Boolean) =
        """{"issuedAtEpochMs":$issuedAt,"killSwitches":{"kugou":$killKugou}}""".toByteArray()

    private fun sig(p: ByteArray) = Ed25519Sign(key.privateKey).sign(RegistryVerifier.signingInput(p))

    @Test fun validPayloadIsStoredAndCurrent() {
        val repo = RegistryRepository(verifier, MemStore(), embeddedFallback = null)
        val p = payload(now, killKugou = true)
        assertTrue(repo.accept(p, sig(p)) is VerifyResult.Valid)
        assertEquals(true, repo.current()?.killSwitches?.get("kugou"))
    }

    @Test fun invalidPayloadKeepsLastKnownGood() {
        val repo = RegistryRepository(verifier, MemStore(), embeddedFallback = null)
        val good = payload(now, killKugou = true)
        repo.accept(good, sig(good))
        val bad = payload(now, killKugou = false)
        assertTrue(repo.accept(bad, ByteArray(64)) is VerifyResult.Invalid)
        assertEquals(true, repo.current()?.killSwitches?.get("kugou"))
    }

    @Test fun olderRegistryThanStoredIsRejectedAsRollback() {
        val repo = RegistryRepository(verifier, MemStore(), embeddedFallback = null)
        val newer = payload(now, killKugou = true)
        repo.accept(newer, sig(newer))
        val older = payload(now - 5_000L, killKugou = false)
        assertTrue(repo.accept(older, sig(older)) is VerifyResult.Invalid)
        assertEquals(true, repo.current()?.killSwitches?.get("kugou"))
    }

    @Test fun equalTimestampIsAccepted() {
        val repo = RegistryRepository(verifier, MemStore(), embeddedFallback = null)
        val p = payload(now, killKugou = true)
        repo.accept(p, sig(p))
        assertTrue(repo.accept(p, sig(p)) is VerifyResult.Valid)
    }

    @Test fun registryOlderThanEmbeddedFallbackIsRejected() {
        val embedded = Registry(issuedAtEpochMs = now + 100L, killSwitches = mapOf("kugou" to true))
        val repo = RegistryRepository(verifier, MemStore(), embedded)
        val older = payload(now, killKugou = false)
        assertTrue(repo.accept(older, sig(older)) is VerifyResult.Invalid)
        assertEquals(embedded, repo.current())
    }

    @Test fun nothingStoredFallsBackToEmbedded() {
        val embedded = Registry(issuedAtEpochMs = 1L, killSwitches = mapOf("kugou" to false))
        val repo = RegistryRepository(verifier, MemStore(), embedded)
        assertEquals(embedded, repo.current())
    }

    @Test fun nothingStoredAndNoFallbackIsNull() {
        assertNull(RegistryRepository(verifier, MemStore(), null).current())
    }

    @Test fun corruptStoredPayloadDoesNotLowerTheRollbackFloor() {
        val store = MemStore()
        val repo = RegistryRepository(verifier, store, embeddedFallback = null)
        val newer = payload(now, killKugou = true)
        repo.accept(newer, sig(newer))
        store.bytes = "{ corrupt".toByteArray()
        val older = payload(now - 5_000L, killKugou = false)
        assertTrue(repo.accept(older, sig(older)) is VerifyResult.Invalid)
        // The replayed registry must not have been stored over the corrupt one.
        assertEquals("{ corrupt", store.bytes!!.decodeToString())
        // A registry at the floor is still accepted, so the app can recover.
        assertTrue(repo.accept(newer, sig(newer)) is VerifyResult.Valid)
        assertEquals(true, repo.current()?.killSwitches?.get("kugou"))
    }

    @Test fun deletedStoredPayloadDoesNotLowerTheRollbackFloor() {
        val store = MemStore()
        val repo = RegistryRepository(verifier, store, embeddedFallback = null)
        val newer = payload(now, killKugou = true)
        repo.accept(newer, sig(newer))
        store.bytes = null
        val older = payload(now - 5_000L, killKugou = false)
        assertTrue(repo.accept(older, sig(older)) is VerifyResult.Invalid)
    }

    @Test fun floorIsTheHighestIssuedAtEverAccepted() {
        val store = MemStore()
        val repo = RegistryRepository(verifier, store, embeddedFallback = null)
        val newer = payload(now, killKugou = true)
        repo.accept(newer, sig(newer))
        assertEquals(now, store.floor)
        val older = payload(now - 5_000L, killKugou = false)
        repo.accept(older, sig(older))
        assertEquals(now, store.floor)
    }

    @Test fun currentPrefersTheNewerOfStoredAndEmbedded() {
        val store = MemStore()
        val embedded = Registry(issuedAtEpochMs = now + 100L, killSwitches = mapOf("kugou" to true))
        // An older registry left in storage by a previous app version.
        store.bytes = payload(now, killKugou = false)
        assertEquals(embedded, RegistryRepository(verifier, store, embedded).current())
    }

    @Test fun currentUsesStoredWhenNewerThanEmbedded() {
        val store = MemStore()
        val embedded = Registry(issuedAtEpochMs = now - 500L, killSwitches = mapOf("kugou" to false))
        val repo = RegistryRepository(verifier, store, embedded)
        val p = payload(now, killKugou = true)
        repo.accept(p, sig(p))
        assertEquals(true, repo.current()?.killSwitches?.get("kugou"))
    }

    @Test fun corruptStoredPayloadFallsBackToEmbedded() {
        val store = MemStore().apply { bytes = "{ corrupt".toByteArray() }
        val embedded = Registry(issuedAtEpochMs = 1L, killSwitches = mapOf("kugou" to false))
        assertEquals(embedded, RegistryRepository(verifier, store, embedded).current())
    }
}
