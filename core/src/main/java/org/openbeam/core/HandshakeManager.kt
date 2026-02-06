package org.openbeam.core

import android.util.Base64
import org.json.JSONObject

/**
 * Handles serialisation and deserialisation of handshake messages exchanged over the transport
 * channel. The handshake occurs after an NFC trigger and uses the temporary session key to
 * authenticate participants and agree on metadata for the transfer.
 */
object HandshakeManager {

    /**
     * Serialises the provided metadata and session details into an encrypted payload. The
     * resulting byte array can be transmitted over the transport layer.
     *
     * @param token Session token shared via NFC
     * @param metadata Transfer metadata including names and sizes
     */
    fun createHandshakeMessage(token: SessionToken, metadata: TransferMetadata): ByteArray {
        val json = JSONObject().apply {
            put("sessionId", token.id)
            put("type", token.type.name)
            put("name", metadata.name)
            put("size", metadata.size)
            put("uriCount", metadata.uris.size)
        }
        val plain = json.toString()
        val aad = token.id
        return CryptoUtil.encrypt(token.tempKey, plain, aad)
    }

    /**
     * Parses an encrypted handshake message into a [TransferMetadata]. If the session ID does
     * not match the expected value or decryption fails, an exception is thrown.
     *
     * @param token Session token containing the temporary key and ID
     * @param message Encrypted message received via transport
     */
    fun parseHandshakeMessage(token: SessionToken, message: ByteArray): TransferMetadata {
        val aad = token.id
        val plain = CryptoUtil.decrypt(token.tempKey, message, aad)
        val json = JSONObject(plain)
        val name = json.getString("name")
        val size = json.getLong("size")
        val uriCount = json.getInt("uriCount")
        // URIs themselves are exchanged separately after handshake; we don't know them here
        return TransferMetadata(name, size, emptyList())
    }
}