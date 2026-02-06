package org.openbeam.core

import java.util.UUID

/**
 * Represents a short lived token that is exchanged via NFC to initiate a transfer session.
 *
 * @param id Unique session identifier.
 * @param type Type of transfer being initiated.
 * @param tempKey A temporary symmetric key encoded in base64, used for encrypting handshake messages.
 * @param params Additional parameters for transport (e.g., WiFi Direct or Bluetooth specifics).
 */
data class SessionToken(
    val id: String,
    val type: TransferType,
    val tempKey: String,
    val params: Map<String, String>
) {
    companion object {
        /**
         * Generates a new token for the given transfer type and parameters. A random UUID is used for the ID
         * and a random 256‑bit key for encryption.
         */
        fun generate(type: TransferType, params: Map<String, String>): SessionToken {
            val id = UUID.randomUUID().toString()
            val key = CryptoUtil.generateRandomKey() // returns base64 string
            // Add transport preference to parameters if not already present
            val mutable = params.toMutableMap()
            if (!mutable.containsKey("transport")) {
                mutable["transport"] = "wifi"
            }
            return SessionToken(id, type, key, mutable)
        }
    }
}