package com.brewingbytes.gemmory.remote

/**
 * SHA-256 and SHA-512 (FIPS 180-4) in pure Kotlin, for remote pictures (#43): a catalog lists each grid's SHA-256, and
 * Ed25519 hashes with SHA-512. commonMain has no platform digest, and minSdk 29 has no platform Ed25519.
 */
object Sha256 {
    private val K = words(
        "428a2f98 71374491 b5c0fbcf e9b5dba5 3956c25b 59f111f1 923f82a4 ab1c5ed5 " +
        "d807aa98 12835b01 243185be 550c7dc3 72be5d74 80deb1fe 9bdc06a7 c19bf174 " +
        "e49b69c1 efbe4786 0fc19dc6 240ca1cc 2de92c6f 4a7484aa 5cb0a9dc 76f988da " +
        "983e5152 a831c66d b00327c8 bf597fc7 c6e00bf3 d5a79147 06ca6351 14292967 " +
        "27b70a85 2e1b2138 4d2c6dfc 53380d13 650a7354 766a0abb 81c2c92e 92722c85 " +
        "a2bfe8a1 a81a664b c24b8b70 c76c51a3 d192e819 d6990624 f40e3585 106aa070 " +
        "19a4c116 1e376c08 2748774c 34b0bcb5 391c0cb3 4ed8aa4a 5b9cca4f 682e6ff3 " +
        "748f82ee 78a5636f 84c87814 8cc70208 90befffa a4506ceb bef9a3f7 c67178f2",
    ).map { it.toUInt(16).toInt() }.toIntArray()

