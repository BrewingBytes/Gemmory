package com.brewingbytes.gemmory.remote

/**
 * Ed25519 (RFC 8032) in pure Kotlin, ported from TweetNaCl (public domain): field elements are 16 limbs of 16 bits.
 * The app only verifies (#43: a remote catalog is signed outside every repo); [sign] and [publicKey] exist for the
 * tests and the publishing tool's self-check. Not constant-time, which only matters for signing with a secret on a
 * shared machine, never the app's case. The curve constants are derived at load from their definitions, not typed in.
 */
object Ed25519 {
    const val KEY_BYTES = 32
    const val SIGNATURE_BYTES = 64

    /** Whether [signature] is [publicKey]'s signature of [message]. False for any malformed input, never throws. */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != KEY_BYTES || signature.size != SIGNATURE_BYTES) return false
        // RFC 8032 5.1.7: S must be below the group order, or a signature could be re-encoded (malleability).
        if (!lessThanL(signature, 32)) return false
        val q = Array(4) { gf() }
        if (!unpackNeg(q, publicKey)) return false
        val h = Sha512.digest(signature.copyOfRange(0, 32) + publicKey + message)
        reduce(h)
        val p = Array(4) { gf() }
        scalarMult(p, q, h)
        scalarBase(q, signature.copyOfRange(32, 64))
        add(p, q)
        val t = ByteArray(32)
        pack(t, p)
        var diff = 0
        for (i in 0 until 32) diff = diff or (t[i].toInt() xor signature[i].toInt())
        return diff == 0
    }

    /** The public key of a 32-byte [seed] (the RFC's "private key"). */
    fun publicKey(seed: ByteArray): ByteArray {
        require(seed.size == KEY_BYTES) { "seed must be 32 bytes" }
        val d = Sha512.digest(seed)
        clamp(d)
        val p = Array(4) { gf() }
        scalarBase(p, d.copyOfRange(0, 32))
        return ByteArray(32).also { pack(it, p) }
    }

    fun sign(seed: ByteArray, message: ByteArray): ByteArray {
        val pk = publicKey(seed)
        val d = Sha512.digest(seed)
        clamp(d)
        val r = Sha512.digest(d.copyOfRange(32, 64) + message)
        reduce(r)
        val p = Array(4) { gf() }
        scalarBase(p, r)
        val sig = ByteArray(64)
        pack(sig, p)
        val h = Sha512.digest(sig.copyOfRange(0, 32) + pk + message)
        reduce(h)
        val x = LongArray(64)
        for (i in 0 until 32) x[i] = u(r[i])
        for (i in 0 until 32) for (j in 0 until 32) x[i + j] += u(h[i]) * u(d[j])
        val s = ByteArray(32)
        modL(s, x)
        s.copyInto(sig, 32)
        return sig
    }

    private fun clamp(d: ByteArray) {
        d[0] = (d[0].toInt() and 248).toByte()
        d[31] = ((d[31].toInt() and 127) or 64).toByte()
    }

    private fun u(b: Byte): Long = (b.toInt() and 0xFF).toLong()

    private fun gf(vararg init: Long): LongArray = LongArray(16).also { init.copyInto(it) }

    private val GF0 = gf()
    private val GF1 = gf(1)

    private val L = longArrayOf(
        0xed, 0xd3, 0xf5, 0x5c, 0x1a, 0x63, 0x12, 0x58, 0xd6, 0x9c, 0xf7, 0xa2, 0xde, 0xf9, 0xde, 0x14,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10,
    )

    /** d = -121665 / 121666. */
    private val D: LongArray = gf().also { d ->
        val inv = gf()
        inv25519(inv, gf(0xDB42, 1))
        sub(d, GF0, gf(0xDB41, 1))
        mul(d, d, inv)
    }

    private val D2: LongArray = gf().also { add(it, D, D) }

    /** sqrt(-1) = 2^((p-1)/4); (p-1)/4 = 2^253 - 5 has every bit 0..252 set except bit 2. */
    private val I: LongArray = gf(1).also { c ->
        val two = gf(2)
        for (a in 252 downTo 0) {
            mul(c, c, c)
            if (a != 2) mul(c, c, two)
        }
    }

    /** The base point: y = 4/5, x the even root. */
    private val BASE: Array<LongArray> = run {
        val y = gf()
        inv25519(y, gf(5))
        mul(y, y, gf(4))
        val encoded = ByteArray(32)
        pack25519(encoded, y)
        val q = Array(4) { gf() }
        check(unpackNeg(q, encoded)) { "base point" }
        // unpackNeg returns -B; negate x (and t) back.
        sub(q[0], GF0, q[0])
        sub(q[3], GF0, q[3])
        q
    }

    private fun car25519(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += 1L shl 16
            val c = o[i] shr 16
            if (i < 15) o[i + 1] += c - 1 else o[0] += 38 * (c - 1)
            o[i] -= c shl 16
        }
    }

    private fun sel25519(p: LongArray, q: LongArray, b: Int) {
        val c = (b - 1).toLong().inv()
        for (i in 0 until 16) {
            val t = c and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    private fun pack25519(o: ByteArray, n: LongArray) {
        val m = gf()
        val t = n.copyOf()
        car25519(t)
        car25519(t)
        car25519(t)
        repeat(2) {
            m[0] = t[0] - 0xffed
            for (i in 1 until 15) {
                m[i] = t[i] - 0xffff - ((m[i - 1] shr 16) and 1)
                m[i - 1] = m[i - 1] and 0xffff
            }
            m[15] = t[15] - 0x7fff - ((m[14] shr 16) and 1)
            val b = ((m[15] shr 16) and 1).toInt()
            m[14] = m[14] and 0xffff
            sel25519(t, m, 1 - b)
        }
        for (i in 0 until 16) {
            o[2 * i] = (t[i] and 0xff).toByte()
            o[2 * i + 1] = (t[i] shr 8).toByte()
        }
    }

    private fun neq25519(a: LongArray, b: LongArray): Boolean {
        val c = ByteArray(32)
        val d = ByteArray(32)
        pack25519(c, a)
        pack25519(d, b)
        return !c.contentEquals(d)
    }

    private fun par25519(a: LongArray): Int {
        val d = ByteArray(32)
        pack25519(d, a)
        return d[0].toInt() and 1
    }

    private fun unpack25519(o: LongArray, n: ByteArray) {
        for (i in 0 until 16) o[i] = u(n[2 * i]) + (u(n[2 * i + 1]) shl 8)
        o[15] = o[15] and 0x7fff
    }

    private fun add(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] + b[i]
    }

    private fun sub(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] - b[i]
    }

    private fun mul(o: LongArray, a: LongArray, b: LongArray) {
        val t = LongArray(31)
        for (i in 0 until 16) for (j in 0 until 16) t[i + j] += a[i] * b[j]
        for (i in 0 until 15) t[i] += 38 * t[i + 16]
        for (i in 0 until 16) o[i] = t[i]
        car25519(o)
        car25519(o)
    }

    private fun inv25519(o: LongArray, i: LongArray) {
        val c = i.copyOf()
        for (a in 253 downTo 0) {
            mul(c, c, c)
            if (a != 2 && a != 4) mul(c, c, i)
        }
        c.copyInto(o)
    }

    private fun pow2523(o: LongArray, i: LongArray) {
        val c = i.copyOf()
        for (a in 250 downTo 0) {
            mul(c, c, c)
            if (a != 1) mul(c, c, i)
        }
        c.copyInto(o)
    }

    private fun add(p: Array<LongArray>, q: Array<LongArray>) {
        val a = gf(); val b = gf(); val c = gf(); val d = gf(); val t = gf()
        val e = gf(); val f = gf(); val g = gf(); val h = gf()
        sub(a, p[1], p[0])
        sub(t, q[1], q[0])
        mul(a, a, t)
        add(b, p[0], p[1])
        add(t, q[0], q[1])
        mul(b, b, t)
        mul(c, p[3], q[3])
        mul(c, c, D2)
        mul(d, p[2], q[2])
        add(d, d, d)
        sub(e, b, a)
        sub(f, d, c)
        add(g, d, c)
        add(h, b, a)
        mul(p[0], e, f)
        mul(p[1], h, g)
        mul(p[2], g, f)
        mul(p[3], e, h)
    }

    private fun cswap(p: Array<LongArray>, q: Array<LongArray>, b: Int) {
        for (i in 0 until 4) sel25519(p[i], q[i], b)
    }

    private fun pack(r: ByteArray, p: Array<LongArray>) {
        val tx = gf(); val ty = gf(); val zi = gf()
        inv25519(zi, p[2])
        mul(tx, p[0], zi)
        mul(ty, p[1], zi)
        pack25519(r, ty)
        r[31] = (r[31].toInt() xor (par25519(tx) shl 7)).toByte()
    }

    private fun scalarMult(p: Array<LongArray>, q: Array<LongArray>, s: ByteArray) {
        GF0.copyInto(p[0]); GF1.copyInto(p[1]); GF1.copyInto(p[2]); GF0.copyInto(p[3])
        for (i in 255 downTo 0) {
            val b = (s[i / 8].toInt() shr (i and 7)) and 1
            cswap(p, q, b)
            add(q, p)
            add(p, p)
            cswap(p, q, b)
        }
    }

    private fun scalarBase(p: Array<LongArray>, s: ByteArray) {
        val q = Array(4) { BASE[it].copyOf() }
        scalarMult(p, q, s)
    }

    private fun modL(r: ByteArray, x: LongArray) {
        var carry: Long
        for (i in 63 downTo 32) {
            carry = 0
            var j = i - 32
            while (j < i - 12) {
                x[j] += carry - 16 * x[i] * L[j - (i - 32)]
                carry = (x[j] + 128) shr 8
                x[j] -= carry shl 8
                j++
            }
            x[j] += carry
            x[i] = 0
        }
        carry = 0
        for (j in 0 until 32) {
            x[j] += carry - (x[31] shr 4) * L[j]
            carry = x[j] shr 8
            x[j] = x[j] and 255
        }
        for (j in 0 until 32) x[j] -= carry * L[j]
        for (i in 0 until 32) {
            x[i + 1] += x[i] shr 8
            r[i] = (x[i] and 255).toByte()
        }
    }

    /** Reduces a 64-byte hash mod L into its first 32 bytes. */
    private fun reduce(r: ByteArray) {
        val x = LongArray(64) { u(r[it]) }
        r.fill(0)
        modL(r, x)
    }

    /** Whether the 32 little-endian bytes of [s] from [from] are below L. */
    private fun lessThanL(s: ByteArray, from: Int): Boolean {
        for (i in 31 downTo 0) {
            val a = u(s[from + i])
            if (a < L[i]) return true
            if (a > L[i]) return false
        }
        return false
    }

    /** Decodes a point and negates it (TweetNaCl's unpackneg). False when the bytes aren't a curve point. */
    private fun unpackNeg(r: Array<LongArray>, p: ByteArray): Boolean {
        val t = gf(); val chk = gf(); val num = gf(); val den = gf()
        val den2 = gf(); val den4 = gf(); val den6 = gf()
        GF1.copyInto(r[2])
        unpack25519(r[1], p)
        mul(num, r[1], r[1])
        mul(den, num, D)
        sub(num, num, r[2])
        add(den, r[2], den)
        mul(den2, den, den)
        mul(den4, den2, den2)
        mul(den6, den4, den2)
        mul(t, den6, num)
        mul(t, t, den)
        pow2523(t, t)
        mul(t, t, num)
        mul(t, t, den)
        mul(t, t, den)
        mul(r[0], t, den)
        mul(chk, r[0], r[0])
        mul(chk, chk, den)
        if (neq25519(chk, num)) mul(r[0], r[0], I)
        mul(chk, r[0], r[0])
        mul(chk, chk, den)
        if (neq25519(chk, num)) return false
        if (par25519(r[0]) == (u(p[31]).toInt() shr 7)) sub(r[0], GF0, r[0])
        mul(r[3], r[0], r[1])
        return true
    }
}
