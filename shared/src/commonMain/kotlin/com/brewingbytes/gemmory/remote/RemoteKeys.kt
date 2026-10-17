package com.brewingbytes.gemmory.remote

/**
 * The Ed25519 public keys a remote catalog (#43) may be signed with. The private key lives outside every repo
 * (README → Publishing remote pictures). A list, so a new key can ship in one release and sign from the next.
 */
object RemoteKeys {
    /** 2026-10-17, `~/.gemmory/remote-pictures-ed25519.pem` on the founder's machine. */
    val PRODUCTION: List<ByteArray> = listOf(
        "9e385ac5161c9f047608adda69b6252fc8d1bbb92e57f463553a3b1998e96db8",
    ).map { requireNotNull(hexToBytes(it)) }
}