    fun digest(data: ByteArray): ByteArray {
        val h = words("6a09e667 bb67ae85 3c6ef372 a54ff53a 510e527f 9b05688c 1f83d9ab 5be0cd19")
            .map { it.toUInt(16).toInt() }.toIntArray()
        val padded = pad(data, 64, 8)
        val w = IntArray(64)
        for (block in padded.indices step 64) {
            for (i in 0 until 16) {
                val o = block + i * 4
                w[i] = (padded[o].toInt() and 0xFF shl 24) or (padded[o + 1].toInt() and 0xFF shl 16) or
                    (padded[o + 2].toInt() and 0xFF shl 8) or (padded[o + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0 until 64) {
                val t1 = hh + (e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)) + ((e and f) xor (e.inv() and g)) + K[i] + w[i]
                val t2 = (a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)) + ((a and b) xor (a and c) xor (b and c))
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        return ByteArray(32) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
    }
}

object Sha512 {
    private val K = words(
        "428a2f98d728ae22 7137449123ef65cd b5c0fbcfec4d3b2f e9b5dba58189dbbc 3956c25bf348b538 59f111f1b605d019 923f82a4af194f9b ab1c5ed5da6d8118 " +
        "d807aa98a3030242 12835b0145706fbe 243185be4ee4b28c 550c7dc3d5ffb4e2 72be5d74f27b896f 80deb1fe3b1696b1 9bdc06a725c71235 c19bf174cf692694 " +
        "e49b69c19ef14ad2 efbe4786384f25e3 0fc19dc68b8cd5b5 240ca1cc77ac9c65 2de92c6f592b0275 4a7484aa6ea6e483 5cb0a9dcbd41fbd4 76f988da831153b5 " +
        "983e5152ee66dfab a831c66d2db43210 b00327c898fb213f bf597fc7beef0ee4 c6e00bf33da88fc2 d5a79147930aa725 06ca6351e003826f 142929670a0e6e70 " +
        "27b70a8546d22ffc 2e1b21385c26c926 4d2c6dfc5ac42aed 53380d139d95b3df 650a73548baf63de 766a0abb3c77b2a8 81c2c92e47edaee6 92722c851482353b " +
        "a2bfe8a14cf10364 a81a664bbc423001 c24b8b70d0f89791 c76c51a30654be30 d192e819d6ef5218 d69906245565a910 f40e35855771202a 106aa07032bbd1b8 " +
        "19a4c116b8d2d0c8 1e376c085141ab53 2748774cdf8eeb99 34b0bcb5e19b48a8 391c0cb3c5c95a63 4ed8aa4ae3418acb 5b9cca4f7763e373 682e6ff3d6b2b8a3 " +
        "748f82ee5defb2fc 78a5636f43172f60 84c87814a1f0ab72 8cc702081a6439ec 90befffa23631e28 a4506cebde82bde9 bef9a3f7b2c67915 c67178f2e372532b " +
        "ca273eceea26619c d186b8c721c0c207 eada7dd6cde0eb1e f57d4f7fee6ed178 06f067aa72176fba 0a637dc5a2c898a6 113f9804bef90dae 1b710b35131c471b " +
        "28db77f523047d84 32caab7b40c72493 3c9ebe0a15c9bebc 431d67c49c100d4c 4cc5d4becb3e42b6 597f299cfc657e2a 5fcb6fab3ad6faec 6c44198c4a475817",
    ).map { it.toULong(16).toLong() }.toLongArray()

    fun digest(data: ByteArray): ByteArray {
        val h = words("6a09e667f3bcc908 bb67ae8584caa73b 3c6ef372fe94f82b a54ff53a5f1d36f1 510e527fade682d1 9b05688c2b3e6c1f 1f83d9abfb41bd6b 5be0cd19137e2179")
            .map { it.toULong(16).toLong() }.toLongArray()
        val padded = pad(data, 128, 16)
        val w = LongArray(80)
        for (block in padded.indices step 128) {
            for (i in 0 until 16) {
                var v = 0L
                for (j in 0 until 8) v = (v shl 8) or (padded[block + i * 8 + j].toLong() and 0xFF)
                w[i] = v
            }
            for (i in 16 until 80) {
                val s0 = w[i - 15].rotateRight(1) xor w[i - 15].rotateRight(8) xor (w[i - 15] ushr 7)
                val s1 = w[i - 2].rotateRight(19) xor w[i - 2].rotateRight(61) xor (w[i - 2] ushr 6)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0 until 80) {
                val t1 = hh + (e.rotateRight(14) xor e.rotateRight(18) xor e.rotateRight(41)) + ((e and f) xor (e.inv() and g)) + K[i] + w[i]
                val t2 = (a.rotateRight(28) xor a.rotateRight(34) xor a.rotateRight(39)) + ((a and b) xor (a and c) xor (b and c))
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        return ByteArray(64) { (h[it / 8] ushr (56 - 8 * (it % 8))).toByte() }
    }
}

/** The round constants and initial values, as FIPS 180-4 prints them. */
private fun words(hex: String): List<String> = hex.split(' ').filter { it.isNotEmpty() }

/** Message + 0x80 + zeros + the bit length, big-endian in the last [lengthBytes] bytes of a [block]-byte block. */
private fun pad(data: ByteArray, block: Int, lengthBytes: Int): ByteArray {
    val total = ((data.size + 1 + lengthBytes + block - 1) / block) * block
    val out = data.copyOf(total)
    out[data.size] = 0x80.toByte()
    val bits = data.size.toLong() * 8
    for (i in 0 until 8) out[total - 1 - i] = (bits ushr (8 * i)).toByte()
    return out
}

/** Lowercase hex, as the catalog writes hashes. */
fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** Parses lowercase or uppercase hex; null for odd length or a non-hex character. */
fun hexToBytes(hex: String): ByteArray? {
    if (hex.length % 2 != 0) return null
    return ByteArray(hex.length / 2) { i ->
        val hi = hex[2 * i].digitToIntOrNull(16) ?: return null
        val lo = hex[2 * i + 1].digitToIntOrNull(16) ?: return null
        (hi * 16 + lo).toByte()
    }
}
