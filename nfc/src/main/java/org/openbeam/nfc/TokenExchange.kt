package org.openbeam.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcEvent
import android.util.Base64
import org.json.JSONObject
import org.openbeam.core.SessionToken
import org.openbeam.core.TransferType

/**
 * Provides utilities to wrap and unwrap [SessionToken]s into NFC NDEF messages. The token is
 * serialised as JSON and encoded in UTF‑8. Binary fields (e.g., keys) remain base64 encoded.
 */
object TokenExchange {
    /**
     * Creates an NDEF message from a session token.
     */
    fun createNdef(token: SessionToken): NdefMessage {
        val json = JSONObject().apply {
            put("id", token.id)
            put("type", token.type.name)
            put("tempKey", token.tempKey)
            put("params", JSONObject(token.params))
        }
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        val record = NdefRecord.createMime("application/vnd.openbeam.session", payload)
        return NdefMessage(arrayOf(record))
    }

    /**
     * Parses an NDEF message into a session token. Returns null if the message is not in
     * the expected format.
     */
    fun parseNdef(message: NdefMessage): SessionToken? {
        val record = message.records.firstOrNull() ?: return null
        return try {
            val json = JSONObject(String(record.payload, Charsets.UTF_8))
            val id = json.getString("id")
            val type = TransferType.valueOf(json.getString("type"))
            val tempKey = json.getString("tempKey")
            val paramsObj = json.getJSONObject("params")
            val params = mutableMapOf<String, String>()
            paramsObj.keys().forEachRemaining { key -> params[key] = paramsObj.getString(key) }
            SessionToken(id, type, tempKey, params)
        } catch (e: Exception) {
            null
        }
    }
}