package org.openbeam.core

import org.json.JSONObject

object HandshakeManager {

    fun createHandshakeMessage(token: SessionToken, metadata: TransferMetadata): ByteArray {
        val json = JSONObject().apply {
            put("sessionId", token.id)
            put("type", token.type.name)
            put("name", metadata.name)
            put("size", metadata.size)
            put("uriCount", metadata.uris.size)
        }
        val plain = json.toString()
        return CryptoUtil.encrypt(token.tempKey, plain, token.id)
    }

    fun parseHandshakeMessage(token: SessionToken, message: ByteArray): TransferMetadata {
        val plain = CryptoUtil.decrypt(token.tempKey, message, token.id)
        val json = JSONObject(plain)
        val name = json.getString("name")
        val size = json.getLong("size")
        return TransferMetadata(name, size, emptyList())
    }
}