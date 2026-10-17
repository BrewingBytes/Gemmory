package com.brewingbytes.gemmory.remote

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.EdECPublicKey
import java.security.spec.EdECPrivateKeySpec
import java.security.spec.NamedParameterSpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pure-Kotlin hashes and Ed25519 (#43) against the JDK's, on random inputs of every padding length. */
class CryptoAgainstJdkTest {
    private val random = Random(43)

    @Test
    fun shaMatchesTheJdk() {
        for (size in (0..300) + listOf(1000, 4096, 16040)) {
            val data = random.nextBytes(size)
            assertContentEquals(MessageDigest.getInstance("SHA-256").digest(data), Sha256.digest(data), "sha256 $size")
            assertContentEquals(MessageDigest.getInstance("SHA-512").digest(data), Sha512.digest(data), "sha512 $size")
        }
    }

    @Test
    fun ed25519SignsLikeTheJdkAndVerifiesItsSignatures() {
        repeat(20) { round ->
            val seed = random.nextBytes(32)
            val message = random.nextBytes(round * 37)
            val jdkKey = KeyFactory.getInstance("Ed25519").generatePrivate(EdECPrivateKeySpec(NamedParameterSpec.ED25519, seed))
            val jdkSig = Signature.getInstance("Ed25519").apply { initSign(jdkKey); update(message) }.sign()
            val pk = Ed25519.publicKey(seed)
            assertContentEquals(jdkSig, Ed25519.sign(seed, message), "deterministic signature $round")
            assertTrue(Ed25519.verify(pk, message, jdkSig), "verify $round")
            val flipped = jdkSig.copyOf().also { it[round % 64] = (it[round % 64].toInt() xor 1).toByte() }
            assertFalse(Ed25519.verify(pk, message, flipped), "flipped $round")
        }
    }

    @Test
    fun publicKeyMatchesTheJdkEncoding() {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val raw = rawPublicKey(pair.public as EdECPublicKey)
        val sig = Signature.getInstance("Ed25519").apply { initSign(pair.private); update(byteArrayOf(1, 2, 3)) }.sign()
        assertTrue(Ed25519.verify(raw, byteArrayOf(1, 2, 3), sig))
    }

    companion object {
        /** RFC 8032 encoding of a JDK public key: y little-endian, x's parity in the top bit. */
        fun rawPublicKey(key: EdECPublicKey): ByteArray {
            val y = key.point.y.toByteArray().reversedArray().copyOf(32)
            if (key.point.isXOdd) y[31] = (y[31].toInt() or 0x80).toByte()
            return y
        }
    }
}
