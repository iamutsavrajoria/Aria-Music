// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.cipher

import app.aria.core.cipher.CipherConfigVerifier
import com.google.crypto.tink.subtle.Ed25519Sign
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CipherConfigUpdaterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val pair = Ed25519Sign.KeyPair.newKeyPair()
    private val now = 5_000_000L
    private fun store() = FileCipherConfigStore(tmp.newFolder())
    private fun verifier(store: CipherConfigStore) =
        CipherConfigVerifier(listOf(pair.publicKey), setOf("playerConfigs"), 86_400_000L, lastAcceptedVersion = store::lastVersion, clock = { now })
    private fun fetched(version: Long, key: ByteArray = pair.privateKey): FetchedConfig {
        val p = """{"version":$version,"issuedAtEpochMs":$now,"config":{"playerConfigs":{}}}""".toByteArray()
        return FetchedConfig(p, Ed25519Sign(key).sign(CipherConfigVerifier.signingInput(p)))
    }

    @Test fun disabledNeverFetches() = runBlocking {
        var calls = 0; val s = store()
        val u = CipherConfigUpdater(false, "https://example.invalid/c", { calls++; fetched(1) }, verifier(s), s)
        assertEquals(UpdateResult.Disabled, u.updateOnce()); assertEquals(0, calls)
    }
    @Test fun blankUrlNeverFetches() = runBlocking {
        var calls = 0; val s = store()
        assertEquals(UpdateResult.Disabled, CipherConfigUpdater(true, "", { calls++; null }, verifier(s), s).updateOnce()); assertEquals(0, calls)
    }
    @Test fun validSignedConfigIsStored() = runBlocking {
        val s = store()
        val r = CipherConfigUpdater(true, "https://example.invalid/c", { fetched(7) }, verifier(s), s).updateOnce()
        assertEquals(UpdateResult.Applied(7), r); assertEquals(7L, s.lastVersion()); assertNotNull(s.readConfig())
    }
    @Test fun badSignatureLeavesStoreUntouched() = runBlocking {
        val s = store(); val other = Ed25519Sign.KeyPair.newKeyPair()
        val r = CipherConfigUpdater(true, "https://example.invalid/c", { fetched(7, other.privateKey) }, verifier(s), s).updateOnce()
        assertTrue(r is UpdateResult.Rejected); assertEquals(0L, s.lastVersion()); assertNull(s.readConfig())
    }
    @Test fun fetchFailureIsNoUpdate() = runBlocking {
        val s = store()
        assertEquals(UpdateResult.NoUpdate, CipherConfigUpdater(true, "https://example.invalid/c", { null }, verifier(s), s).updateOnce())
    }
}
