package com.brewingbytes.gemmory.remote

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Published vectors (FIPS 180-4 examples, RFC 8032 section 7.1), on every target. */
class CryptoVectorsTest {
    private fun hex(s: String) = hexToBytes(s)!!

    @Test
    fun shaOfAbc() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.digest("abc".encodeToByteArray()).toHex())
        assertEquals(
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
            Sha512.digest("abc".encodeToByteArray()).toHex(),
        )
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.digest(ByteArray(0)).toHex())
    }

    @Test
    fun rfc8032Test1And2() {
        val vectors = listOf(
            Triple(
                "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60" to "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
                "",
                "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
            ),
            Triple(
                "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb" to "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
                "72",
                "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00",
            ),
        )
        for ((keys, message, signature) in vectors) {
            val (seed, pk) = keys
            assertContentEquals(hex(pk), Ed25519.publicKey(hex(seed)))
            assertContentEquals(hex(signature), Ed25519.sign(hex(seed), hex(message)))
            assertTrue(Ed25519.verify(hex(pk), hex(message), hex(signature)))
            assertFalse(Ed25519.verify(hex(pk), byteArrayOf(1), hex(signature)))
        }
    }

    @Test
    fun malformedInputsAreFalseNotThrown() {
        val seed = ByteArray(32) { it.toByte() }
        val pk = Ed25519.publicKey(seed)
        val sig = Ed25519.sign(seed, byteArrayOf(5))
        assertFalse(Ed25519.verify(pk.copyOf(31), byteArrayOf(5), sig))
        assertFalse(Ed25519.verify(pk, byteArrayOf(5), sig.copyOf(63)))
        // S = L + S' would verify in TweetNaCl; RFC 8032 refuses S ≥ L.
        val high = sig.copyOf().also { it[63] = 0xF0.toByte() }
        assertFalse(Ed25519.verify(pk, byteArrayOf(5), high))
        // A y with no x on the curve.
        assertFalse(Ed25519.verify(ByteArray(32) { if (it == 0) 2 else 0 }, byteArrayOf(5), sig))
    }
}
